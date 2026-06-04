package com.chronovault.repository;

import com.chronovault.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByCreatedAtDesc();
    List<Event> findByLevelOrderByCreatedAtDesc(Event.EventLevel level);
    List<Event> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /** Paged query to prevent OOM on high-event-volume systems */
    Page<Event> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Paged query for events within a time range — replaces full-table scans */
    Page<Event> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /** Load events since cutoff, limited to prevent memory issues on high-volume systems */
    List<Event> findTop10000ByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /** Count events by source since a given time */
    @Query("SELECT e.source, COUNT(e) FROM Event e WHERE e.createdAt >= :since GROUP BY e.source")
    List<Object[]> countBySourceSince(@Param("since") LocalDateTime since);
}
