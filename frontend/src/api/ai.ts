import client from './client'
import type { AiInsight, AiRecommendation, RiskRadar, StoragePrediction, AiServerAnalysis, BackupRecommendation, AnomalyDetection } from '@/types'

export const aiApi = {
  getInsights: () => client.get<AiInsight[]>('/ai/insights') as unknown as Promise<AiInsight[]>,
  getRecommendations: () => client.get<AiRecommendation[]>('/ai/recommendations') as unknown as Promise<AiRecommendation[]>,
  applyRecommendation: (id: number) => client.post(`/ai/recommendations/${id}/apply`) as unknown as Promise<void>,
  getRiskRadar: () => client.get<RiskRadar>('/ai/risk-radar') as unknown as Promise<RiskRadar>,
  getStoragePrediction: () => client.get<StoragePrediction>('/ai/storage-prediction') as unknown as Promise<StoragePrediction>,
  generateReport: () => client.post<string>('/ai/generate-report') as unknown as Promise<string>,
  analyzeServer: (serverId: number) => client.get<AiServerAnalysis>(`/ai/server-analysis/${serverId}`) as unknown as Promise<AiServerAnalysis>,
  getBackupRecommendations: () => client.get<BackupRecommendation>('/ai/backup-recommendations') as unknown as Promise<BackupRecommendation>,
  detectAnomalies: (serverId: number) => client.get<AnomalyDetection>(`/ai/anomalies/${serverId}`) as unknown as Promise<AnomalyDetection>,
  detectAllAnomalies: () => client.get<AnomalyDetection[]>('/ai/anomalies') as unknown as Promise<AnomalyDetection[]>,
}
