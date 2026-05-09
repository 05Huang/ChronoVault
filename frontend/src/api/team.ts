import client from './client'

export const teamApi = {
  getMembers: () => client.get('/team'),
  invite: (data: { name: string; email: string; role?: string }) =>
    client.post('/team/invite', data),
  updateMember: (id: number, data: { role?: string; permissions?: string }) =>
    client.put(`/team/${id}`, data),
}
