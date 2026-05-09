import client from './client'

export const alertsApi = {
  getAll: (filter?: string) => client.get('/alerts', { params: { filter } }),
  getStats: () => client.get('/alerts/stats'),
  restart: (id: number) => client.post(`/alerts/${id}/restart`),
  expandStorage: (id: number) => client.post(`/alerts/${id}/expand-storage`),
  rollbackConfig: (id: number) => client.post(`/alerts/${id}/rollback-config`),
  dismiss: (id: number) => client.post(`/alerts/${id}/dismiss`),
  getRules: () => client.get('/alerts/rules'),
  createRule: (data: { name: string; metric: string; threshold: number; durationMinutes: number; severity: string }) =>
    client.post('/alerts/rules', data),
}
