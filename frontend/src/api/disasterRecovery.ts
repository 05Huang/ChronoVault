import client from './client'
import type { DisasterRecoveryPlan } from '@/types'

export const drApi = {
  getAll: () =>
    client.get<DisasterRecoveryPlan[]>('/disaster-recovery') as unknown as Promise<DisasterRecoveryPlan[]>,
  get: (id: number) =>
    client.get<DisasterRecoveryPlan>(`/disaster-recovery/${id}`) as unknown as Promise<DisasterRecoveryPlan>,
  create: (data: Partial<DisasterRecoveryPlan>) =>
    client.post<DisasterRecoveryPlan>('/disaster-recovery', data) as unknown as Promise<DisasterRecoveryPlan>,
  update: (id: number, data: Partial<DisasterRecoveryPlan>) =>
    client.put<DisasterRecoveryPlan>(`/disaster-recovery/${id}`, data) as unknown as Promise<DisasterRecoveryPlan>,
  delete: (id: number) =>
    client.delete(`/disaster-recovery/${id}`) as unknown as Promise<void>,
  execute: (id: number) =>
    client.post<DisasterRecoveryPlan>(`/disaster-recovery/${id}/execute`) as unknown as Promise<DisasterRecoveryPlan>,
}
