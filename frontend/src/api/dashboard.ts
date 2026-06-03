import client from './client'
import type { DashboardStats, Anomaly, RiskScore, StorageSummary, ActivityTrend, Topology, DashboardOverview } from '@/types'

export const dashboardApi = {
  getStats: () => client.get<DashboardStats>('/dashboard/stats') as unknown as Promise<DashboardStats>,
  getAnomalies: () => client.get<Anomaly[]>('/dashboard/anomalies') as unknown as Promise<Anomaly[]>,
  getStorageSummary: () => client.get<StorageSummary[]>('/dashboard/storage-summary') as unknown as Promise<StorageSummary[]>,
  getRiskScore: () => client.get<RiskScore>('/dashboard/risk-score') as unknown as Promise<RiskScore>,
  getTopology: () => client.get<Topology>('/dashboard/topology') as unknown as Promise<Topology>,
  getActivityTrend: (range?: string) => client.get<ActivityTrend[]>('/dashboard/activity-trend', { params: { range: range || '7d' } }) as unknown as Promise<ActivityTrend[]>,
  getOverview: () => client.get<DashboardOverview>('/dashboard/overview') as unknown as Promise<DashboardOverview>,
}
