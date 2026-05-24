import client from './client'
import type { SimulateRequest, ExecuteRequest, MigrateRequest, RecoveryResult } from '@/types'

export const recoveryApi = {
  simulate: (data: SimulateRequest) =>
    client.post<RecoveryResult>('/recovery/simulate', data) as unknown as Promise<RecoveryResult>,
  execute: (data: ExecuteRequest) =>
    client.post<RecoveryResult>('/recovery/execute', data) as unknown as Promise<RecoveryResult>,
  migrate: (data: MigrateRequest) =>
    client.post<RecoveryResult>('/recovery/migrate', data) as unknown as Promise<RecoveryResult>,
}
