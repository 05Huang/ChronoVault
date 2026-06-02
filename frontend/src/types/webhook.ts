export interface WebhookEndpoint {
  id: number
  url: string
  secret?: string
  events?: string
  enabled: boolean
  createdAt?: string
}

export interface WebhookDeliveryLog {
  id: number
  webhookId: number
  eventType: string
  success: boolean
  responseCode?: number
  error?: string
  attempt: number
  createdAt: string
}
