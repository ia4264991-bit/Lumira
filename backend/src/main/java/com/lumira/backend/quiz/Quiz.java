package com.lumira.backend.quiz;

import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "quiz")
public class Quiz extends BaseEntity {
    @Embedded
    @AttributeOverride(name = "owningCardId", column = @Column(name = "owner_card_id"))
    @AttributeOverride(name = "owningUserId", column = @Column(name = "owner_user_id"))
    private ArtifactOwner owner;
    @Column(nullable = false, columnDefinition = "text")
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Quiz() { }

    public Quiz(ArtifactOwner owner, String title, String description) {
        this.owner = Objects.requireNonNull(owner);
        this.title = required(title, "title");
        this.description = Objects.requireNonNull(description, "description is required");
    }

    public ArtifactOwner getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String description) {
        if (title != null) this.title = required(title, "title");
        if (description != null) this.description = description;
    }

    public void transferOwnershipToCard(java.util.UUID cardId) { owner = ArtifactOwner.forCard(cardId); }

    private String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
}
