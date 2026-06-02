export interface StorageOverview {
  id: number
  name: string
  type: string
  usedBytes: number
  totalBytes: number
}

export interface StorageDistribution {
  name: string
  bytes: number
  sizeBytes?: number
  percent: number
}

export interface StorageHealthCheck {
  name: string
  desc: string
  status: string
}

export interface CreateStorageRequest {
  type: string
  name: string
  endpoint?: string
  totalBytes?: number
  accessKey?: string
  secretKey?: string
  region?: string
  bucket?: string
}

export type StorageType = 'LOCAL' | 'S3' | 'OSS' | 'WEBDAV' | 'BLOCK' | 'ARCHIVE'
