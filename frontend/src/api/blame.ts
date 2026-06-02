import client from './client'
import type { ChangeAttribution } from '@/types'

export const blameApi = {
  getServerBlame: (serverId: number) =>
    client.get<ChangeAttribution[]>(`/servers/${serverId}/blame`) as unknown as Promise<ChangeAttribution[]>,
  getSnapshotBlame: (snapshotId: number) =>
    client.get<ChangeAttribution[]>(`/snapshots/${snapshotId}/blame`) as unknown as Promise<ChangeAttribution[]>,
}
