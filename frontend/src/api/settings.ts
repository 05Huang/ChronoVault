import client from './client'

export const settingsApi = {
  getAuditLogs: () => client.get('/settings/audit-logs'),
  getApiKeys: () => client.get('/settings/api-keys'),
  generateKey: (data: { name: string; scope?: string }) =>
    client.post('/settings/api-keys', data),
  deleteKey: (id: number) => client.delete(`/settings/api-keys/${id}`),
}
