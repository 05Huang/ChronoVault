<template>
  <div class="p-6 space-y-4">
    <div v-if="messageHtml" class="text-[14px] text-on-surface-variant prose prose-sm max-w-none prose-headings:font-bold prose-h2:text-[16px] prose-h3:text-[14px] prose-h4:text-[13px] prose-p:my-1 prose-ul:my-1 prose-li:my-0" v-html="messageHtml"></div>
    <p v-else class="text-[14px] text-on-surface-variant">{{ message }}</p>
    <div class="flex justify-end gap-3">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleConfirm" :disabled="loading" class="px-4 py-2 text-[12px] font-bold text-white rounded-lg transition-all disabled:opacity-60" :class="confirmClass || 'bg-primary hover:bg-primary-container'">{{ loading ? '处理中...' : (confirmText || '确认') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'

const props = defineProps<{
  message?: string
  messageHtml?: string
  confirmText?: string
  confirmClass?: string
  successMessage?: string
  onConfirm?: () => Promise<void> | void
}>()

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()
const loading = ref(false)

async function handleConfirm() {
  if (props.onConfirm) {
    loading.value = true
    try {
      await props.onConfirm()
      if (props.successMessage) toast.success(props.successMessage)
      emit('close')
    } catch (e: unknown) {
      toast.error((e instanceof Error ? e.message : null) || '操作失败')
    } finally {
      loading.value = false
    }
  } else {
    if (props.successMessage) toast.success(props.successMessage)
    emit('close')
  }
}
</script>
