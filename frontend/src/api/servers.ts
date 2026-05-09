import client from './client'

export const serversApi = {
  getAll: () => client.get('/servers'),
  get: (id: number) => client.get(`/servers/${id}`),
  create: (data: { name: string; ip: string; os?: string }) =>
    client.post('/servers', data),
  getContainers: (id: number) => client.get(`/servers/${id}/containers`),
  getVolumes: (id: number) => client.get(`/servers/${id}/volumes`),
  addVolume: (id: number, data: { name: string; containerPath: string; hostPath: string }) =>
    client.post(`/servers/${id}/volumes`, data),
  getLogs: (id: number) => client.get(`/servers/${id}/logs`),
  clearLogs: (id: number) => client.delete(`/servers/${id}/logs`),
  connect: (id: number) => client.post(`/servers/${id}/connect`),
  updateSshConfig: (id: number, data: { port?: number; username?: string; authMethod?: string; credential?: string }) =>
    client.put(`/servers/${id}/ssh`, data),
  testConnection: (id: number) => client.post(`/servers/${id}/test-connection`),
}
