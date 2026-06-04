package com.chronovault.repository;

import com.chronovault.entity.AiInsight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {
    List<AiInsight> findByCategory(String category);

    /**
     * Limited query to prevent OOM when loading insights.
     */
    List<AiInsight> findTop50ByOrderByCreatedAtDesc();

    /** Paged query to prevent OOM on high-data-volume systems */
    Page<AiInsight> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
