import client from './client'
import type { ServerBranch, CreateBranchRequest, MergeBranchRequest } from '@/types'

export const branchesApi = {
  getAll: (serverId: number) =>
    client.get<ServerBranch[]>(`/servers/${serverId}/branches`) as unknown as Promise<ServerBranch[]>,
  create: (serverId: number, data: CreateBranchRequest) =>
    client.post<ServerBranch>(`/servers/${serverId}/branches`, data) as unknown as Promise<ServerBranch>,
  delete: (serverId: number, branchId: number) =>
    client.delete(`/servers/${serverId}/branches/${branchId}`) as unknown as Promise<void>,
  switch: (serverId: number, branchId: number) =>
    client.post<ServerBranch>(`/servers/${serverId}/branches/${branchId}/switch`) as unknown as Promise<ServerBranch>,
  merge: (serverId: number, data: MergeBranchRequest) =>
    client.post<ServerBranch>(`/servers/${serverId}/branches/merge`, data) as unknown as Promise<ServerBranch>,
  rename: (serverId: number, branchId: number, name: string) =>
    client.put<ServerBranch>(`/servers/${serverId}/branches/${branchId}`, { name }) as unknown as Promise<ServerBranch>,
}