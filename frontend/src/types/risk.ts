export interface RiskTrendPoint {
  date: string
  stability: number
  security: number
}

export interface RiskNode {
  id: number
  name: string
  status: string
}

export interface Risk {
  id: number
  title: string
  description: string
  level: string
  category: string
  status?: string
  discoveredAt?: string
  aiSuggestion?: string
  actionText?: string
}
