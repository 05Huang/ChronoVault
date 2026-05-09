import client from './client'

export const authApi = {
  login: (email: string, password: string) =>
    client.post('/auth/login', { email, password }),

  register: (name: string, email: string, password: string) =>
    client.post('/auth/register', { name, email, password }),

  getMe: () => client.get('/auth/me'),
}
