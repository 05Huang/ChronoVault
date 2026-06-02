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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotTagServiceTest {

    @Mock private SnapshotTagRepository tagRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private SnapshotTagService tagService;

    private Snapshot testSnapshot;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Test User").email("test@example.com").build();
        testSnapshot = Snapshot.builder().id(1L).title("Test Snapshot").status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
    }

    @Test
    void getTagsBySnapshot_returnsTags() {
        SnapshotTag tag = SnapshotTag.builder().id(1L).snapshot(testSnapshot).name("prod").color("#ff0000").createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.existsById(1L)).thenReturn(true);
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tag));

        List<SnapshotTagDTO> result = tagService.getTagsBySnapshot(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("prod", result.get(0).name());
    }

    @Test
    void getTagsBySnapshot_nonExistingSnapshot_throwsException() {
        when(snapshotRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> tagService.getTagsBySnapshot(999L));
    }

    @Test
    void addTag_validRequest_returnsTag() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(tagRepository.findBySnapshotIdAndName(1L, "test-tag")).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tagRepository.save(any(SnapshotTag.class))).thenAnswer(inv -> inv.getArgument(0));

        SnapshotTagDTO result = tagService.addTag(1L, new CreateTagRequest("test-tag", "#00ff00"), 1L);

        assertNotNull(result);
        assertEquals("test-tag", result.name());
        verify(tagRepository).save(any(SnapshotTag.class));
    }

    @Test
    void addTag_duplicateTag_throwsBadRequest() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        SnapshotTag existingTag = SnapshotTag.builder().id(1L).snapshot(testSnapshot).name("test-tag").build();
        when(tagRepository.findBySnapshotIdAndName(1L, "test-tag")).thenReturn(Optional.of(existingTag));

        assertThrows(BadRequestException.class,
                () -> tagService.addTag(1L, new CreateTagRequest("test-tag", "#00ff00"), 1L));
    }

    @Test
    void addTag_nonExistingSnapshot_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.addTag(999L, new CreateTagRequest("tag", "#000"), 1L));
    }

    @Test
    void removeTag_existingTag_removes() {
        when(snapshotRepository.existsById(1L)).thenReturn(true);
        SnapshotTag existingTag = SnapshotTag.builder().id(1L).snapshot(testSnapshot).name("prod").build();
        when(tagRepository.findBySnapshotIdAndName(1L, "prod")).thenReturn(Optional.of(existingTag));

        tagService.removeTag(1L, "prod");

        verify(tagRepository).deleteBySnapshotIdAndName(1L, "prod");
    }

    @Test
    void removeTag_nonExistingTag_throwsException() {
        when(snapshotRepository.existsById(1L)).thenReturn(true);
        when(tagRepository.findBySnapshotIdAndName(1L, "missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.removeTag(1L, "missing"));
    }

    @Test
    void bulkTag_addsTagsToMultipleSnapshots() {
        Snapshot s1 = Snapshot.builder().id(1L).title("S1").status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s2 = Snapshot.builder().id(2L).title("S2").status(Snapshot.SnapshotStatus.STABLE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(s1));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(s2));
        when(tagRepository.findBySnapshotIdAndName(anyLong(), eq("bulk-tag"))).thenReturn(Optional.empty());

        int count = tagService.bulkTag(List.of(1L, 2L), "bulk-tag", "#000", 1L);

        assertEquals(2, count);
        verify(tagRepository, times(2)).save(any(SnapshotTag.class));
    }
}
