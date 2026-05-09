<template>
  <div class="p-6 space-y-5">
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">卷名称</label>
      <input v-model="form.name" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="e.g., app_data" />
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">容器内挂载路径</label>
      <input v-model="form.path" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="e.g., /app/data" />
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">主机路径</label>
      <input v-model="form.hostPath" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="e.g., /mnt/volumes/app_data" />
    </div>
    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleAdd" :disabled="!form.name || !form.path || loading" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-40">{{ loading ? '添加中...' : '添加' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'
import { serversApi } from '@/api/servers'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const props = defineProps<{ serverId: number }>()

const form = ref({ name: '', path: '', hostPath: '' })
const loading = ref(false)

async function handleAdd() {
  if (!form.value.name || !form.value.path) return
  loading.value = true
  try {
    await serversApi.addVolume(props.serverId, { name: form.value.name, containerPath: form.value.path, hostPath: form.value.hostPath })
    toast.success(`挂载路径 ${form.value.name} 已添加`)
    emit('close')
  } catch (e: any) {
    toast.error(e.message || '添加失败')
  } finally {
    loading.value = false
  }
}
</script>
