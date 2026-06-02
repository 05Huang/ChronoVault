import client from './client'
import type { SnapshotHook } from '@/types'

export const hooksApi = {
  getAll: (serverId: number) =>
    client.get<SnapshotHook[]>(`/servers/${serverId}/hooks`) as unknown as Promise<SnapshotHook[]>,
  create: (serverId: number, data: Partial<SnapshotHook>) =>
    client.post<SnapshotHook>(`/servers/${serverId}/hooks`, data) as unknown as Promise<SnapshotHook>,
  update: (serverId: number, hookId: number, data: Partial<SnapshotHook>) =>
    client.put<SnapshotHook>(`/servers/${serverId}/hooks/${hookId}`, data) as unknown as Promise<SnapshotHook>,
  delete: (serverId: number, hookId: number) =>
    client.delete(`/servers/${serverId}/hooks/${hookId}`) as unknown as Promise<void>,
}
