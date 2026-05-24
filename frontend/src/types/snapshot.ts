export interface Snapshot {
  id: number
  name?: string
  title?: string
  description?: string
  note?: string
  status: 'STABLE' | 'WARNING' | 'ARCHIVED'
  type?: 'FULL' | 'INCREMENTAL'
  createdAt: string
  date?: string
  hash?: string
  sizeBytes: number
  serverName?: string
  serverId?: number
  storageTargetId?: number
  tags?: SnapshotTag[]
}

export interface CreateSnapshotRequest {
  serverId: number
  storageTargetId?: number
  type?: string
  note?: string
}

export interface SnapshotDiff {
  path: string
  prev: string
  next: string
}

export interface SnapshotManifest {
  id: number
  snapshotId: number
  files: ManifestFile[]
}

export interface ManifestFile {
  path: string
  type: 'CONFIG' | 'DATABASE' | 'ENV' | 'COMPOSE' | 'FILE'
  size?: number
}

export interface SnapshotTag {
  id: number
  snapshotId: number
  name: string
  color: string
  createdAt: string
}

export interface CreateTagRequest {
  name: string
  color?: string
}
