export interface AiInsight {
  id?: number
  title: string
  description: string
  category: string
  icon?: string
  severity?: string
  createdAt?: string
}

export interface AiRecommendation {
  id: number
  title: string
  desc?: string
  description?: string
  category: string
  icon?: string
  impact?: string
  action?: string
}

export interface RiskRadar {
  indicators: Array<{ name: string; max: number }>
  values: number[]
}

export interface StoragePrediction {
  months: string[]
  actual: number[]
  predicted: number[]
}

export interface AiServerAnalysis {
  healthScore: number
  summary: string
  findings: string[]
  recommendations: string[]
}

/** Anomaly detection result from AI engine */
export interface AnomalyDetection {
  serverId: number
  serverName: string
  anomalies: Array<{
    type: string       // PORT/SERVICE/PACKAGE/CONFIG/DOCKER/CRONTAB/OS
    severity: string   // CRITICAL/WARNING/INFO
    title: string
    detail: string
    serverId: number
    metadata: Record<string, unknown>
  }>
  summary: string
  detectedAt: string
}

/** Backup strategy recommendation from AI engine */
export interface BackupRecommendation {
  frequency: {
    suggestedFrequency: string
    reason: string
    cronExpression: string
    priority: string
  }
  retention: {
    suggestedRetainDays: number
    reason: string
    freeSpaceTarget: string
    priority: string
  }
  paths: {
    priorityPaths: string[]
    excludePaths: string[]
    perServerSuggestions: Array<{
      serverId: number
      suggestedPaths: string[]
      reason: string
    }>
  }
  servers: Array<{
    serverId: number
    serverName: string
    snapshotCount: number
    withStateJson: number
    status: string
    suggestion: string
  }>
  aiSummary: string | null
}
