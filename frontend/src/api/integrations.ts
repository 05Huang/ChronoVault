import client from './client'

export const integrationsApi = {
  getAll: () => client.get('/integrations') as unknown as Promise<any>,
  create: (data: { type: string; name: string; url?: string }) =>
    client.post('/integrations', data) as unknown as Promise<any>,
  update: (id: number, data: { active?: boolean }) =>
    client.put(`/integrations/${id}`, data) as unknown as Promise<void>,
}
