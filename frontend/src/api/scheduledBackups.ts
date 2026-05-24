import client from './client'

export const scheduledBackupsApi = {
  getAll: () => client.get('/scheduled-backups') as unknown as Promise<any>,
  getById: (id: number) => client.get(`/scheduled-backups/${id}`) as unknown as Promise<any>,
  create: (data: CreateScheduledBackupRequest) => client.post('/scheduled-backups', data) as unknown as Promise<any>,
  toggle: (id: number) => client.put(`/scheduled-backups/${id}/toggle`) as unknown as Promise<any>,
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
