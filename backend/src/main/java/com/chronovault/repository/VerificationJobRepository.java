package com.chronovault.repository;

import com.chronovault.entity.VerificationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VerificationJobRepository extends JpaRepository<VerificationJob, Long> {
    List<VerificationJob> findByEnabledTrue();
}