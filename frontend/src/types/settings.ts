export interface AuditLog {
  id: number
  action: string
  userId?: number
  userName?: string
  details?: string
  ipAddress?: string
  createdAt: string
}

export interface ApiKey {
  id: number
  name: string
  key?: string
  scope?: string
  createdAt: string
  lastUsedAt?: string
}

export interface GenerateKeyRequest {
  name: string
  scope?: string
}

export interface CreateApiKeyResponse {
  apiKey: ApiKey
  key: string
}

export interface AiConfig {
  enabled: boolean
  baseUrl: string
  apiKey: string
  model: string
  maxTokens: number
  temperature: number
}
