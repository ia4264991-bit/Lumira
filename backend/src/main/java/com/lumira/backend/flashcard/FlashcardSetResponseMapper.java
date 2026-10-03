package com.lumira.backend.flashcard;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class FlashcardSetResponseMapper {
    private final FlashcardRepository cards;

    public FlashcardSetResponseMapper(FlashcardRepository cards) { this.cards = cards; }

    public FlashcardSetResponse from(FlashcardSet set) {
        List<FlashcardResponse> cardResponses = cards.findByFlashcardSetIdOrderByPositionAscIdAsc(set.getId())
                .stream().map(card -> new FlashcardResponse(card.getId(), card.getPosition(), card.getFront(), card.getBack()))
                .toList();
        return response(set, cardResponses);
    }

    public List<FlashcardSetResponse> fromMany(List<FlashcardSet> sets) {
        if (sets.isEmpty()) return List.of();
        Map<UUID, List<FlashcardResponse>> cardsBySet = cards.findByFlashcardSetIdInOrderByFlashcardSetIdAscPositionAscIdAsc(
                        sets.stream().map(FlashcardSet::getId).toList()).stream()
                .collect(Collectors.groupingBy(Flashcard::getFlashcardSetId,
                        Collectors.mapping(card -> new FlashcardResponse(card.getId(), card.getPosition(), card.getFront(), card.getBack()),
                                Collectors.toList())));
        return sets.stream().map(set -> response(set, cardsBySet.getOrDefault(set.getId(), List.of()))).toList();
    }

    private FlashcardSetResponse response(FlashcardSet set, List<FlashcardResponse> cardResponses) {
        return new FlashcardSetResponse(set.getId(), set.getOwner().getOwningCardId(),
                set.getOwner().getOwningUserId(), set.getTitle(), set.getDescription(),
                cardResponses, set.getCreatedAt(), set.getUpdatedAt());
    }
}
