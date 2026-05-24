import client from './client'
import type { User, AuthResponse, LoginRequest, RegisterRequest } from '@/types'

export const authApi = {
  login: (email: string, password: string) =>
    client.post<AuthResponse>('/auth/login', { email, password } as LoginRequest) as unknown as Promise<AuthResponse>,

  register: (name: string, email: string, password: string) =>
    client.post<AuthResponse>('/auth/register', { name, email, password } as RegisterRequest) as unknown as Promise<AuthResponse>,

  getMe: () => client.get<User>('/auth/me') as unknown as Promise<User>,
}
