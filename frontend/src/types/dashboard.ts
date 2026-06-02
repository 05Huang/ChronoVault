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
  id?: number
  name?: string
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

export interface TopologyEdge {
  source: string
  target: string
}

export interface Topology {
  nodes: TopologyNode[]
  edges: TopologyEdge[]
}

export interface RiskScore {
  overallScore: number
  level: string
  summary: string
  criticalCount: number
  warningCount: number
  anomalyCount: number
}

// P2-4 Dashboard Overview types
export interface ServerSnapshotStatus {
  serverId: number
  serverName: string
  lastSnapshotTime: string | null
  timeSinceLastSnapshot: string
  isStale: boolean
  lastChangeSummary: string | null
}

export interface RecentChangeSummary {
  snapshotId: number
  serverName: string
  createdAt: string
  packagesAdded: number
  packagesRemoved: number
  packagesUpgraded: number
  servicesChanged: number
  configsChanged: number
}

export interface PendingAlertsInfo {
  totalPending: number
  highRisk: number
  warnings: number
}

export interface RecentRollbackInfo {
  lastRollbackTime: string | null
  lastRollbackUser: string | null
  lastRollbackSnapshot: string | null
}

export interface DashboardOverview {
  serverStatuses: ServerSnapshotStatus[]
  recentChanges: RecentChangeSummary[]
  pendingAlerts: PendingAlertsInfo
  recentRollback: RecentRollbackInfo
}
