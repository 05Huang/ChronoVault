# ChronoVault — Git for Server State: Feature Specifications

## Overview
ChronoVault aims to be the "Git for Server State" — applying git's version control concepts to server infrastructure management.

## Feature 1: Snapshot Tags (git tag)

### Purpose
Mark important snapshots with human-readable labels, like git tags mark commits.

### Database Schema
```sql
CREATE TABLE snapshot_tags (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(7) NOT NULL DEFAULT '#0058be',
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(name)  -- Each tag name points to exactly one snapshot
);
CREATE INDEX idx_snapshot_tags_snapshot_id ON snapshot_tags(snapshot_id);
```

### API Endpoints
- `POST /api/snapshots/{id}/tags` — Create tag
  - Body: `{"name": "v1.0", "color": "#0058be"}`
  - Returns: 201 with tag DTO, or 409 if name exists
- `DELETE /api/snapshots/{id}/tags/{tagId}` — Delete tag
  - Returns: 204
- `GET /api/snapshots/tags` — List all unique tag names
  - Returns: `["v1.0", "production", "before-upgrade"]`

### Frontend
- Tags displayed as colored badges on snapshot cards
- AddTagModal with name input + color picker (6 preset colors)
- Tag name autocomplete from existing tags

---

## Feature 2: Server Drift Detection (git status)

### Purpose
Show what changed on a server since the last snapshot — like `git status` shows working tree changes.

### Detection Dimensions
1. **Docker containers**: Compare `docker ps` output vs last snapshot manifest
   - New containers (added since snapshot)
   - Stopped containers (were running, now stopped)
   - Config changes (container restart count, image changes)
2. **Config files**: MD5 comparison of key files
   - `/etc/nginx/nginx.conf`
   - `/etc/docker/daemon.json`
   - `docker-compose.yml` (in common locations)
   - `/etc/systemd/system/*.service`
3. **Listening ports**: Compare `ss -tlnp` output
   - New ports opened
   - Ports closed

### API Endpoint
`GET /api/servers/{id}/drift`

### Response DTO
```java
public record DriftReportDTO(
    Long serverId,
    String serverName,
    Long lastSnapshotId,
    String lastSnapshotTime,
    String lastSnapshotTag,        // nullable, from snapshot_tags
    int totalChanges,
    List<ContainerDrift> containerChanges,
    List<FileDrift> fileChanges,
    List<String> newPorts,
    List<String> closedPorts,
    String summary                 // Human-readable, e.g. "3 containers changed, 1 new port"
) {
    public record ContainerDrift(
        String name,
        String status,             // running/stopped
        String changeType          // ADDED/REMOVED/MODIFIED
    ) {}
    public record FileDrift(
        String path,
        String changeType,         // ADDED/MODIFIED/DELETED
        String details             // e.g. "MD5 changed"
    ) {}
}
```

### Error Handling
- No snapshots for server: return `{"totalChanges": 0, "summary": "No snapshots found. Create a snapshot first to enable drift detection."}`
- SSH connection failure: return 503 with error message
- Partial detection failure: return what was detected, note failures in summary

---

## Feature 3: Snapshot Timeline (git log)

### Purpose
Visual timeline of snapshot history, like `git log --oneline --graph`.

### Component Design
- Vertical timeline with connecting line
- Each node shows: time (relative), title, tags (badges), type (FULL/INCREMENTAL), size, status
- Color coding: STABLE=blue, WARNING=amber, ARCHIVED=gray
- Active node highlighted with glow effect
- Filters: by server (dropdown), by tag (badges)
- Empty state: "Create your first snapshot" with link to server list

---

## Feature 4: Enhanced Diff Viewer (git diff)

### Purpose
Show file-level differences between snapshots in a readable format.

### Backend Improvements
- Parse restic diff output into structured changes
- Group files by parent directory
- Calculate stats: N added, N modified, N deleted

### Response DTO Enhancement
```java
public record SnapshotDiffDTO(
    String path,
    String changeType,      // added/modified/deleted
    String details,
    String directory         // parent directory for grouping
) {}
```

### Frontend Component (DiffViewer.vue)
- GitHub-style diff view
- Files grouped by directory (collapsible)
- Color coding: green=added, red=deleted, yellow=modified
- Footer stats: "+12 files ~5 files -3 files"
