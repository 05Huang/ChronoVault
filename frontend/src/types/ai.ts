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
