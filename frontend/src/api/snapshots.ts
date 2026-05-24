import client from './client'
import type { Snapshot, SnapshotDiff, CreateSnapshotRequest, SnapshotTag, CreateTagRequest } from '@/types'

export const snapshotsApi = {
  getAll: () => client.get<Snapshot[]>('/snapshots') as unknown as Promise<Snapshot[]>,
  get: (id: number) => client.get<Snapshot>(`/snapshots/${id}`) as unknown as Promise<Snapshot>,
  create: (data: CreateSnapshotRequest) =>
    client.post<Snapshot>('/snapshots', data) as unknown as Promise<Snapshot>,
  getDiff: (id: number) => client.get<SnapshotDiff[]>(`/snapshots/${id}/diff`) as unknown as Promise<SnapshotDiff[]>,
  rollback: (id: number) => client.post(`/snapshots/${id}/rollback`) as unknown as Promise<void>,
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
}
