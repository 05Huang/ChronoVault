package com.chronovault.repository;

import com.chronovault.entity.AiRecommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {
    List<AiRecommendation> findByApplied(Boolean applied);

    /**
     * Limited query to prevent OOM when loading recommendations.
     */
    List<AiRecommendation> findTop50ByOrderByCreatedAtDesc();

    /** Paged query to prevent OOM on high-data-volume systems */
    Page<AiRecommendation> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
