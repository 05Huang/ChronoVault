import client from './client'

export const riskApi = {
  getScore: () => client.get('/risk/score') as unknown as Promise<any>,
  getTrend: () => client.get('/risk/trend') as unknown as Promise<any>,
  getNodes: () => client.get('/risk/nodes') as unknown as Promise<any>,
  getRisks: () => client.get('/risk/list') as unknown as Promise<any>,
  mitigate: (id: number) => client.post(`/risk/${id}/mitigate`) as unknown as Promise<void>,
  scan: () => client.post('/risk/scan') as unknown as Promise<void>,
}
