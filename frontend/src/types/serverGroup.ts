export interface ServerGroup {
  id: number
  name: string
  description?: string
  environmentType: 'PRODUCTION' | 'STAGING' | 'DEVELOPMENT' | 'TESTING'
  color: string
  userId: number
  createdAt?: string
}
