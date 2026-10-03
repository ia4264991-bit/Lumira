package com.lumira.backend.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceShareRepository extends JpaRepository<ResourceShare, UUID> {
    Optional<ResourceShare> findByResourceIdAndCardId(UUID resourceId, UUID cardId);
    Optional<ResourceShare> findByNoteIdAndCardId(UUID noteId, UUID cardId);
    Optional<ResourceShare> findByStudySetIdAndCardId(UUID studySetId, UUID cardId);
    Optional<ResourceShare> findByQuizIdAndCardId(UUID quizId, UUID cardId);
    boolean existsByResourceIdAndCardIdAndActiveTrue(UUID resourceId, UUID cardId);
    List<ResourceShare> findByCardIdAndActiveTrue(UUID cardId);

    List<ResourceShare> findByCardIdAndActiveTrueOrderByResourceIdAsc(UUID cardId);
    List<ResourceShare> findByCardIdAndActiveTrueOrderByCreatedAtAsc(UUID cardId);
    List<ResourceShare> findByNoteIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID noteId);
    List<ResourceShare> findByStudySetIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID studySetId);
    List<ResourceShare> findByQuizIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID quizId);
    List<ResourceShare> findByNoteIdInAndActiveTrueOrderByNoteIdAscCreatedAtAscIdAsc(List<UUID> noteIds);
    List<ResourceShare> findByStudySetIdInAndActiveTrueOrderByStudySetIdAscCreatedAtAscIdAsc(List<UUID> studySetIds);
    List<ResourceShare> findByQuizIdInAndActiveTrueOrderByQuizIdAscCreatedAtAscIdAsc(List<UUID> quizIds);

    List<ResourceShare> findByResourceIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID resourceId);

    List<ResourceShare> findByResourceIdInAndActiveTrueOrderByResourceIdAscCreatedAtAscIdAsc(List<UUID> resourceIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ResourceShare s where s.resourceId = :resourceId and s.cardId = :cardId")
    Optional<ResourceShare> findByResourceIdAndCardIdForUpdate(@Param("resourceId") UUID resourceId,
                                                                @Param("cardId") UUID cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ResourceShare s where s.noteId = :artifactId and s.cardId = :cardId")
    Optional<ResourceShare> findNoteShareForUpdate(@Param("artifactId") UUID artifactId, @Param("cardId") UUID cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ResourceShare s where s.studySetId = :artifactId and s.cardId = :cardId")
    Optional<ResourceShare> findStudySetShareForUpdate(@Param("artifactId") UUID artifactId, @Param("cardId") UUID cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ResourceShare s where s.quizId = :artifactId and s.cardId = :cardId")
    Optional<ResourceShare> findQuizShareForUpdate(@Param("artifactId") UUID artifactId, @Param("cardId") UUID cardId);

    @Query(value = "select * from artifact_share s where s.active = true and " +
            "((:type = 'resource' and s.resource_id = :artifactId) or " +
            "(:type = 'note' and s.note_id = :artifactId) or " +
            "(:type = 'studyset' and s.study_set_id = :artifactId) or " +
            "(:type = 'quiz' and s.quiz_id = :artifactId)) order by s.created_at, s.id", nativeQuery = true)
    List<ResourceShare> findActiveForArtifact(@Param("type") String type, @Param("artifactId") UUID artifactId);

    @Query(value = "select * from artifact_share s where s.card_id=:cardId and " +
            "((:type = 'resource' and s.resource_id=:artifactId) or " +
            "(:type = 'note' and s.note_id=:artifactId) or " +
            "(:type = 'studyset' and s.study_set_id=:artifactId) or " +
            "(:type = 'quiz' and s.quiz_id=:artifactId))", nativeQuery = true)
    Optional<ResourceShare> findForArtifactCard(@Param("type") String type,
            @Param("artifactId") UUID artifactId, @Param("cardId") UUID cardId);
}
