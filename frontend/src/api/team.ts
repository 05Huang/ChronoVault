import client from './client'
import type { TeamMember, InviteRequest, UpdateMemberRequest } from '@/types'

export const teamApi = {
  getMembers: () => client.get<TeamMember[]>('/team') as unknown as Promise<TeamMember[]>,
  invite: (data: InviteRequest) =>
    client.post('/team/invite', data) as unknown as Promise<void>,
  updateMember: (id: number, data: UpdateMemberRequest) =>
    client.put(`/team/${id}`, data) as unknown as Promise<void>,
}
