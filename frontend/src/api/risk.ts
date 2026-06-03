import client from './client'
import type { RiskScore, RiskTrendPoint, RiskNode, Risk } from '@/types'

export const riskApi = {
  getScore: () => client.get<RiskScore>('/risk/score') as unknown as Promise<RiskScore>,
  getTrend: () => client.get<RiskTrendPoint[]>('/risk/trend') as unknown as Promise<RiskTrendPoint[]>,
  getNodes: () => client.get<RiskNode[]>('/risk/nodes') as unknown as Promise<RiskNode[]>,
  getRisks: () => client.get<Risk[]>('/risk/list') as unknown as Promise<Risk[]>,
  mitigate: (id: number) => client.post(`/risk/${id}/mitigate`) as unknown as Promise<void>,
  scan: () => client.post('/risk/scan') as unknown as Promise<void>,
}
