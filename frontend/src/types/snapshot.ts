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
  paths?: string[]
  excludes?: string[]
}

export interface SnapshotDiff {
  path: string
  prev: string
  next: string
  changeType?: 'added' | 'modified' | 'deleted'
}

export interface DiffSummary {
  addedCount: number
  modifiedCount: number
  deletedCount: number
  diffs: SnapshotDiff[]
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

export interface SelectiveRestoreRequest {
  paths: string[]
  targetPath?: string
  overwrite?: boolean
}

export interface BisectSession {
  sessionId: string
  serverId: number
  goodSnapshotId: number
  badSnapshotId: number
  currentSnapshotId: number
  currentSnapshotName: string
  stepsRemaining: number
  totalSteps: number
  status: 'IN_PROGRESS' | 'FOUND'
  culpritSnapshotName: string | null
  candidateSnapshots: Snapshot[]
}

export interface BisectStartRequest {
  serverId: number
  goodSnapshotId: number
  badSnapshotId: number
}

export interface BisectMarkRequest {
  snapshotId: number
  verdict: 'good' | 'bad'
}

export interface CherryPickRequest {
  files: string[]
  targetServerId: number
}

export interface SnapshotFileEntry {
  path: string
  name: string
  size: number
  type: 'FILE' | 'DIRECTORY'
  modifiedAt: string
}

export interface SnapshotVerifyResult {
  snapshotId: number
  verified: boolean
  errors: string | null
  durationMs: number
}

export interface ContainerState {
  id: number
  containerName: string
  image: string
  status: string
  ports: string
  volumes: string
  networks: string
}
