<template>
  <div class="p-6 space-y-5">
    <div v-if="loadError" class="p-3 rounded-lg bg-error/10 text-error text-[13px] border border-error/20">
      {{ loadError }}
    </div>
    <div v-if="submitError" class="p-3 rounded-lg bg-error/10 text-error text-[13px] border border-error/20 flex items-start gap-2">
      <span class="material-symbols-outlined text-[18px] mt-0.5">error</span>
      <div>
        <p class="font-bold">创建失败</p>
        <p class="text-[12px] opacity-80 mt-1">{{ submitError }}</p>
      </div>
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">目标服务器</label>
      <select v-model="form.server" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px] appearance-none">
        <option :value="null" disabled>请选择服务器</option>
        <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }} ({{ s.ip }})</option>
      </select>
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">存储位置</label>
      <select v-model="form.storageTarget" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px] appearance-none">
        <option :value="null" disabled>请选择存储位置</option>
        <option v-for="t in storageTargets" :key="t.id" :value="t.id">
          {{ t.name }} ({{ t.type }}) {{ t.totalBytes > 0 ? '- 已用 ' + formatBytes(t.usedBytes) + ' / ' + formatBytes(t.totalBytes) : '' }}
        </option>
      </select>
      <p v-if="storageTargets.length > 0 && !form.storageTarget" class="text-[11px] text-outline">
        推荐使用 S3/OSS 等外部存储，避免占用服务器本地磁盘
      </p>
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">备份类型</label>
      <div class="grid grid-cols-2 gap-3">
        <button v-for="t in backupTypes" :key="t.value" @click="form.type = t.value"
          class="p-3 rounded-xl border-2 text-left transition-all"
          :class="form.type === t.value ? 'border-primary bg-primary/5' : 'border-outline-variant/30 hover:border-primary/30'">
          <p class="text-[12px] font-bold">{{ t.label }}</p>
          <p class="text-[10px] text-outline">{{ t.desc }}</p>
        </button>
      </div>
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">备注（可选）</label>
      <input v-model="form.note" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="例如 部署前手动快照" />
    </div>
    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleCreate" :disabled="loading || !form.server || !form.storageTarget" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50">{{ loading ? '创建中...' : '立即备份' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { snapshotsApi } from '@/api/snapshots'
import { serversApi } from '@/api/servers'
import { storageApi } from '@/api/storage'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const backupTypes = [
  { value: 'FULL', label: '全量快照', desc: '完整系统状态' },
  { value: 'INCREMENTAL', label: '增量快照', desc: '仅变更部分' },
]

const servers = ref<any[]>([])
const storageTargets = ref<any[]>([])
const form = ref({ server: null as number | null, storageTarget: null as number | null, type: 'FULL', note: '' })
const loading = ref(false)
const loadError = ref('')
const submitError = ref('')

function formatBytes(bytes: number) {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i]
}

onMounted(async () => {
  try {
    const [serverRes, storageRes] = await Promise.all([
      serversApi.getAll(),
      storageApi.getOverview()
    ])
    servers.value = serverRes || []
    storageTargets.value = storageRes || []
    if (servers.value.length > 0) {
      form.value.server = servers.value[0].id
    } else {
      loadError.value = '暂无可用服务器，请先添加服务器'
    }
    // Auto-select first non-LOCAL target, or first target
    if (storageTargets.value.length > 0) {
      const nonLocal = storageTargets.value.find((t: any) => t.type !== 'LOCAL')
      form.value.storageTarget = (nonLocal || storageTargets.value[0]).id
    }
  } catch (e: any) {
    console.error('Failed to load data', e)
    loadError.value = '加载数据失败'
  }
})

async function handleCreate() {
  if (!form.value.server) {
    toast.error('请选择目标服务器')
    return
  }
  if (!form.value.storageTarget) {
    toast.error('请选择存储位置')
    return
  }
  submitError.value = ''
  loading.value = true
  try {
    await snapshotsApi.create({ serverId: form.value.server, storageTargetId: form.value.storageTarget || undefined, type: form.value.type, note: form.value.note })
    toast.success('备份任务已创建')
    emit('close')
  } catch (e: any) {
    console.error('Snapshot creation failed:', e)
    const msg = e?.message || '创建失败，请检查服务器连接和存储配置'
    submitError.value = msg
    toast.error(msg)
  } finally {
    loading.value = false
  }
}
</script>
