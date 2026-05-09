import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useAuthStore } from './auth'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)

  const currentUser = computed(() => {
    const auth = useAuthStore()
    return auth.user || { name: 'Admin', email: '', role: '' }
  })

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { sidebarCollapsed, currentUser, toggleSidebar }
})
