package com.lumira.backend.flashcard;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlashcardSetResponseMapper {
    private final FlashcardRepository cards;

    public FlashcardSetResponseMapper(FlashcardRepository cards) { this.cards = cards; }

    public FlashcardSetResponse from(FlashcardSet set) {
        List<FlashcardResponse> cardResponses = cards.findByFlashcardSetIdOrderByPositionAscIdAsc(set.getId())
                .stream().map(card -> new FlashcardResponse(card.getId(), card.getPosition(), card.getFront(), card.getBack()))
                .toList();
        return new FlashcardSetResponse(set.getId(), set.getOwner().getOwningCardId(),
                set.getOwner().getOwningUserId(), set.getTitle(), set.getDescription(),
                cardResponses, set.getCreatedAt(), set.getUpdatedAt());
    }
}
