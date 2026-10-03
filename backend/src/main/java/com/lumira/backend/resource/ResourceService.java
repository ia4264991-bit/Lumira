package com.lumira.backend.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardMembership;
import com.lumira.backend.card.CardMembershipRepository;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.card.MembershipRole;
import com.lumira.backend.card.MembershipStatus;
import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.error.ForbiddenException;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ResourceService {
    private final ResourceRepository resources;
    private final ResourceShareRepository shares;
    private final CardRepository cards;
    private final CardMembershipRepository memberships;
    private final UserRepository users;
    private final ResourceFileProcessor processor;
    private final ResourcePublicationService publication;
    private final ObjectMapper mapper;
    private final long maxUploadBytes;

    public ResourceService(ResourceRepository resources, ResourceShareRepository shares, CardRepository cards,
            CardMembershipRepository memberships, UserRepository users, ResourceFileProcessor processor,
            ResourcePublicationService publication, ObjectMapper mapper,
            @Value("${lumira.resource.max-upload-bytes:20971520}") long maxUploadBytes) {
        this.resources = resources;
        this.shares = shares;
        this.cards = cards;
        this.memberships = memberships;
        this.users = users;
        this.processor = processor;
        this.publication = publication;
        this.mapper = mapper;
        this.maxUploadBytes = maxUploadBytes;
    }

    public ResourceResponse createForCard(UUID cardId, UUID actorId, MultipartFile file, String title) {
        Card card = cardForUpload(cardId, actorId);
        if (file == null || file.isEmpty()) throw new com.lumira.backend.common.error.ValidationException("A non-empty file is required");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException ex) { throw new com.lumira.backend.common.error.ValidationException("The uploaded file could not be read"); }
        String filename = safeFilename(file.getOriginalFilename());
        if (title == null || title.isBlank()) throw new com.lumira.backend.common.error.ValidationException("Resource title is required");
        String effectiveTitle = title.strip();
        if (effectiveTitle.length() > 240) throw new com.lumira.backend.common.error.ValidationException("Resource title must be 240 characters or fewer");
        byte[] retainedBytes = bytes.length <= maxUploadBytes ? bytes : null;
        Resource resource = resources.saveAndFlush(new Resource(ArtifactOwner.forCard(card.getId()), effectiveTitle,
                filename, file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                bytes.length, retainedBytes, mapper.createArrayNode(), mapper.createObjectNode()));
        process(resource, card.getId(), actorId, true);
        return ResourceResponse.from(resource);
    }

    /** Internal domain path for resources not yet organized into a Card (AD-021). */
    public ResourceResponse createForUser(UUID userId, MultipartFile file, String title) {
        if (!users.existsById(userId)) throw new ResourceNotFoundException("User not found");
        if (file == null || file.isEmpty()) throw new com.lumira.backend.common.error.ValidationException("A non-empty file is required");
        try {
            byte[] bytes = file.getBytes();
            String filename = safeFilename(file.getOriginalFilename());
            if (title == null || title.isBlank()) title = filename;
            String effectiveTitle = title.strip();
            byte[] retainedBytes = bytes.length <= maxUploadBytes ? bytes : null;
            Resource resource = resources.saveAndFlush(new Resource(ArtifactOwner.forUser(userId), effectiveTitle,
                    filename, file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    bytes.length, retainedBytes, mapper.createArrayNode(), mapper.createObjectNode()));
            process(resource, null, userId, false);
            return ResourceResponse.from(resource);
        } catch (IOException ex) {
            throw new com.lumira.backend.common.error.ValidationException("The uploaded file could not be read");
        }
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> listForCard(UUID cardId, UUID actorId) {
        Card card = cards.findById(cardId).orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        authorizeRead(card, actorId);
        return resources.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId).stream()
                .filter(r -> !card.isShared() || card.isOwnedBy(actorId)
                        || shares.existsByResourceIdAndCardIdAndActiveTrue(r.getId(), cardId))
                .map(ResourceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(UUID resourceId, UUID actorId) {
        Resource resource = resources.findById(resourceId).orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        authorizeResourceRead(resource, actorId);
        return ResourceResponse.from(resource);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> download(UUID resourceId, UUID actorId) {
        Resource resource = resources.findById(resourceId).orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        authorizeResourceRead(resource, actorId);
        if (resource.getOriginalBytes() == null) throw new ResourceNotFoundException("Original file is unavailable");
        String safeName = resource.getOriginalFilename().replaceAll("[\\\"\\r\\n]", "_");
        Set<String> inlineTypes = Set.of("application/pdf", "text/plain", "text/csv", "image/png", "image/jpeg", "image/webp");
        boolean inline = resource.getStatus() == ResourceStatus.READY && inlineTypes.contains(resource.getMimeType());
        MediaType responseType = inline ? MediaType.parseMediaType(resource.getMimeType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(responseType)
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment") + "; filename=\"" + safeName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource.getOriginalBytes());
    }

    public ResourceResponse reprocess(UUID resourceId, UUID actorId) {
        Resource resource = resources.findById(resourceId).orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        authorizeOwner(resource, actorId);
        process(resource, null, actorId, false);
        return ResourceResponse.from(resource);
    }

    private void process(Resource resource, UUID cardId, UUID actorId, boolean publicationEvent) {
        if (resource.getOriginalBytes() == null) {
            resource.fail("The file exceeds the configured upload-size limit or original bytes are unavailable.");
            if (publicationEvent) publication.completeUpload(resource, cardId, actorId);
            else resources.saveAndFlush(resource);
            return;
        }
        resource.beginProcessing();
        resources.saveAndFlush(resource);
        try {
            ResourceFileProcessor.ProcessedContent processed = processor.process(resource.getOriginalFilename(),
                    resource.getMimeType(), resource.getOriginalBytes());
            resource.complete(processed.chunks(), processed.metadata());
        } catch (ResourceFileProcessor.ProcessingFailure ex) {
            resource.fail(ex.getMessage());
        } catch (RuntimeException ex) {
            resource.fail("The file could not be processed safely. Retry after checking the file contents.");
        }
        if (publicationEvent) publication.completeUpload(resource, cardId, actorId);
        else resources.saveAndFlush(resource);
    }

    private Card cardForUpload(UUID cardId, UUID actorId) {
        Card card = cards.findById(cardId).orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (!users.existsById(actorId)) throw new ResourceNotFoundException("Card not found");
        if (!card.isShared()) {
            if (!card.isOwnedBy(actorId)) throw new ResourceNotFoundException("Card not found");
            return card;
        }
        CardMembership membership = memberships.findFirstByCardIdAndUserIdAndStatusIn(cardId, actorId,
                        List.of(MembershipStatus.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (membership.getRole() != MembershipRole.OWNER && membership.getRole() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Owner or active Admin role is required to upload a Course Space Resource");
        }
        return card;
    }

    private void authorizeRead(Card card, UUID actorId) {
        if (!users.existsById(actorId)) throw new ResourceNotFoundException("Resource not found");
        if (!card.isShared()) {
            if (!card.isOwnedBy(actorId)) throw new ResourceNotFoundException("Resource not found");
            return;
        }
        boolean owner = card.isOwnedBy(actorId);
        boolean member = memberships.existsByCardIdAndUserIdAndStatus(card.getId(), actorId, MembershipStatus.ACTIVE);
        if (!owner && !member) throw new ResourceNotFoundException("Resource not found");
    }

    private void authorizeResourceRead(Resource resource, UUID actorId) {
        if (resource.getOwner().isUserOwned()) {
            if (!actorId.equals(resource.getOwner().getOwningUserId())) throw new ResourceNotFoundException("Resource not found");
            return;
        }
        UUID ownerCardId = resource.getOwner().getOwningCardId();
        if (cards.findByIdAndOwnerId(ownerCardId, actorId).isPresent()) return;
        boolean sharedAccess = shares.findByResourceIdAndCardId(resource.getId(), ownerCardId)
                .filter(ResourceShare::isActive).isPresent()
                && cards.findById(ownerCardId).filter(Card::isShared).isPresent()
                && memberships.existsByCardIdAndUserIdAndStatus(ownerCardId, actorId, MembershipStatus.ACTIVE);
        if (!sharedAccess) throw new ResourceNotFoundException("Resource not found");
    }

    private void authorizeOwner(Resource resource, UUID actorId) {
        if (resource.getOwner().isUserOwned()) {
            if (!actorId.equals(resource.getOwner().getOwningUserId())) throw new ResourceNotFoundException("Resource not found");
            return;
        }
        if (cards.findByIdAndOwnerId(resource.getOwner().getOwningCardId(), actorId).isEmpty())
            throw new ResourceNotFoundException("Resource not found");
    }

    private String safeFilename(String original) {
        if (original == null || original.isBlank()) return "upload";
        String value = original.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        return value.length() <= 512 ? value : value.substring(value.length() - 512);
    }
}
