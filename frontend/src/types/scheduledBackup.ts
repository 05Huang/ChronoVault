export interface ScheduledBackup {
  id: number
  name: string
  serverId: number
  serverName: string
  storageTargetId?: number
  cronExpression: string
  enabled: boolean
  paths?: string
  excludes?: string
  lastRunAt?: string
  nextRunAt?: string
  lastStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING'
  lastError?: string
  runCount: number
  createdAt: string
}
