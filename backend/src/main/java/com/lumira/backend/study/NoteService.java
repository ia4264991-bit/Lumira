package com.lumira.backend.study;

import com.lumira.backend.card.Card;
import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.common.error.ValidationException;
import com.lumira.backend.resource.ArtifactType;
import com.lumira.backend.resource.ResourceAuthorizationService;
import com.lumira.backend.resource.ResourceShare;
import com.lumira.backend.resource.ResourceShareRepository;
import com.lumira.backend.resource.ResourceSharingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {
    private final NoteRepository notes;
    private final ResourceShareRepository shares;
    private final ResourceAuthorizationService authorization;
    private final ResourceSharingService sharing;

    public NoteService(NoteRepository notes, ResourceShareRepository shares,
            ResourceAuthorizationService authorization, ResourceSharingService sharing) {
        this.notes = notes;
        this.shares = shares;
        this.authorization = authorization;
        this.sharing = sharing;
    }

    @Transactional
    public NoteResponse create(UUID cardId, UUID actorId, NoteCreateRequest request) {
        authorization.requireCardUploadForUpdate(cardId, actorId);
        Note note = notes.saveAndFlush(new Note(ArtifactOwner.forCard(cardId), request.title(), request.content()));
        sharing.createCardShareIfShared(ArtifactType.NOTE, note.getId(), cardId, actorId);
        return NoteResponse.from(note);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID cardId, UUID actorId) {
        Card card = authorization.requireCardReadable(cardId, actorId);
        if (!card.isShared()) return notes.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId).stream().map(NoteResponse::from).toList();
        return shares.findByCardIdAndActiveTrueOrderByCreatedAtAsc(cardId).stream()
                .filter(s -> s.getNoteId() != null).map(ResourceShare::getNoteId).map(notes::findById)
                .flatMap(java.util.Optional::stream).sorted(Comparator.comparing(Note::getCreatedAt).reversed())
                .map(NoteResponse::from).toList();
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
