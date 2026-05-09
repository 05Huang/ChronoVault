import client from './client'

export const snapshotsApi = {
  getAll: () => client.get('/snapshots'),
  get: (id: number) => client.get(`/snapshots/${id}`),
  create: (data: { serverId: number; type?: string; note?: string }) =>
    client.post('/snapshots', data),
  getDiff: (id: number) => client.get(`/snapshots/${id}/diff`),
  rollback: (id: number) => client.post(`/snapshots/${id}/rollback`),
}
