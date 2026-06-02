import client from './client'
import type { Snapshot } from '@/types'

export const stashApi = {
  create: (serverId: number, note?: string) =>
    client.post<Snapshot>(`/servers/${serverId}/stash`, note ? { note } : {}) as unknown as Promise<Snapshot>,
  list: (serverId: number) =>
    client.get<Snapshot[]>(`/servers/${serverId}/stash`) as unknown as Promise<Snapshot[]>,
  pop: (serverId: number) =>
    client.post<string>(`/servers/${serverId}/stash/pop`) as unknown as Promise<string>,
  discard: (serverId: number, stashId: number) =>
    client.delete(`/servers/${serverId}/stash/${stashId}`) as unknown as Promise<void>,
}
