import { ref } from 'vue'

/**
 * Composable for managing loading state on API calls.
 * Prevents duplicate submissions and provides consistent loading UX.
 *
 * Usage:
 *   const { isLoading, withLoading } = useLoading()
 *   async function save() {
 *     await withLoading(async () => {
 *       await api.save(data)
 *     })
 *   }
 */
export function useLoading(initialState = false) {
  const isLoading = ref(initialState)

  async function withLoading<T>(fn: () => Promise<T>): Promise<T | undefined> {
    if (isLoading.value) return undefined // Prevent duplicate submissions
    isLoading.value = true
    try {
      return await fn()
    } finally {
      isLoading.value = false
    }
  }

  function setLoading(state: boolean) {
    isLoading.value = state
  }

  return { isLoading, withLoading, setLoading }
}