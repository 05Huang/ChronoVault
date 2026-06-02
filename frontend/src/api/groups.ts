import client from './client'
import type { ServerGroup } from '@/types'

export const groupsApi = {
  getAll: () =>
    client.get<ServerGroup[]>('/server-groups') as unknown as Promise<ServerGroup[]>,
  create: (data: Partial<ServerGroup>) =>
    client.post<ServerGroup>('/server-groups', data) as unknown as Promise<ServerGroup>,
  update: (id: number, data: Partial<ServerGroup>) =>
    client.put<ServerGroup>(`/server-groups/${id}`, data) as unknown as Promise<ServerGroup>,
  delete: (id: number) =>
    client.delete(`/server-groups/${id}`) as unknown as Promise<void>,
  addServer: (groupId: number, serverId: number) =>
    client.post(`/server-groups/${groupId}/servers/${serverId}`) as unknown as Promise<void>,
  removeServer: (serverId: number) =>
    client.delete(`/server-groups/servers/${serverId}`) as unknown as Promise<void>,
}
