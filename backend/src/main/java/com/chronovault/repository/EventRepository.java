package com.chronovault.repository;

import com.chronovault.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByCreatedAtDesc();
    List<Event> findByLevelOrderByCreatedAtDesc(Event.EventLevel level);
    List<Event> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
}
