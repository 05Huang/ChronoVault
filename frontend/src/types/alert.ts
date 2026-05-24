import type { Severity } from './common'

export interface Alert {
  id: number
  title: string
  description?: string
  message?: string
  severity: Severity
  status: string
  source?: string
  aiAnalysis?: string
  time?: string
  createdAt: string
  updatedAt?: string
}

export interface AlertRule {
  id: number
  name: string
  metric: string
  threshold: number
  durationMinutes: number
  severity: string
  enabled?: boolean
}

export interface CreateAlertRuleRequest {
  name: string
  metric: string
  threshold: number
  durationMinutes: number
  severity: string
}

export interface AlertStats {
  total: number
  critical: number
  warning: number
  info: number
  predictive?: number
  resolvedCount?: number
}
