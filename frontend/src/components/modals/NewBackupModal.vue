<template>
  <div class="p-6 space-y-5">
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">目标服务器</label>
      <select v-model="form.server" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px] appearance-none">
        <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }} ({{ s.ip }})</option>
      </select>
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
      <input v-model="form.note" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="e.g., 部署前手动快照" />
    </div>
    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleCreate" :disabled="loading" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50">{{ loading ? '创建中...' : '立即备份' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { snapshotsApi } from '@/api/snapshots'
import { serversApi } from '@/api/servers'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const backupTypes = [
  { value: 'FULL', label: '全量快照', desc: '完整系统状态' },
  { value: 'INCREMENTAL', label: '增量快照', desc: '仅变更部分' },
]

const servers = ref<any[]>([])
const form = ref({ server: 0, type: 'FULL', note: '' })
const loading = ref(false)

onMounted(async () => {
  try {
    const res: any = await serversApi.getAll()
    servers.value = res.data || res || []
    if (servers.value.length > 0) {
      form.value.server = servers.value[0].id
    }
  } catch (e) {
    console.error('Failed to load servers', e)
  }
})

async function handleCreate() {
  loading.value = true
  try {
    await snapshotsApi.create({ serverId: form.value.server, type: form.value.type, note: form.value.note })
    toast.success('备份任务已创建')
    emit('close')
  } catch (e: any) {
    toast.error(e.message || '创建失败')
  } finally {
    loading.value = false
  }
}
</script>
