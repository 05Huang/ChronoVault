import client from './client'
import type { AuditLog, ApiKey, GenerateKeyRequest, CreateApiKeyResponse, AiConfig } from '@/types'

export const settingsApi = {
  getAuditLogs: () => client.get<AuditLog[]>('/settings/audit-logs') as unknown as Promise<AuditLog[]>,
  searchAuditLogs: (params: { action?: string; userId?: number; since?: string; until?: string; page?: number; size?: number }) =>
    client.get('/settings/audit-logs/search', { params }) as unknown as Promise<any>,
  getApiKeys: () => client.get<ApiKey[]>('/settings/api-keys') as unknown as Promise<ApiKey[]>,
  generateKey: (data: GenerateKeyRequest) =>
    client.post<CreateApiKeyResponse>('/settings/api-keys', data) as unknown as Promise<CreateApiKeyResponse>,
  deleteKey: (id: number) => client.delete(`/settings/api-keys/${id}`) as unknown as Promise<void>,
  getAiConfig: () => client.get<AiConfig>('/settings/ai-config') as unknown as Promise<AiConfig>,
  updateAiConfig: (data: Partial<AiConfig>) =>
    client.put('/settings/ai-config', data) as unknown as Promise<void>,
}
