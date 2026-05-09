<template>
  <div class="fixed top-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none">
    <TransitionGroup name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-xl shadow-lg backdrop-blur-xl border text-[13px] font-medium min-w-[280px] animate-slide-in"
        :class="typeClasses[toast.type]"
        @click="remove(toast.id)"
      >
        <span class="material-symbols-outlined text-[18px]">{{ typeIcons[toast.type] }}</span>
        <span class="flex-1">{{ toast.message }}</span>
        <span class="material-symbols-outlined text-[16px] opacity-50 hover:opacity-100 cursor-pointer">close</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useToastStore } from '@/stores/toast'

const { toasts } = storeToRefs(useToastStore())
const { remove } = useToastStore()

const typeClasses: Record<string, string> = {
  success: 'bg-green-500/10 border-green-500/30 text-green-700',
  error: 'bg-error/10 border-error/30 text-error',
  info: 'bg-primary/10 border-primary/30 text-primary',
  warning: 'bg-tertiary/10 border-tertiary/30 text-tertiary',
}

const typeIcons: Record<string, string> = {
  success: 'check_circle',
  error: 'error',
  info: 'info',
  warning: 'warning',
}
</script>

<style scoped>
.toast-enter-active { animation: slideIn 0.3s cubic-bezier(0.22, 1, 0.36, 1); }
.toast-leave-active { animation: slideIn 0.2s cubic-bezier(0.22, 1, 0.36, 1) reverse; }

@keyframes slideIn {
  from { opacity: 0; transform: translateX(24px); }
  to { opacity: 1; transform: translateX(0); }
}
</style>
