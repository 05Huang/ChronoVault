package com.chronovault.repository;

import com.chronovault.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByOwnerId(Long ownerId);
    long countByOwnerId(Long ownerId);
}
