import { useToastStore } from '@/stores/toast'

/**
 * Unified error handler for frontend API calls.
 * Extracts user-friendly messages from backend ApiResponse format.
 */
export function getErrorMessage(error: unknown): string {
  if (!error) return '未知错误'

  // Axios error with backend ApiResponse
  if (typeof error === 'object' && error !== null) {
    const err = error as Record<string, unknown>

    // Backend ApiResponse format: { code, message, data }
    if ('message' in err && typeof err.message === 'string') {
      return err.message
    }

    // Axios response error
    if ('response' in err) {
      const resp = err.response as Record<string, unknown> | undefined
      if (resp?.data && typeof resp.data === 'object') {
        const data = resp.data as Record<string, unknown>
        if (typeof data.message === 'string') return data.message
      }
      if (resp?.status === 404) return '资源不存在'
      if (resp?.status === 403) return '无权限执行此操作'
      if (resp?.status === 429) return '请求过于频繁，请稍后再试'
      if (resp?.status && (resp.status as number) >= 500) return '服务器错误，请稍后重试'
    }

    // Standard Error
    if ('message' in err && typeof err.message === 'string') {
      return err.message
    }
  }

  if (typeof error === 'string') return error
  return '操作失败，请重试'
}

/**
 * Handle API error with toast notification.
 * Returns the error message for further use.
 */
export function handleApiError(error: unknown, context?: string): string {
  const message = getErrorMessage(error)
  try {
    const toast = useToastStore()
    toast.error(context ? `${context}: ${message}` : message)
  } catch {
    // Toast store may not be available outside app context
  }
  console.error(`[API Error${context ? ' - ' + context : ''}]`, error)
  return message
}