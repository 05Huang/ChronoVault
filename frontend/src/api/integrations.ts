import client from './client'
import type { Integration } from '@/types'

export const integrationsApi = {
  getAll: () => client.get<Integration[]>('/integrations') as unknown as Promise<Integration[]>,
  create: (data: { type: string; name: string; url?: string }) =>
    client.post<Integration>('/integrations', data) as unknown as Promise<Integration>,
  update: (id: number, data: { active?: boolean }) =>
    client.put(`/integrations/${id}`, data) as unknown as Promise<void>,
}
