import client from './client'

export const dashboardApi = {
  getStats: () => client.get('/dashboard/stats'),
  getAnomalies: () => client.get('/dashboard/anomalies'),
  getStorageSummary: () => client.get('/dashboard/storage-summary'),
  getRiskScore: () => client.get('/dashboard/risk-score'),
  getTopology: () => client.get('/dashboard/topology'),
  getActivityTrend: () => client.get('/dashboard/activity-trend'),
}
