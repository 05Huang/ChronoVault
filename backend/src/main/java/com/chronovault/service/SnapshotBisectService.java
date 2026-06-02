package com.chronovault.service;

import com.chronovault.dto.snapshot.BisectMarkRequest;
import com.chronovault.dto.snapshot.BisectSessionDTO;
import com.chronovault.dto.snapshot.BisectStartRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.entity.Snapshot;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotBisectService {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotTagRepository tagRepository;

    private final Map<String, BisectState> sessions = new ConcurrentHashMap<>();

    /**
     * Start a bisect session: user provides known-good and known-bad snapshots.
     * The system orders all snapshots between them chronologically and picks the middle one.
     */
    public BisectSessionDTO start(BisectStartRequest request) {
        Snapshot good = snapshotRepository.findById(request.goodSnapshotId())
                .orElseThrow(() -> new ResourceNotFoundException("好快照不存在: " + request.goodSnapshotId()));
        Snapshot bad = snapshotRepository.findById(request.badSnapshotId())
                .orElseThrow(() -> new ResourceNotFoundException("坏快照不存在: " + request.badSnapshotId()));

        if (!good.getServer().getId().equals(bad.getServer().getId())) {
            throw new BadRequestException("好快照和坏快照必须属于同一台服务器");
        }
        if (good.getServer().getId() != request.serverId()) {
            throw new BadRequestException("服务器ID与快照不匹配");
        }

        // Get all snapshots for this server, ordered by creation time ascending
        List<Snapshot> allSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(request.serverId());
        // Reverse to get ascending order (oldest first)
        List<Snapshot> ascending = new ArrayList<>(allSnapshots);
        Collections.reverse(ascending);

        // Filter to only snapshots between good and bad (inclusive)
        List<Snapshot> candidates = new ArrayList<>();
        boolean foundGood = false;
        boolean foundBad = false;
        for (Snapshot s : ascending) {
            if (s.getId().equals(good.getId())) foundGood = true;
            if (s.getId().equals(bad.getId())) foundBad = true;
            if (foundGood && foundBad) {
                candidates.add(s);
                break;
            }
            if (foundGood) {
                candidates.add(s);
            }
        }

        // Ensure bad is included if we found good but not bad yet
        if (foundGood && !foundBad) {
            candidates.add(bad);
        }

        if (candidates.size() < 2) {
            throw new BadRequestException("好快照和坏快照之间没有足够的快照进行二分查找");
        }

        // Binary search: pick the middle
        int mid = candidates.size() / 2;
        Snapshot current = candidates.get(mid);
        int totalSteps = (int) Math.ceil(Math.log(candidates.size()) / Math.log(2));

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        BisectState state = new BisectState(
                sessionId, request.serverId(), good.getId(), bad.getId(),
                current.getId(), candidates, 0, totalSteps, "IN_PROGRESS", null
        );
        sessions.put(sessionId, state);

        log.info("Bisect session {} started: {} candidates, {} steps, current={}",
                sessionId, candidates.size(), totalSteps, current.getId());

        return toDTO(state);
    }

    /**
     * Mark a snapshot as good or bad. The binary search narrows the range.
     */
    public BisectSessionDTO mark(String sessionId, BisectMarkRequest request) {
        BisectState state = sessions.get(sessionId);
        if (state == null) {
            throw new ResourceNotFoundException("二分查找会话不存在: " + sessionId);
        }
        if (!"IN_PROGRESS".equals(state.status)) {
            throw new BadRequestException("此二分查找会话已结束");
        }

        String verdict = request.verdict();
        if (!"good".equals(verdict) && !"bad".equals(verdict)) {
            throw new BadRequestException("判定结果必须为 good 或 bad");
        }

        state.stepsCompleted++;

        // If this is the last step or we found the culprit
        if (state.stepsCompleted >= state.totalSteps || state.candidates.size() <= 2) {
            // The current snapshot is the one that caused the issue (the bad one in the pair)
            Snapshot culprit = snapshotRepository.findById(request.snapshotId())
                    .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + request.snapshotId()));

            state.culpritSnapshotId = request.snapshotId();
            state.culpritSnapshotName = culprit.getTitle();
            state.status = "FOUND";
            state.currentSnapshotId = request.snapshotId();

            log.info("Bisect session {} FOUND culprit: snapshot {} ({})", sessionId, culprit.getId(), culprit.getTitle());
            return toDTO(state);
        }

        // Narrow the candidates list
        List<Snapshot> newCandidates = new ArrayList<>();
        boolean foundCurrent = false;
        for (Snapshot s : state.candidates) {
            if (s.getId().equals(request.snapshotId())) {
                foundCurrent = true;
                if ("bad".equals(verdict)) {
                    // Current is bad, keep it and everything before it (towards good)
                    newCandidates.add(s);
                }
                // If good, discard this and everything before it
                continue;
            }
            if (!foundCurrent) {
                // Before current snapshot
                if ("bad".equals(verdict)) {
                    // Current is bad, discard earlier ones (they are good)
                    continue;
                } else {
                    // Current is good, keep earlier ones (towards bad)
                    newCandidates.add(s);
                }
            } else {
                // After current snapshot
                if ("bad".equals(verdict)) {
                    // Current is bad, discard later ones (they are bad too, beyond current)
                    break;
                } else {
                    // Current is good, keep later ones (towards bad)
                    newCandidates.add(s);
                }
            }
        }

        state.candidates = newCandidates;

        // Pick the next middle
        int mid = newCandidates.size() / 2;
        state.currentSnapshotId = newCandidates.get(mid).getId();

        log.info("Bisect session {} step {}: {} candidates remaining, next={}",
                sessionId, state.stepsCompleted, newCandidates.size(), state.currentSnapshotId);

        return toDTO(state);
    }

    /**
     * Get the current state of a bisect session.
     */
    public BisectSessionDTO getSession(String sessionId) {
        BisectState state = sessions.get(sessionId);
        if (state == null) {
            throw new ResourceNotFoundException("二分查找会话不存在: " + sessionId);
        }
        return toDTO(state);
    }

    private BisectSessionDTO toDTO(BisectState state) {
        Snapshot currentSnap = snapshotRepository.findById(state.currentSnapshotId).orElse(null);
        Snapshot goodSnap = snapshotRepository.findById(state.goodSnapshotId).orElse(null);
        Snapshot badSnap = snapshotRepository.findById(state.badSnapshotId).orElse(null);

        String currentName = currentSnap != null ? currentSnap.getTitle() : "未知";
        List<SnapshotDTO> candidateDTOs = state.candidates.stream()
                .map(s -> {
                    List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(s.getId())
                            .stream().map(SnapshotTagDTO::from).toList();
                    return SnapshotDTO.from(s, tags);
                })
                .toList();

        return new BisectSessionDTO(
                state.sessionId,
                state.serverId,
                state.goodSnapshotId,
                state.badSnapshotId,
                state.currentSnapshotId,
                currentName,
                state.totalSteps - state.stepsCompleted,
                state.totalSteps,
                state.status,
                state.culpritSnapshotName,
                candidateDTOs
        );
    }

    /**
     * Internal bisect state (in-memory, not persisted).
     */
    private static class BisectState {
        final String sessionId;
        final Long serverId;
        final Long goodSnapshotId;
        final Long badSnapshotId;
        Long currentSnapshotId;
        List<Snapshot> candidates;
        int stepsCompleted;
        final int totalSteps;
        String status;
        String culpritSnapshotName;
        Long culpritSnapshotId;

        BisectState(String sessionId, Long serverId, Long goodSnapshotId, Long badSnapshotId,
                    Long currentSnapshotId, List<Snapshot> candidates, int stepsCompleted,
                    int totalSteps, String status, String culpritSnapshotName) {
            this.sessionId = sessionId;
            this.serverId = serverId;
            this.goodSnapshotId = goodSnapshotId;
            this.badSnapshotId = badSnapshotId;
            this.currentSnapshotId = currentSnapshotId;
            this.candidates = candidates;
            this.stepsCompleted = stepsCompleted;
            this.totalSteps = totalSteps;
            this.status = status;
            this.culpritSnapshotName = culpritSnapshotName;
        }
    }
}
