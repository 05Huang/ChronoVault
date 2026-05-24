package com.chronovault.service;

import com.chronovault.dto.snapshot.CreateTagRequest;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.SnapshotTag;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
import com.chronovault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotTagService {

    private final SnapshotTagRepository tagRepository;
    private final SnapshotRepository snapshotRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SnapshotTagDTO> getTagsBySnapshot(Long snapshotId) {
        if (!snapshotRepository.existsById(snapshotId)) {
            throw new ResourceNotFoundException("快照不存在: " + snapshotId);
        }
        return tagRepository.findBySnapshotIdOrderByCreatedAtDesc(snapshotId).stream()
                .map(SnapshotTagDTO::from)
                .toList();
    }

    @Transactional
    public SnapshotTagDTO addTag(Long snapshotId, CreateTagRequest request, Long userId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        // Check for duplicate tag name on the same snapshot
        if (tagRepository.findBySnapshotIdAndName(snapshotId, request.name()).isPresent()) {
            throw new BadRequestException("标签 '" + request.name() + "' 已存在");
        }

        User user = userRepository.findById(userId).orElse(null);

        SnapshotTag tag = SnapshotTag.builder()
                .snapshot(snapshot)
                .name(request.name())
                .color(request.color())
                .createdBy(user)
                .build();

        tag = tagRepository.save(tag);
        log.info("Tag '{}' added to snapshot {} by user {}", request.name(), snapshotId, userId);
        return SnapshotTagDTO.from(tag);
    }

    @Transactional
    public void removeTag(Long snapshotId, String tagName) {
        if (!snapshotRepository.existsById(snapshotId)) {
            throw new ResourceNotFoundException("快照不存在: " + snapshotId);
        }
        tagRepository.findBySnapshotIdAndName(snapshotId, tagName)
                .orElseThrow(() -> new ResourceNotFoundException("标签 '" + tagName + "' 不存在"));
        tagRepository.deleteBySnapshotIdAndName(snapshotId, tagName);
        log.info("Tag '{}' removed from snapshot {}", tagName, snapshotId);
    }

    @Transactional(readOnly = true)
    public List<SnapshotTagDTO> getAllTags() {
        return tagRepository.findAll().stream()
                .map(SnapshotTagDTO::from)
                .toList();
    }
}
