/** Generic API response wrapper from backend */
export interface ApiResponse<T> {
  data: T
  message?: string
  timestamp?: string
}

/** Paginated response */
export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

/** Async task from backend */
export interface AsyncTask {
  id: number
  type: 'SNAPSHOT' | 'RECOVER' | 'SCAN'
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progress: number
  message: string
  result?: string
  error?: string
  createdAt: string
  updatedAt: string
}

/** WebSocket task event */
export interface TaskEvent {
  taskId: number
  status: string
  progress: number
  message: string
}

/** Severity level */
export type Severity = 'CRITICAL' | 'WARNING' | 'INFO' | 'PREDICTIVE'

/** Health status */
export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'CRITICAL'

/** Connection test result */
export interface ConnectionTestResult {
  success: boolean
  message: string
  sessionId?: string
  latency?: number
  osInfo?: string
}

/** Integration (Slack, Email, Webhook, DingTalk) */
export interface Integration {
  id: number
  type: 'SLACK' | 'EMAIL' | 'WEBHOOK' | 'DINGTALK'
  name: string
  url?: string
  active: boolean
  createdAt: string
}
