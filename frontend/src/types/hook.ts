export interface SnapshotHook {
  id: number
  name: string
  serverId: number
  hookType: 'PRE_SNAPSHOT' | 'POST_SNAPSHOT' | 'PRE_RESTORE' | 'POST_RESTORE'
  command: string
  timeoutSeconds: number
  enabled: boolean
  orderIndex: number
  createdAt?: string
}
