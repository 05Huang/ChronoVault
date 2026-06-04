<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'

const error = ref<Error | null>(null)
const errorInfo = ref('')

onErrorCaptured((err: Error, instance, info) => {
  error.value = err
  errorInfo.value = info
  console.error('[ErrorBoundary]', err, info)
  return false // Stop propagation
})

function reset() {
  error.value = null
  errorInfo.value = ''
}
</script>

<template>
  <div v-if="error" class="min-h-[200px] flex flex-col items-center justify-center p-8 text-center">
    <div class="w-16 h-16 rounded-full bg-error/10 flex items-center justify-center mb-4">
      <span class="material-symbols-outlined text-error text-3xl">error</span>
    </div>
    <h3 class="text-lg font-semibold text-on-surface mb-2">页面出错了</h3>
    <p class="text-sm text-on-surface-variant max-w-md mb-4">
      {{ error.message || '发生了未知错误' }}
    </p>
    <button
      @click="reset"
      class="px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
    >
      重试
    </button>
  </div>
  <slot v-else />
</template>