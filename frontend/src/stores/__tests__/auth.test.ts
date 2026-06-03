import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'

// Mock the router
vi.mock('@/router', () => ({
  default: { push: vi.fn() }
}))

// Mock the auth API
vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    getMe: vi.fn(),
  }
}))

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('initializes with empty token', () => {
    const auth = useAuthStore()
    expect(auth.token).toBe('')
    expect(auth.user).toBeNull()
  })

  it('logout clears state', () => {
    const auth = useAuthStore()
    auth.token = 'test-token'
    auth.logout()
    expect(auth.token).toBe('')
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('cv_token')).toBeNull()
  })

  it('has correct initial state from localStorage', () => {
    localStorage.setItem('cv_token', 'saved-token')
    const auth = useAuthStore()
    expect(auth.token).toBe('saved-token')
  })
})