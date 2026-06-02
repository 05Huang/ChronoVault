import client from './client'
import type { WebhookEndpoint, WebhookDeliveryLog } from '@/types'

export const webhooksApi = {
  getAll: () =>
    client.get<WebhookEndpoint[]>('/webhooks') as unknown as Promise<WebhookEndpoint[]>,
  create: (data: Partial<WebhookEndpoint>) =>
    client.post<WebhookEndpoint>('/webhooks', data) as unknown as Promise<WebhookEndpoint>,
  update: (id: number, data: Partial<WebhookEndpoint>) =>
    client.put<WebhookEndpoint>(`/webhooks/${id}`, data) as unknown as Promise<WebhookEndpoint>,
  delete: (id: number) =>
    client.delete(`/webhooks/${id}`) as unknown as Promise<void>,
  getLogs: (id: number) =>
    client.get<WebhookDeliveryLog[]>(`/webhooks/${id}/logs`) as unknown as Promise<WebhookDeliveryLog[]>,
  test: (id: number) =>
    client.post<string>(`/webhooks/${id}/test`) as unknown as Promise<string>,
}
