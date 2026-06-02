import client from './client'
import type { Snapshot, SnapshotDiff, CreateSnapshotRequest, SnapshotTag, CreateTagRequest, SelectiveRestoreRequest, BisectSession, BisectStartRequest, BisectMarkRequest, CherryPickRequest, SnapshotFileEntry, SnapshotVerifyResult, ContainerState, DiffSummary } from '@/types'

export const snapshotsApi = {
  getAll: (tagName?: string) => client.get<Snapshot[]>('/snapshots', { params: tagName ? { tagName } : {} }) as unknown as Promise<Snapshot[]>,
  get: (id: number) => client.get<Snapshot>(`/snapshots/${id}`) as unknown as Promise<Snapshot>,
  create: (data: CreateSnapshotRequest) =>
    client.post<Snapshot>('/snapshots', data) as unknown as Promise<Snapshot>,
  getDiff: (id: number) => client.get<SnapshotDiff[]>(`/snapshots/${id}/diff`) as unknown as Promise<SnapshotDiff[]>,
  compare: (fromId: number, toId: number) =>
    client.get<DiffSummary>('/snapshots/compare', { params: { from: fromId, to: toId } }) as unknown as Promise<DiffSummary>,
  rollback: (id: number) => client.post(`/snapshots/${id}/rollback`) as unknown as Promise<void>,
  revert: (id: number) => client.post<string>(`/snapshots/${id}/revert`) as unknown as Promise<string>,
  restoreFiles: (id: number, data: SelectiveRestoreRequest) =>
    client.post<string>(`/snapshots/${id}/restore-files`, data) as unknown as Promise<string>,
  cherryPick: (id: number, data: CherryPickRequest) =>
    client.post<string>(`/snapshots/${id}/cherry-pick`, data) as unknown as Promise<string>,
  listFiles: (id: number, path?: string) =>
    client.get<SnapshotFileEntry[]>(`/snapshots/${id}/files`, { params: { path } }) as unknown as Promise<SnapshotFileEntry[]>,
  downloadFile: (id: number, path: string) =>
    client.get<string>(`/snapshots/${id}/files/download`, { params: { path } }) as unknown as Promise<string>,
  verify: (id: number) =>
    client.post<SnapshotVerifyResult>(`/snapshots/${id}/verify`) as unknown as Promise<SnapshotVerifyResult>,
  getContainers: (id: number) =>
    client.get<ContainerState[]>(`/snapshots/${id}/containers`) as unknown as Promise<ContainerState[]>,
  batchDelete: (ids: number[]) =>
    client.post('/snapshots/batch-delete', ids) as unknown as Promise<number>,
  exportSnapshots: (format: 'csv' | 'json' = 'json') =>
    client.get('/snapshots/export', { params: { format }, responseType: 'blob' }),

  // Tag endpoints
  getTags: (snapshotId: number) =>
    client.get<SnapshotTag[]>(`/snapshots/${snapshotId}/tags`) as unknown as Promise<SnapshotTag[]>,
  addTag: (snapshotId: number, data: CreateTagRequest) =>
    client.post<SnapshotTag>(`/snapshots/${snapshotId}/tags`, data) as unknown as Promise<SnapshotTag>,
  removeTag: (snapshotId: number, tagName: string) =>
    client.delete(`/snapshots/${snapshotId}/tags/${encodeURIComponent(tagName)}`) as unknown as Promise<void>,
  getAllTags: () =>
    client.get<SnapshotTag[]>('/snapshots/tags/all') as unknown as Promise<SnapshotTag[]>,
  batchTag: (data: { snapshotIds: number[]; tagName: string; color?: string }) =>
    client.post<string>('/snapshots/batch-tag', data) as unknown as Promise<string>,

  // Bisect endpoints
  bisectStart: (data: BisectStartRequest) =>
    client.post<BisectSession>('/snapshots/bisect/start', data) as unknown as Promise<BisectSession>,
  bisectMark: (sessionId: string, data: BisectMarkRequest) =>
    client.post<BisectSession>(`/snapshots/bisect/${sessionId}/mark`, data) as unknown as Promise<BisectSession>,
  bisectGet: (sessionId: string) =>
    client.get<BisectSession>(`/snapshots/bisect/${sessionId}`) as unknown as Promise<BisectSession>,

  // Batch snapshot
  batch: (data: { serverIds: number[]; storageTargetId?: number; name?: string }) =>
    client.post<string>('/snapshots/batch', data) as unknown as Promise<string>,
  batchStatus: (batchId: string) =>
    client.get<any>(`/snapshots/batch/${batchId}`) as unknown as Promise<any>,
}
