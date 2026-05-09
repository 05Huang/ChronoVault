import client from './client'

export const riskApi = {
  getScore: () => client.get('/risk/score'),
  getTrend: () => client.get('/risk/trend'),
  getNodes: () => client.get('/risk/nodes'),
  getRisks: () => client.get('/risk/list'),
  mitigate: (id: number) => client.post(`/risk/${id}/mitigate`),
  scan: () => client.post('/risk/scan'),
}
