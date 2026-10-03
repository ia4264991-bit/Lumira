package com.lumira.backend.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Objects;

@Entity
@Table(name = "resource")
public class Resource extends BaseEntity {
    @Embedded
    @AttributeOverride(name = "owningCardId", column = @Column(name = "owner_card_id"))
    @AttributeOverride(name = "owningUserId", column = @Column(name = "owner_user_id"))
    private ArtifactOwner owner;

    @Column(name = "title", nullable = false, length = 240)
    private String title;
    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;
    @Column(name = "mime_type", nullable = false, length = 160)
    private String mimeType;
    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 16)
    private ResourceStatus status;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "original_bytes", columnDefinition = "bytea")
    private byte[] originalBytes;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_content", nullable = false, columnDefinition = "jsonb")
    private JsonNode extractedContent;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_metadata", nullable = false, columnDefinition = "jsonb")
    private JsonNode imageMetadata;

    protected Resource() { }

    public Resource(ArtifactOwner owner, String title, String originalFilename, String mimeType,
                    long fileSizeBytes, byte[] originalBytes, JsonNode emptyContent, JsonNode emptyMetadata) {
        this.owner = Objects.requireNonNull(owner);
        this.title = requireText(title, "title");
        this.originalFilename = requireText(originalFilename, "filename");
        this.mimeType = requireText(mimeType, "mimeType");
        this.fileSizeBytes = fileSizeBytes;
        this.originalBytes = originalBytes;
        this.extractedContent = Objects.requireNonNull(emptyContent);
        this.imageMetadata = Objects.requireNonNull(emptyMetadata);
        this.status = ResourceStatus.UPLOADED;
    }

    public ArtifactOwner getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public ResourceStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public byte[] getOriginalBytes() { return originalBytes; }
    public JsonNode getExtractedContent() { return extractedContent; }
    public JsonNode getImageMetadata() { return imageMetadata; }

    public void transferOwnershipToCard(java.util.UUID cardId) {
        this.owner = ArtifactOwner.forCard(Objects.requireNonNull(cardId));
    }

    public void beginProcessing() {
        if (originalBytes == null) throw new IllegalStateException("Original file bytes are unavailable");
        status = ResourceStatus.PROCESSING;
        failureReason = null;
        extractedContent = JsonNodeFactory.instance.arrayNode();
        imageMetadata = JsonNodeFactory.instance.objectNode();
    }

    public void complete(JsonNode content, JsonNode metadata) {
        if (status != ResourceStatus.PROCESSING) throw new IllegalStateException("Resource is not processing");
        extractedContent = Objects.requireNonNull(content);
        imageMetadata = Objects.requireNonNull(metadata);
        status = ResourceStatus.READY;
        failureReason = null;
    }

    public void fail(String reason) {
        status = ResourceStatus.FAILED;
        failureReason = requireText(reason, "failure reason");
        extractedContent = JsonNodeFactory.instance.arrayNode();
        imageMetadata = JsonNodeFactory.instance.objectNode();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.strip();
    }
}
