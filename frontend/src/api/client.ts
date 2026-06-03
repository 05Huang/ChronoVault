import axios from 'axios'
import router from '@/router'
import { authApi } from './auth'

const client = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
let failedQueue: Array<{ resolve: (value: unknown) => void; reject: (reason?: unknown) => void }> = []

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error)
    } else {
      promise.resolve(token)
    }
  })
  failedQueue = []
}

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('cv_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (res) => {
    const body = res.data
    // Unwrap backend ApiResponse<T> wrapper { code, message, data, timestamp }
    if (body && typeof body === 'object' && 'data' in body && 'code' in body) {
      return body.data
    }
    return body
  },
  async (err) => {
    const originalRequest = err.config

    // If 401 and we have a refresh token, try to refresh
    if (err.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('cv_refresh_token')
      if (refreshToken) {
        if (isRefreshing) {
          // Queue this request while refresh is in progress
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject })
          }).then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            return client(originalRequest)
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const response = await authApi.refreshToken(refreshToken)
          const newToken = response.accessToken
          localStorage.setItem('cv_token', newToken)
          processQueue(null, newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return client(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError, null)
          localStorage.removeItem('cv_token')
          localStorage.removeItem('cv_refresh_token')
          router.push('/login')
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }
    }

    // If 403 or 401 without refresh, redirect to login
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('cv_token')
      localStorage.removeItem('cv_refresh_token')
      router.push('/login')
    }
    return Promise.reject(err.response?.data || err)
  }
)

export default client
