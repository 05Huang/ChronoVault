export interface SimulateRequest {
  serverId: number
  snapshotId: number
}

export interface ExecuteRequest {
  serverId: number
  snapshotId: number
  mode?: string
}

export interface MigrateRequest {
  sourceServerId: number
  targetServerId: number
  snapshotId: number
}

export interface RecoverySimulation {
  success: boolean
  estimatedTime?: number
  affectedFiles?: number
  riskLevel?: string
  details?: string
}

export interface RecoveryResult {
  success: boolean
  message: string
  taskId?: number
}
