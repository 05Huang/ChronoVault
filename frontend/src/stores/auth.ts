import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import router from '@/router'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('cv_token') || '')
  const refreshToken = ref(localStorage.getItem('cv_refresh_token') || '')
  const user = ref<User | null>(null)

  async function login(email: string, password: string) {
    const res = await authApi.login(email, password)
    token.value = res.token
    refreshToken.value = res.refreshToken ?? ''
    user.value = res.user ?? null
    localStorage.setItem('cv_token', res.token)
    if (res.refreshToken) {
      localStorage.setItem('cv_refresh_token', res.refreshToken)
    }
  }

  async function register(name: string, email: string, password: string) {
    const res = await authApi.register(name, email, password)
    token.value = res.token
    refreshToken.value = res.refreshToken ?? ''
    user.value = res.user ?? null
    localStorage.setItem('cv_token', res.token)
    if (res.refreshToken) {
      localStorage.setItem('cv_refresh_token', res.refreshToken)
    }
  }

  async function fetchUser() {
    if (!token.value) return
    try {
      const res = await authApi.getMe()
      user.value = res
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    localStorage.removeItem('cv_token')
    localStorage.removeItem('cv_refresh_token')
    router.push('/login')
  }

  return { token, refreshToken, user, login, register, fetchUser, logout }
})
