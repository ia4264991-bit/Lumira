package com.lumira.backend.user;

import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardJoinRequestRepository;
import com.lumira.backend.card.CardMembershipRepository;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.common.error.AccountDeletionConflictException;
import com.lumira.backend.common.error.ConflictException;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.resource.Resource;
import com.lumira.backend.resource.ResourceRepository;
import com.lumira.backend.resource.ResourceShare;
import com.lumira.backend.resource.ResourceShareRepository;
import com.lumira.backend.study.Note;
import com.lumira.backend.study.NoteRepository;
import com.lumira.backend.study.StudySet;
import com.lumira.backend.study.StudySetRepository;
import com.lumira.backend.quiz.Quiz;
import com.lumira.backend.quiz.QuizRepository;
import com.lumira.backend.quiz.QuizAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Account deletion for the persisted B0-B4 model (AD-049 through AD-052/075). */
@Service
public class AccountDeletionService {
    private final UserRepository users;
    private final CardRepository cards;
    private final CardMembershipRepository memberships;
    private final CardJoinRequestRepository joinRequests;
    private final ResourceRepository resources;
    private final ResourceShareRepository shares;
    private final NoteRepository notes;
    private final StudySetRepository studySets;
    private final QuizRepository quizzes;
    private final QuizAttemptRepository quizAttempts;

    public AccountDeletionService(UserRepository users, CardRepository cards,
            CardMembershipRepository memberships, CardJoinRequestRepository joinRequests,
            ResourceRepository resources, ResourceShareRepository shares,
            NoteRepository notes, StudySetRepository studySets, QuizRepository quizzes,
            QuizAttemptRepository quizAttempts) {
        this.users = users;
        this.cards = cards;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.resources = resources;
        this.shares = shares;
        this.notes = notes;
        this.studySets = studySets;
        this.quizzes = quizzes;
        this.quizAttempts = quizAttempts;
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        List<Card> ownedCards = cards.findByOwnerIdForUpdateOrderById(userId);
        List<UUID> activeCourseSpaceIds = ownedCards.stream()
                .filter(Card::isShared).map(Card::getId).toList();
        if (!activeCourseSpaceIds.isEmpty()) {
            throw new AccountDeletionConflictException(activeCourseSpaceIds);
        }

        // Share lifecycle and dissolution lock the Course Space Card before
        // the Resource. Lock every currently shared-to Card first, in a stable
        // order, then lock the owned Resources and re-read active shares. The
        // deleting User lock prevents a new share by the artifact owner while
        // this snapshot is assembled; Card locks serialize force-unshare and
        // dissolution so a selected successor cannot disappear mid-transfer.
        List<UUID> candidateResourceIds = resources.findResourceIdsOwnedByUserOrTheirCards(userId);
        List<UUID> candidateNoteIds = notes.findIdsOwnedByUserOrCards(userId);
        List<UUID> candidateStudySetIds = studySets.findIdsOwnedByUserOrCards(userId);
        List<UUID> candidateQuizIds = quizzes.findIdsOwnedByUserOrCards(userId);
        List<ResourceShare> observedShares = new java.util.ArrayList<>();
        if (!candidateResourceIds.isEmpty()) {
            observedShares.addAll(shares
                    .findByResourceIdInAndActiveTrueOrderByResourceIdAscCreatedAtAscIdAsc(candidateResourceIds));
        }
        if (!candidateNoteIds.isEmpty()) observedShares.addAll(shares.findByNoteIdInAndActiveTrueOrderByNoteIdAscCreatedAtAscIdAsc(candidateNoteIds));
        if (!candidateStudySetIds.isEmpty()) observedShares.addAll(shares.findByStudySetIdInAndActiveTrueOrderByStudySetIdAscCreatedAtAscIdAsc(candidateStudySetIds));
        if (!candidateQuizIds.isEmpty()) observedShares.addAll(shares.findByQuizIdInAndActiveTrueOrderByQuizIdAscCreatedAtAscIdAsc(candidateQuizIds));
        List<UUID> sharedCardIds = observedShares.stream().map(ResourceShare::getCardId).distinct().sorted().toList();
        if (!sharedCardIds.isEmpty()) cards.findByIdInForUpdateOrderById(sharedCardIds);

        // The locked Resource rows serialize this transaction with every
        // B4 share writer. The successor list below is fetched only after all
        // relevant Course Space Cards and Resources are locked.
        List<Resource> ownedResources = resources.findResourcesOwnedByUserOrTheirCardsForUpdate(userId);
        for (Resource resource : ownedResources) {
            List<ResourceShare> activeShares = shares
                    .findByResourceIdAndActiveTrueOrderByCreatedAtAscIdAsc(resource.getId());
            if (activeShares.isEmpty()) {
                resources.delete(resource);
                continue;
            }

            ResourceShare successorShare = activeShares.getFirst();
            Card successorCourseSpace = cards.findById(successorShare.getCardId())
                    .orElseThrow(() -> new ConflictException("Active ResourceShare references a missing Card"));
            if (!successorCourseSpace.isShared()) {
                throw new ConflictException("Active ResourceShare references a Card that is not a Course Space");
            }
            resource.transferOwnershipToCard(successorCourseSpace.getId());
            resources.save(resource);
        }
        for (Note note : notes.findOwnedByUserOrCardsForUpdate(userId)) {
            List<ResourceShare> activeShares = shares.findByNoteIdAndActiveTrueOrderByCreatedAtAscIdAsc(note.getId());
            Card successor = successorCard(activeShares);
            if (successor == null) notes.delete(note);
            else { note.transferOwnershipToCard(successor.getId()); notes.save(note); }
        }
        for (StudySet set : studySets.findOwnedByUserOrCardsForUpdate(userId)) {
            List<ResourceShare> activeShares = shares.findByStudySetIdAndActiveTrueOrderByCreatedAtAscIdAsc(set.getId());
            Card successor = successorCard(activeShares);
            if (successor == null) studySets.delete(set);
            else { set.transferOwnershipToCard(successor.getId()); studySets.save(set); }
        }
        for (Quiz quiz : quizzes.findOwnedByUserOrCardsForUpdate(userId)) {
            List<ResourceShare> activeShares = shares.findByQuizIdAndActiveTrueOrderByCreatedAtAscIdAsc(quiz.getId());
            Card successor = successorCard(activeShares);
            if (successor == null) quizzes.delete(quiz);
            else { quiz.transferOwnershipToCard(successor.getId()); quizzes.save(quiz); }
        }
        resources.flush();
        notes.flush();
        studySets.flush();
        quizzes.flush();

        List<UUID> ownedCardIds = ownedCards.stream().map(Card::getId).toList();
        joinRequests.deleteByRequestingUserId(userId);
        if (!ownedCardIds.isEmpty()) joinRequests.deleteByCardIdIn(ownedCardIds);
        joinRequests.clearResolverUser(userId);
        memberships.deleteByUserId(userId);
        quizAttempts.deleteByUserId(userId);
        if (!ownedCardIds.isEmpty()) memberships.deleteByCardIdIn(ownedCardIds);
        cards.deleteAll(ownedCards);
        users.delete(user);
    }

    private Card successorCard(List<ResourceShare> activeShares) {
        if (activeShares.isEmpty()) return null;
        ResourceShare successorShare = activeShares.getFirst();
        Card card = cards.findById(successorShare.getCardId())
                .orElseThrow(() -> new ConflictException("Active artifact share references a missing Card"));
        if (!card.isShared()) throw new ConflictException("Active artifact share references a Card that is not a Course Space");
        return card;
    }
}
