package com.chronovault.service;

import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.VerificationJob;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.repository.VerificationJobRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationJobService {

    private final VerificationJobRepository jobRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    @Transactional(readOnly = true)
    public List<VerificationJob> getJobs() {
        return jobRepository.findAll();
    }

    @Transactional
    public VerificationJob createJob(VerificationJob job) {
        return jobRepository.save(job);
    }

    @Transactional
    public VerificationJob updateJob(Long id, VerificationJob updates) {
        VerificationJob job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("验证任务不存在: " + id));
        if (updates.getScheduleCron() != null) job.setScheduleCron(updates.getScheduleCron());
        job.setEnabled(updates.isEnabled());
        return jobRepository.save(job);
    }

    @Transactional
    public void deleteJob(Long id) {
        jobRepository.deleteById(id);
    }

    /**
     * Run verification for a specific job.
     */
    @Transactional
    public VerificationJob runJob(Long jobId) {
        VerificationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("验证任务不存在: " + jobId));

        job.setLastRunAt(LocalDateTime.now());
        job.setLastStatus("RUNNING");
        jobRepository.save(job);

        try {
            // Find the latest snapshot for the server
            List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(
                    job.getServer().getId());
            Snapshot latest = snapshots.stream()
                    .filter(s -> s.getHash() != null && !s.getHash().isBlank())
                    .findFirst()
                    .orElse(null);

            if (latest == null) {
                job.setLastStatus("SKIPPED");
                job.setLastError("没有可验证的快照");
                jobRepository.save(job);
                return job;
            }

            SshConnection conn = sshManager.getConnection(job.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new RuntimeException("无法安装 restic");
            }

            StorageTarget target = job.getStorageTarget() != null
                    ? job.getStorageTarget()
                    : storageTargetRepository.findAll().stream().findFirst().orElse(null);

            if (target == null) {
                job.setLastStatus("SKIPPED");
                job.setLastError("没有可用的存储目标");
                jobRepository.save(job);
                return job;
            }

            String repoUrl = resticClient.buildRepoUrl(target);
            boolean ok = resticClient.check(conn, repoUrl, resticPassword);

            job.setLastStatus(ok ? "SUCCESS" : "FAILED");
            job.setLastError(ok ? null : "完整性检查发现问题");
            jobRepository.save(job);

            log.info("Verification job {} completed: status={}", jobId, job.getLastStatus());
        } catch (Exception e) {
            job.setLastStatus("FAILED");
            job.setLastError(e.getMessage());
            jobRepository.save(job);
            log.error("Verification job {} failed: {}", jobId, e.getMessage());
        }

        return job;
    }

    /**
     * Run all enabled verification jobs. Scheduled hourly.
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void runScheduledVerifications() {
        List<VerificationJob> jobs = jobRepository.findByEnabledTrue();
        for (VerificationJob job : jobs) {
            try {
                log.info("Running scheduled verification for job {}", job.getId());
                runJob(job.getId());
            } catch (Exception e) {
                log.warn("Scheduled verification failed for job {}: {}", job.getId(), e.getMessage());
            }
        }
    }
}