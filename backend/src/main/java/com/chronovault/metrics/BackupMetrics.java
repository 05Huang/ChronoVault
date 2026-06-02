package com.chronovault.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Component
public class BackupMetrics {

    private final MeterRegistry registry;
    private Counter backupSuccessCounter;
    private Counter backupFailureCounter;
    private Counter restoreCounter;
    private Timer backupTimer;
    private Counter snapshotCounter;

    public BackupMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void init() {
        backupSuccessCounter = Counter.builder("chronovault.backup.success")
                .description("Number of successful backups")
                .register(registry);

        backupFailureCounter = Counter.builder("chronovault.backup.failure")
                .description("Number of failed backups")
                .register(registry);

        restoreCounter = Counter.builder("chronovault.restore.total")
                .description("Number of restore operations")
                .register(registry);

        backupTimer = Timer.builder("chronovault.backup.duration")
                .description("Duration of backup operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        snapshotCounter = Counter.builder("chronovault.snapshot.total")
                .description("Total number of snapshots created")
                .register(registry);
    }

    public void recordBackupSuccess() {
        backupSuccessCounter.increment();
    }

    public void recordBackupFailure() {
        backupFailureCounter.increment();
    }

    public void recordBackupDuration(long durationMs) {
        backupTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordSnapshot() {
        snapshotCounter.increment();
    }

    public void recordRestore() {
        restoreCounter.increment();
    }
}