export interface ContainerDrift {
  containerName: string
  status: string
  driftType: string
  details: string
}

export interface FileDrift {
  filePath: string
  driftType: string
  currentHash: string
  baselineHash: string | null
  details: string
}

export interface PortDrift {
  port: number
  protocol: string
  driftType: string
  details: string
}

export interface DriftReport {
  serverId: number
  serverName: string
  totalChanges: number
  containerDrifts: ContainerDrift[]
  fileDrifts: FileDrift[]
  portDrifts: PortDrift[]
  status: string
  scannedAt: string
}
