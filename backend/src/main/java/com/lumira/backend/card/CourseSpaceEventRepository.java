package com.lumira.backend.card;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CourseSpaceEventRepository extends JpaRepository<CourseSpaceEvent, UUID> {
    List<CourseSpaceEvent> findByCardIdOrderByCreatedAtDesc(UUID cardId);
}
