import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'

interface ApiState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

/**
 * Composable for API calls with loading state and error handling.
 * Provides consistent UX across all API-driven views.
 *
 * Usage:
 *   const { data, loading, error, execute } = useApi<Snapshot[]>()
 *   onMounted(() => execute(() => snapshotsApi.getAll()))
 */
export function useApi<T = unknown>() {
  const data = ref<T | null>(null) as { value: T | null }
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function execute(fn: () => Promise<T>, options?: { silent?: boolean }): Promise<T | null> {
    loading.value = true
    error.value = null
    try {
      const result = await fn()
      data.value = result
      return result
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '操作失败'
      error.value = msg
      if (!options?.silent) {
        try {
          const toast = useToastStore()
          toast.error(msg)
        } catch { /* toast may not be available */ }
      }
      return null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    data.value = null
    loading.value = false
    error.value = null
  }

  return { data, loading, error, execute, reset }
}