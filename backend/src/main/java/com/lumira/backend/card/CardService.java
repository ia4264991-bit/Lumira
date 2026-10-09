package com.lumira.backend.card;

import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.common.error.ConflictException;
import com.lumira.backend.user.User;
import com.lumira.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

/**
 * Service for Card lifecycle operations (B1 scope).
 *
 * <p>All methods are owner-scoped. AD-041: ownership never implied by object
 * existence — every write operation explicitly verifies ownership before acting.
 * AD-048: no one-primary-Card constraint; a user may own zero or more Cards.
 */
@Service
@Transactional
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public CardService(CardRepository cardRepository, UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.cardRepository = cardRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a new personal Card for the authenticated user.
     *
     * <p>Verifies the owning User exists (enforces referential integrity at the
     * service layer before hitting the DB FK constraint).
     */
    public Card createCard(UUID ownerId, String name, String color) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));

        Card card = new Card(owner.getId(), name, color);
        return cardRepository.save(card);
    }

    /** Rename only a Card owned by the caller. Membership alone never grants rename rights. */
    public Card renameCard(UUID cardId, UUID ownerId, String name) {
        Card card = ownedCardForUpdate(cardId, ownerId);
        card.setName(name);
        return card;
    }

    /**
     * Delete an owned private Card. Private contents cascade with the Card;
     * artifacts actively shared elsewhere are re-owned by the same User first.
     * Course Spaces and Cards retained by membership/event history are not deletable.
     */
    public void deletePrivateCard(UUID cardId, UUID ownerId) {
        Card card = ownedCardForUpdate(cardId, ownerId);
        if (card.isShared()) {
            throw new ConflictException("Dissolve this Course Space before deleting its Card.");
        }
        if (hasCardHistory(cardId)) {
            throw new ConflictException("This Card is linked to Course Space history and cannot be deleted.");
        }

        preserveActivelySharedArtifacts(cardId, ownerId);
        cardRepository.delete(card);
        cardRepository.flush();
    }

    private Card ownedCardForUpdate(UUID cardId, UUID ownerId) {
        Card card = cardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (!card.isOwnedBy(ownerId)) throw new ResourceNotFoundException("Card not found");
        return card;
    }

    private boolean hasCardHistory(UUID cardId) {
        return exists("select exists(select 1 from card_membership where card_id = ? or member_card_id = ?)", cardId, cardId)
                || exists("select exists(select 1 from card_join_request where card_id = ?)", cardId)
                || exists("select exists(select 1 from course_space_event where card_id = ?)", cardId);
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, args));
    }

    private void preserveActivelySharedArtifacts(UUID cardId, UUID ownerId) {
        jdbcTemplate.update("update resource r set owner_card_id = null, owner_user_id = ? " +
                "where r.owner_card_id = ? and exists (select 1 from artifact_share s where s.resource_id = r.id and s.active = true)", ownerId, cardId);
        jdbcTemplate.update("update note n set owner_card_id = null, owner_user_id = ? " +
                "where n.owner_card_id = ? and exists (select 1 from artifact_share s where s.note_id = n.id and s.active = true)", ownerId, cardId);
        jdbcTemplate.update("update study_set a set owner_card_id = null, owner_user_id = ? " +
                "where a.owner_card_id = ? and exists (select 1 from artifact_share s where s.study_set_id = a.id and s.active = true)", ownerId, cardId);
        jdbcTemplate.update("update quiz q set owner_card_id = null, owner_user_id = ? " +
                "where q.owner_card_id = ? and exists (select 1 from artifact_share s where s.quiz_id = q.id and s.active = true)", ownerId, cardId);
        jdbcTemplate.update("update flashcard_set f set owner_card_id = null, owner_user_id = ? " +
                "where f.owner_card_id = ? and exists (select 1 from artifact_share s where s.flashcard_set_id = f.id and s.active = true)", ownerId, cardId);
    }

    /**
     * Returns all Cards owned by the given user, newest first.
     * Never returns another user's cards (AD-041).
     */
    @Transactional(readOnly = true)
    public List<Card> listMyCards(UUID ownerId, int page, int pageSize) {
        return cardRepository.findByOwnerIdOrderByCreatedAtDescIdAsc(ownerId,
                PageRequest.of(page, pageSize)).getContent();
    }

    /**
     * Returns a single Card by id, verified to be owned by {@code ownerId}.
     * Returns the same not-found result for an absent Card and a Card the
     * caller cannot access, as required by the API contract's AD-056 BOLA guard.
     */
    @Transactional(readOnly = true)
    public Card getMyCard(UUID cardId, UUID ownerId) {
        return cardRepository.findByIdAndOwnerId(cardId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + cardId));
    }
}
