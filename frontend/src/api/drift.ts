import client from './client'
import type { DriftReport } from '@/types'

export const driftApi = {
  detect: (serverId: number) =>
    client.get<DriftReport>(`/servers/${serverId}/drift`) as unknown as Promise<DriftReport>,
}
