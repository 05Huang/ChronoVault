import client from './client'

export const aiApi = {
  getInsights: () => client.get('/ai/insights'),
  getRecommendations: () => client.get('/ai/recommendations'),
  applyRecommendation: (id: number) => client.post(`/ai/recommendations/${id}/apply`),
  getRiskRadar: () => client.get('/ai/risk-radar'),
  getStoragePrediction: () => client.get('/ai/storage-prediction'),
  generateReport: () => client.post('/ai/generate-report'),
}
