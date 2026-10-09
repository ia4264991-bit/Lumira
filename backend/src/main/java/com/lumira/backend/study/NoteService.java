package com.lumira.backend.study;

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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {
    private final NoteRepository notes;
    private final ResourceShareRepository shares;
    private final ResourceAuthorizationService authorization;
    public NoteService(NoteRepository notes, ResourceShareRepository shares,
            ResourceAuthorizationService authorization) {
        this.notes = notes;
        this.shares = shares;
        this.authorization = authorization;
    }

    @Transactional
    public NoteResponse create(UUID cardId, UUID actorId, NoteCreateRequest request) {
        authorization.requirePrivateArtifactCardForCreate(cardId, actorId);
        Note note = notes.saveAndFlush(new Note(ArtifactOwner.forCard(cardId), request.title(), request.content()));
        return NoteResponse.from(note);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID cardId, UUID actorId) {
        Card card = authorization.requireCardReadable(cardId, actorId);
        if (!card.isShared()) return notes.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId).stream().map(NoteResponse::from).toList();
        List<UUID> sharedIds = shares.findByCardIdAndActiveTrueOrderByCreatedAtAsc(cardId).stream()
                .filter(s -> s.getNoteId() != null).map(ResourceShare::getNoteId).toList();
        var visible = new LinkedHashMap<UUID, Note>();
        if (card.isOwnedBy(actorId)) {
            notes.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId).forEach(note -> visible.put(note.getId(), note));
        }
        notes.findAllById(sharedIds).forEach(note -> visible.put(note.getId(), note));
        return visible.values().stream().sorted(Comparator.comparing(Note::getCreatedAt).reversed())
                .map(note -> NoteResponse.from(note, sharedIds.contains(note.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse get(UUID noteId, UUID actorId) {
        Note note = notes.findById(noteId).orElseThrow(() -> notFound());
        authorization.requireArtifactReadable(note.getOwner(), ArtifactType.NOTE, noteId, "Note not found", actorId);
        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse update(UUID noteId, UUID actorId, NotePatchRequest request) {
        Note note = notes.findByIdForUpdate(noteId).orElseThrow(() -> notFound());
        authorization.requireArtifactOwner(note.getOwner(), "Note not found", actorId);
        if (request.title() != null && request.title().isBlank()) throw new ValidationException("title must not be blank");
        note.update(request.title(), request.content());
        notes.saveAndFlush(note);
        return NoteResponse.from(note);
    }

    @Transactional
    public void delete(UUID noteId, UUID actorId) {
        Note note = notes.findByIdForUpdate(noteId).orElseThrow(() -> notFound());
        authorization.requireArtifactOwner(note.getOwner(), "Note not found", actorId);
        notes.delete(note);
    }

    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Note not found"); }
}
