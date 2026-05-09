import client from './client'

export const storageApi = {
  getOverview: () => client.get('/storage/overview'),
  getDistribution: () => client.get('/storage/distribution'),
  getHealth: () => client.get('/storage/health'),
  addTarget: (data: { type: string; name: string; endpoint: string; totalBytes: number }) =>
    client.post('/storage', data),
}
