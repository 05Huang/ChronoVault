export interface DashboardStats {
  activeServers: number
  totalContainers: number
  todayBackups: number
  recoveryRate: number
  totalServers?: number
  totalSnapshots?: number
  totalAlerts?: number
  usedBytes?: number
  totalBytes?: number
  teamMembers?: number
}

export interface Anomaly {
  title: string
  severity: string
  time: string
  description?: string
}

export interface StorageSummary {
  usedBytes: number
  totalBytes: number
  type: string
}

export interface ActivityTrend {
  label: string
  snapshots: number
  alerts?: number
  other?: number
}

export interface TopologyNode {
  id: number
  name: string
  ip: string
  status: string
}
