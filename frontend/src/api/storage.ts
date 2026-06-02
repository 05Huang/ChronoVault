import client from './client'
import type { StorageOverview, StorageDistribution, StorageHealthCheck, CreateStorageRequest } from '@/types'

export const storageApi = {
  getOverview: () => client.get<StorageOverview[]>('/storage/overview') as unknown as Promise<StorageOverview[]>,
  getDistribution: () => client.get<StorageDistribution[]>('/storage/distribution') as unknown as Promise<StorageDistribution[]>,
  getHealth: () => client.get<StorageHealthCheck[]>('/storage/health') as unknown as Promise<StorageHealthCheck[]>,
  addTarget: (data: CreateStorageRequest) => client.post('/storage', data) as unknown as Promise<void>,
  deleteTarget: (id: number) => client.delete(`/storage/${id}`) as unknown as Promise<void>,
  replicateSnapshot: (snapshotId: number, targetStorageId: number) =>
    client.post<string>(`/snapshots/${snapshotId}/replicate`, { targetStorageId }) as unknown as Promise<string>,
}
