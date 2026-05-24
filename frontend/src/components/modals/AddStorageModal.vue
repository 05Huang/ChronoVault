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
      <label class="block text-[12px] font-bold text-on-surface-variant">名称</label>
      <input v-model="form.name" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="例如 生产环境备份" />
    </div>

    <!-- S3/OSS specific fields -->
    <template v-if="form.type === 'S3' || form.type === 'OSS'">
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">接入点 (Endpoint)</label>
        <input v-model="form.endpoint" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" :placeholder="form.type === 'OSS' ? '例如 oss-cn-hangzhou.aliyuncs.com' : '例如 s3.amazonaws.com'" />
      </div>
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">存储桶 (Bucket)</label>
        <input v-model="form.bucket" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="例如 my-backup-bucket" />
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">地域 (Region)</label>
          <input v-model="form.region" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" :placeholder="form.type === 'OSS' ? '例如 cn-hangzhou' : '例如 us-east-1'" />
        </div>
      </div>
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">访问密钥 ID (Access Key)</label>
        <input v-model="form.accessKey" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="AKIAIOSFODNN7EXAMPLE" />
      </div>
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">秘密访问密钥 (Secret Key)</label>
        <input v-model="form.secretKey" type="password" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY" />
      </div>
    </template>

    <!-- WebDAV specific fields -->
    <template v-if="form.type === 'WEBDAV'">
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">WebDAV 地址</label>
        <input v-model="form.endpoint" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="例如 https://dav.example.com/backup" />
      </div>
    </template>

    <!-- LOCAL specific fields -->
    <template v-if="form.type === 'LOCAL'">
      <div class="p-3 rounded-lg bg-warning/10 text-warning text-[12px] border border-warning/20">
        <span class="material-symbols-outlined text-[16px] align-middle mr-1">warning</span>
        本地存储会占用服务器磁盘空间，推荐使用 S3 或 OSS
      </div>
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">存储路径</label>
        <input v-model="form.endpoint" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="例如 /data/chronovault-repo" />
      </div>
    </template>

    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleAdd" :disabled="!canSubmit || loading" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-40">{{ loading ? '添加中...' : '添加' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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

const form = ref({
  type: 'S3',
  name: '',
  endpoint: '',
  bucket: '',
  region: '',
  accessKey: '',
  secretKey: '',
})
const loading = ref(false)

const canSubmit = computed(() => {
  if (!form.value.name) return false
  if (form.value.type === 'S3' || form.value.type === 'OSS') {
    return form.value.endpoint && form.value.bucket && form.value.accessKey && form.value.secretKey
  }
  if (form.value.type === 'WEBDAV') {
    return form.value.endpoint
  }
  if (form.value.type === 'LOCAL') {
    return form.value.endpoint
  }
  return true
})

async function handleAdd() {
  if (!canSubmit.value) return
  loading.value = true
  try {
    await storageApi.addTarget({
      type: form.value.type,
      name: form.value.name,
      endpoint: form.value.endpoint || undefined,
      accessKey: form.value.accessKey || undefined,
      secretKey: form.value.secretKey || undefined,
      region: form.value.region || undefined,
      bucket: form.value.bucket || undefined,
    })
    toast.success(`存储目标 ${form.value.name} 已添加`)
    emit('close')
  } catch (e: any) {
    toast.error(e.message || '添加失败')
  } finally {
    loading.value = false
  }
}
</script>
