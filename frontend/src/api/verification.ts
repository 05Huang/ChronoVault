import client from './client'
import type { VerificationJob } from '@/types'

export const verificationApi = {
  getAll: () =>
    client.get<VerificationJob[]>('/verification-jobs') as unknown as Promise<VerificationJob[]>,
  create: (data: Partial<VerificationJob>) =>
    client.post<VerificationJob>('/verification-jobs', data) as unknown as Promise<VerificationJob>,
  update: (id: number, data: Partial<VerificationJob>) =>
    client.put<VerificationJob>(`/verification-jobs/${id}`, data) as unknown as Promise<VerificationJob>,
  delete: (id: number) =>
    client.delete(`/verification-jobs/${id}`) as unknown as Promise<void>,
  run: (id: number) =>
    client.post<VerificationJob>(`/verification-jobs/${id}/run`) as unknown as Promise<VerificationJob>,
}
