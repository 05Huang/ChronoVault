import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useToastStore } from '../toast'

describe('Toast Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('adds a toast', () => {
    const toast = useToastStore()
    toast.show('Test message', 'info')
    expect(toast.toasts).toHaveLength(1)
    expect(toast.toasts[0].message).toBe('Test message')
    expect(toast.toasts[0].type).toBe('info')
  })

  it('adds success toast', () => {
    const toast = useToastStore()
    toast.success('Operation completed')
    expect(toast.toasts).toHaveLength(1)
    expect(toast.toasts[0].type).toBe('success')
  })

  it('adds error toast', () => {
    const toast = useToastStore()
    toast.error('Something went wrong')
    expect(toast.toasts).toHaveLength(1)
    expect(toast.toasts[0].type).toBe('error')
  })

  it('removes a toast', () => {
    const toast = useToastStore()
    toast.show('Test', 'info')
    const id = toast.toasts[0].id
    toast.remove(id)
    expect(toast.toasts).toHaveLength(0)
  })

  it('supports multiple toasts', () => {
    const toast = useToastStore()
    toast.success('First')
    toast.error('Second')
    toast.warning('Third')
    expect(toast.toasts).toHaveLength(3)
  })
})