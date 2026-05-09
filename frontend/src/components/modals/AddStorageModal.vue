<template>
  <div class="p-6 space-y-5">
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">存储类型</label>
      <div class="grid grid-cols-3 gap-3">
        <button v-for="t in types" :key="t.value" @click="form.type = t.value"
          class="p-3 rounded-xl border-2 text-center transition-all"
          :class="form.type === t.value ? 'border-primary bg-primary/5' : 'border-outline-variant/30 hover:border-primary/30'">
          <span class="material-symbols-outlined text-[24px]" :class="form.type === t.value ? 'text-primary' : 'text-outline'">{{ t.icon }}</span>
          <p class="text-[11px] font-bold mt-1">{{ t.label }}</p>
        </button>
      </div>
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">存储桶名称</label>
      <input v-model="form.name" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="e.g., my-backup-bucket" />
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">Endpoint URL</label>
      <input v-model="form.endpoint" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="e.g., https://s3.amazonaws.com" />
    </div>
    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleAdd" :disabled="!form.name || loading" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-40">{{ loading ? '添加中...' : '添加' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'
import { storageApi } from '@/api/storage'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const types = [
  { value: 'S3', label: 'S3 对象', icon: 'cloud' },
  { value: 'OSS', label: '阿里云 OSS', icon: 'cloud' },
  { value: 'WEBDAV', label: 'WebDAV', icon: 'folder_special' },
  { value: 'LOCAL', label: '本地存储', icon: 'hard_drive' },
  { value: 'BLOCK', label: '块存储', icon: 'hard_drive' },
  { value: 'ARCHIVE', label: '冷归档', icon: 'archive' },
]

const form = ref({ type: 'S3', name: '', endpoint: '' })
const loading = ref(false)

async function handleAdd() {
  if (!form.value.name) return
  loading.value = true
  try {
    await storageApi.addTarget({ type: form.value.type, name: form.value.name, endpoint: form.value.endpoint, totalBytes: 0 })
    toast.success(`存储桶 ${form.value.name} 已添加`)
    emit('close')
  } catch (e: any) {
    toast.error(e.message || '添加失败')
  } finally {
    loading.value = false
  }
}
</script>
