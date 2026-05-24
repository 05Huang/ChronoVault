import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import router from '@/router'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('cv_token') || '')
  const user = ref<User | null>(null)

  async function login(email: string, password: string) {
    const res = await authApi.login(email, password)
    token.value = res.token
    user.value = res.user ?? null
    localStorage.setItem('cv_token', res.token)
  }

  async function register(name: string, email: string, password: string) {
    const res = await authApi.register(name, email, password)
    token.value = res.token
    user.value = res.user ?? null
    localStorage.setItem('cv_token', res.token)
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
    user.value = null
    localStorage.removeItem('cv_token')
    router.push('/login')
  }

  return { token, user, login, register, fetchUser, logout }
})
