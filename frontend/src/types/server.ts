export interface Server {
  id: number
  name: string
  ip: string
  os: string
  status: 'RUNNING' | 'STOPPED' | 'ERROR'
  uptimeSeconds: number
  sshPort?: number
  sshUsername?: string
  sshAuthMethod?: 'password' | 'key'
  autoSnapshotEnabled?: boolean
  groupId?: number
  createdAt?: string
  updatedAt?: string
}

export interface CreateServerRequest {
  name: string
  ip: string
  os?: string
}

export interface CloneServerRequest {
  sourceServerId: number
  targetServerIp: string
  targetName?: string
  targetSshPort?: number
  targetSshUsername?: string
}

export interface UpdateSshConfigRequest {
  port?: number
  username?: string
  authMethod?: string
  credential?: string
}

export interface Container {
  id?: string
  name: string
  type: string
  status: string
  cpuUsage: string
  memoryUsage: string
  memoryPercent: string
  networks?: string
  image?: string
  ports?: string
}

export interface Volume {
  name: string
  type: string
  path: string
  sizeBytes?: number
  size?: string
  status?: string
}

export interface AddVolumeRequest {
  name: string
  containerPath: string
  hostPath: string
}

export interface LogEntry {
  message?: string
  text?: string
  level: 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'
  timestamp?: string
}

export interface ServerHealth {
  status: string
  uptimeSeconds: number
  os: string
}

/** Topology edge: [source, target, label?] */
export type TopologyEdge = [string, string, string?]

export interface EnvironmentScanResult {
  success: boolean
  message?: string
  data?: {
    os: string
    disk: string
    memory: string
    uptime: string
    dockerInstalled: boolean
    containers: { name: string; image: string; status: string; cpu: string; memory: string }[]
    databases: { type: string; port: string }[]
  }
}

export interface AiAnalyzeResult {
  analysis: string
}
