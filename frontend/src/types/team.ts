export interface TeamMember {
  id: number
  name: string
  email: string
  role: string
  status?: string
  joinedAt?: string
  lastActiveAt?: string
  permissions?: string
}

export interface InviteRequest {
  name: string
  email: string
  role?: string
}

export interface UpdateMemberRequest {
  role?: string
  permissions?: string
}
