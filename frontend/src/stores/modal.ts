import { defineStore } from 'pinia'
import { ref, shallowRef, markRaw, type Component } from 'vue'

export interface ModalOptions {
  component: Component
  props?: Record<string, any>
  title?: string
  width?: string
}

export const useModalStore = defineStore('modal', () => {
  const isOpen = ref(false)
  const component = shallowRef<Component | null>(null)
  const props = ref<Record<string, any>>({})
  const title = ref('')
  const width = ref('max-w-lg')

  function open(options: ModalOptions) {
    component.value = markRaw(options.component)
    props.value = options.props || {}
    title.value = options.title || ''
    width.value = options.width || 'max-w-lg'
    isOpen.value = true
  }

  function close() {
    isOpen.value = false
    setTimeout(() => {
      component.value = null
      props.value = {}
    }, 200)
  }

  return { isOpen, component, props, title, width, open, close }
})
