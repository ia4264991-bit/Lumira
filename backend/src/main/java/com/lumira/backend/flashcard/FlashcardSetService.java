package com.lumira.backend.flashcard;

import com.lumira.backend.card.Card;
import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.common.error.ValidationException;
import com.lumira.backend.resource.ArtifactType;
import com.lumira.backend.resource.ResourceAuthorizationService;
import com.lumira.backend.resource.ResourceShare;
import com.lumira.backend.resource.ResourceShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FlashcardSetService {
    private final FlashcardSetRepository sets;
    private final FlashcardRepository cards;
    private final FlashcardProgressRepository progress;
    private final ResourceShareRepository shares;
    private final ResourceAuthorizationService authorization;
    private final FlashcardSetResponseMapper mapper;

    public FlashcardSetService(FlashcardSetRepository sets, FlashcardRepository cards,
            FlashcardProgressRepository progress, ResourceShareRepository shares,
            ResourceAuthorizationService authorization,
            FlashcardSetResponseMapper mapper) {
        this.sets = sets;
        this.cards = cards;
        this.progress = progress;
        this.shares = shares;
        this.authorization = authorization;
        this.mapper = mapper;
    }

    @Transactional
    public FlashcardSetResponse create(UUID cardId, UUID actorId, FlashcardSetCreateRequest request) {
        authorization.requirePrivateArtifactCardForCreate(cardId, actorId);
        validateCards(request.cards());
        FlashcardSet set = sets.saveAndFlush(new FlashcardSet(ArtifactOwner.forCard(cardId),
                request.title(), request.description()));
        persistNewCards(set.getId(), request.cards());
        return mapper.from(set);
    }

    @Transactional(readOnly = true)
    public List<FlashcardSetResponse> list(UUID cardId, UUID actorId) {
        Card card = authorization.requireCardReadable(cardId, actorId);
        if (!card.isShared()) {
            return mapper.fromMany(sets.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId));
        }
        List<UUID> ids = shares.findByCardIdAndActiveTrueOrderByCreatedAtAsc(cardId).stream()
                .filter(s -> s.getFlashcardSetId() != null).map(ResourceShare::getFlashcardSetId)
                .toList();
        var visible = new LinkedHashMap<UUID, FlashcardSet>();
        if (card.isOwnedBy(actorId)) {
            sets.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId).forEach(set -> visible.put(set.getId(), set));
        }
        sets.findAllById(ids).forEach(set -> visible.put(set.getId(), set));
        List<FlashcardSet> values = visible.values().stream()
                .sorted(java.util.Comparator.comparing(FlashcardSet::getCreatedAt).reversed()).toList();
        return mapper.fromMany(values, Set.copyOf(ids));
    }

    @Transactional(readOnly = true)
    public FlashcardSetResponse get(UUID id, UUID actorId) {
        FlashcardSet set = load(id);
        authorization.requireArtifactReadable(set.getOwner(), ArtifactType.FLASHCARD_SET,
                id, "Flashcard Set not found", actorId);
        return mapper.from(set);
    }

    @Transactional
    public FlashcardSetResponse update(UUID id, UUID actorId, FlashcardSetPatchRequest request) {
        FlashcardSet set = sets.findByIdForUpdate(id).orElseThrow(this::notFound);
        authorization.requireArtifactOwner(set.getOwner(), "Flashcard Set not found", actorId);
        if (request.title() != null && request.title().isBlank()) throw new ValidationException("title must not be blank");
        if (request.cards() != null) replaceCards(set.getId(), request.cards());
        set.update(request.title(), request.description());
        sets.saveAndFlush(set);
        return mapper.from(set);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        FlashcardSet set = sets.findByIdForUpdate(id).orElseThrow(this::notFound);
        authorization.requireArtifactOwner(set.getOwner(), "Flashcard Set not found", actorId);
        sets.delete(set);
    }

    @Transactional
    public FlashcardProgressResponse recordProgress(UUID setId, UUID actorId, FlashcardProgressRequest request) {
        FlashcardSet set = sets.findByIdForUpdate(setId).orElseThrow(this::notFound);
        authorization.requireArtifactReadable(set.getOwner(), ArtifactType.FLASHCARD_SET,
                setId, "Flashcard Set not found", actorId);
        Flashcard card = cards.findByIdForUpdate(request.cardId())
                .filter(found -> found.getFlashcardSetId().equals(setId))
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard not found"));
        Instant now = Instant.now();
        FlashcardProgress current = progress.findForUpdate(actorId, card.getId()).orElse(null);
        if (current == null) current = new FlashcardProgress(actorId, card.getId(), request.outcome(), now);
        else current.update(request.outcome(), now);
        return FlashcardProgressResponse.from(progress.saveAndFlush(current));
    }

    @Transactional(readOnly = true)
    public List<FlashcardProgressResponse> myProgress(UUID setId, UUID actorId) {
        FlashcardSet set = load(setId);
        authorization.requireArtifactReadable(set.getOwner(), ArtifactType.FLASHCARD_SET,
                setId, "Flashcard Set not found", actorId);
        List<UUID> ids = cards.findByFlashcardSetIdOrderByPositionAscIdAsc(setId).stream()
                .map(Flashcard::getId).toList();
        if (ids.isEmpty()) return List.of();
        return progress.findByUserIdAndFlashcardIdInOrderByFlashcardIdAsc(actorId, ids).stream()
                .map(FlashcardProgressResponse::from).toList();
    }

    private void replaceCards(UUID setId, List<FlashcardInput> input) {
        validateCards(input);
        List<Flashcard> oldCards = cards.findByFlashcardSetIdOrderByPositionAscIdAsc(setId);
        Map<UUID, Flashcard> oldById = oldCards.stream().collect(Collectors.toMap(Flashcard::getId, Function.identity()));
        Set<UUID> retained = new HashSet<>();
        for (FlashcardInput item : input) {
            if (item.id() != null && (!oldById.containsKey(item.id()) || !retained.add(item.id()))) {
                throw new ValidationException("Flashcard ID is unknown to this set or duplicated");
            }
        }
        int offset = oldCards.size() + input.size() + input.stream().mapToInt(FlashcardInput::position).max().orElse(0) + 1;
        List<Flashcard> retainedCards = oldCards.stream().filter(card -> retained.contains(card.getId())).toList();
        retainedCards.forEach(card -> card.moveTemporarily(card.getPosition() + offset));
        cards.saveAllAndFlush(retainedCards);
        cards.deleteAll(oldCards.stream().filter(card -> !retained.contains(card.getId())).toList());
        cards.flush();
        List<Flashcard> newCards = new java.util.ArrayList<>();
        for (FlashcardInput item : input) {
            if (item.id() == null) {
                newCards.add(new Flashcard(setId, item.position(), item.front(), item.back()));
            } else {
                Flashcard card = oldById.get(item.id());
                card.update(item.position(), item.front(), item.back());
            }
        }
        cards.saveAllAndFlush(newCards);
        cards.saveAllAndFlush(retainedCards);
    }

    private void persistNewCards(UUID setId, List<FlashcardInput> inputs) {
        cards.saveAllAndFlush(inputs.stream()
                .map(input -> new Flashcard(setId, input.position(), input.front(), input.back())).toList());
    }

    private void validateCards(List<FlashcardInput> inputs) {
        if (inputs == null) throw new ValidationException("cards are required");
        Set<Integer> positions = new HashSet<>();
        for (FlashcardInput input : inputs) {
            if (input == null || input.position() == null || input.position() < 1 || !positions.add(input.position())) {
                throw new ValidationException("Flashcard positions must be unique positive integers");
            }
            if (input.front() == null || input.back() == null) throw new ValidationException("Flashcard front and back are required");
        }
    }

    private FlashcardSet load(UUID id) { return sets.findById(id).orElseThrow(this::notFound); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Flashcard Set not found"); }
}
