import client from './client'
import type { Alert, AlertRule, AlertStats, CreateAlertRuleRequest } from '@/types'

export const alertsApi = {
  getAll: (filter?: string) => client.get<Alert[]>('/alerts', { params: { filter } }) as unknown as Promise<Alert[]>,
  getStats: () => client.get<AlertStats>('/alerts/stats') as unknown as Promise<AlertStats>,
  restart: (id: number) => client.post(`/alerts/${id}/restart`) as unknown as Promise<void>,
  expandStorage: (id: number) => client.post(`/alerts/${id}/expand-storage`) as unknown as Promise<void>,
  rollbackConfig: (id: number) => client.post(`/alerts/${id}/rollback-config`) as unknown as Promise<void>,
  dismiss: (id: number) => client.post(`/alerts/${id}/dismiss`) as unknown as Promise<void>,
  getRules: () => client.get<AlertRule[]>('/alerts/rules') as unknown as Promise<AlertRule[]>,
  createRule: (data: CreateAlertRuleRequest) =>
    client.post<AlertRule>('/alerts/rules', data) as unknown as Promise<AlertRule>,
}
