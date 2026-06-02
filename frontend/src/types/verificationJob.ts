export interface VerificationJob {
  id: number
  serverId: number
  serverName?: string
  storageTargetId?: number
  scheduleCron: string
  lastStatus: string
  lastRunAt?: string
  lastError?: string
  enabled: boolean
  createdAt?: string
}
