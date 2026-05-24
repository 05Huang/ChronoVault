package com.chronovault.repository;

import com.chronovault.entity.AsyncTask;
import com.chronovault.entity.AsyncTask.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long> {

    List<AsyncTask> findAllByOrderByCreatedAtDesc();

    List<AsyncTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AsyncTask> findByServerIdAndStatusIn(Long serverId, List<TaskStatus> statuses);

    List<AsyncTask> findByStatus(TaskStatus status);

    @Query("SELECT t FROM AsyncTask t WHERE t.server.id = :serverId AND t.status = 'RUNNING'")
    List<AsyncTask> findRunningByServerId(@Param("serverId") Long serverId);

    @Query("SELECT COUNT(t) FROM AsyncTask t WHERE t.status = :status")
    long countByStatus(@Param("status") TaskStatus status);
}
