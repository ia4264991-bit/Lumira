package com.lumira.backend.study;

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
@Table(name = "study_set")
public class StudySet extends BaseEntity {
    @Embedded
    @AttributeOverride(name = "owningCardId", column = @Column(name = "owner_card_id"))
    @AttributeOverride(name = "owningUserId", column = @Column(name = "owner_user_id"))
    private ArtifactOwner owner;
    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudySet() { }
    public StudySet(ArtifactOwner owner, String title, String description) {
        this.owner = Objects.requireNonNull(owner);
        this.title = requiredTitle(title);
        this.description = Objects.requireNonNull(description, "description is required");
    }
    public ArtifactOwner getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void transferOwnershipToCard(java.util.UUID cardId) { owner = ArtifactOwner.forCard(cardId); }
    public void update(String title, String description) {
        if (title != null) this.title = requiredTitle(title);
        if (description != null) this.description = description;
    }
    private String requiredTitle(String value) {
        Objects.requireNonNull(value, "title is required");
        if (value.isBlank()) throw new IllegalArgumentException("title must not be blank");
        return value.strip();
    }
}
