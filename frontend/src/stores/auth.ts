import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('cv_token') || '')
  const user = ref<{ id: number; name: string; email: string; role: string } | null>(null)

  async function login(email: string, password: string) {
    const res: any = await authApi.login(email, password)
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('cv_token', res.data.token)
  }

  async function register(name: string, email: string, password: string) {
    const res: any = await authApi.register(name, email, password)
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('cv_token', res.data.token)
  }

  async function fetchUser() {
    if (!token.value) return
    try {
      const res: any = await authApi.getMe()
      user.value = res.data
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
