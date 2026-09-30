package com.lumira.backend.card;

import com.lumira.backend.common.error.ForbiddenException;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.user.User;
import com.lumira.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    public CardService(CardRepository cardRepository, UserRepository userRepository) {
        this.cardRepository = cardRepository;
        this.userRepository = userRepository;
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

    /**
     * Returns all Cards owned by the given user, newest first.
     * Never returns another user's cards (AD-041).
     */
    @Transactional(readOnly = true)
    public List<Card> listMyCards(UUID ownerId) {
        return cardRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    /**
     * Returns a single Card by id, verified to be owned by {@code ownerId}.
     * Throws {@link ForbiddenException} rather than {@link ResourceNotFoundException}
     * on ownership mismatch to avoid leaking card existence to non-owners (AD-056).
     */
    @Transactional(readOnly = true)
    public Card getMyCard(UUID cardId, UUID ownerId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + cardId));

        // AD-056: authorization deterministic — never inferred from object existence
        if (!card.isOwnedBy(ownerId)) {
            // Return 403, not 404 — leaking "card exists but isn't yours" is an IDOR risk
            throw new ForbiddenException("Access denied to card: " + cardId);
        }
        return card;
    }
}
