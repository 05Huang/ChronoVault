import axios from 'axios'
import router from '@/router'

const client = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
})

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
  (err) => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('cv_token')
      router.push('/login')
    }
    return Promise.reject(err.response?.data || err)
  }
)

export default client
