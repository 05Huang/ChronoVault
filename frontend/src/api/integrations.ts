import client from './client'

export const integrationsApi = {
  getAll: () => client.get('/integrations'),
  create: (data: { type: string; name: string; url?: string }) =>
    client.post('/integrations', data),
  update: (id: number, data: { active?: boolean }) =>
    client.put(`/integrations/${id}`, data),
}
