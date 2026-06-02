export interface ServerBranch {
  id: number
  name: string
  description?: string
  serverId: number
  createdFromSnapshotId?: number
  isDefault: boolean
  createdAt: string
}

export interface CreateBranchRequest {
  name: string
  description?: string
  fromSnapshotId?: number
}

export interface MergeBranchRequest {
  sourceBranchId: number
  targetBranchId: number
}
