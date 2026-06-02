export interface ChangeAttribution {
  id: number
  userId?: number
  userName: string
  action: string
  changeType?: string
  serverId?: number
  serverName?: string
  snapshotId?: number
  snapshotName?: string
  resourceId?: number
  details?: string
  timestamp: string
}