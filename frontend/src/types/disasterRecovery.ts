export interface DisasterRecoveryPlan {
  id: number
  name: string
  description?: string
  steps?: string
  estimatedRto?: number
  estimatedRpo?: number
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  lastExecutedAt?: string
  createdAt?: string
}
