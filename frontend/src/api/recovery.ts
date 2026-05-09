import client from './client'

export const recoveryApi = {
  simulate: (data: { serverId: number; snapshotId: number }) =>
    client.post('/recovery/simulate', data),
  execute: (data: { serverId: number; snapshotId: number }) =>
    client.post('/recovery/execute', data),
  migrate: (data: { sourceServerId: number; targetServerId: number; snapshotId: number }) =>
    client.post('/recovery/migrate', data),
}
