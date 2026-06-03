import client from './client'
import type { ScheduledBackup } from '@/types'

export const scheduledBackupsApi = {
  getAll: () => client.get<ScheduledBackup[]>('/scheduled-backups') as unknown as Promise<ScheduledBackup[]>,
  getById: (id: number) => client.get<ScheduledBackup>(`/scheduled-backups/${id}`) as unknown as Promise<ScheduledBackup>,
  create: (data: CreateScheduledBackupRequest) => client.post<ScheduledBackup>('/scheduled-backups', data) as unknown as Promise<ScheduledBackup>,
  toggle: (id: number) => client.put<ScheduledBackup>(`/scheduled-backups/${id}/toggle`) as unknown as Promise<ScheduledBackup>,
  delete: (id: number) => client.delete(`/scheduled-backups/${id}`) as unknown as Promise<void>,
}

interface CreateScheduledBackupRequest {
  serverId: number
  storageTargetId?: number
  name: string
  cronExpression: string
  paths?: string
  excludes?: string
}
