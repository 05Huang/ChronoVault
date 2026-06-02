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
      <!-- Single server mode -->
      <div v-if="!multiMode">
        <select v-model="form.server" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px] appearance-none">
          <option :value="null" disabled>请选择服务器</option>
          <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }} ({{ s.ip }})</option>
        </select>
        <button v-if="servers.length > 1" @click="multiMode = true; form.server = null"
          class="mt-2 text-[11px] text-primary font-bold flex items-center gap-1 hover:underline">
          <span class="material-symbols-outlined text-[14px]">add_circle</span>
          切换到多服务器模式
        </button>
      </div>
      <!-- Multi server mode -->
      <div v-else class="space-y-2">
        <div class="flex flex-wrap gap-2">
          <button v-for="s in servers" :key="s.id" @click="toggleMultiServer(s.id)"
            class="px-3 py-1.5 rounded-lg text-[11px] font-bold border transition-all"
            :class="form.serverIds.includes(s.id)
              ? 'bg-primary/10 border-primary text-primary'
              : 'bg-white/50 border-outline-variant/30 text-on-surface-variant hover:border-primary/30'">
            {{ s.name }}
          </button>
        </div>
        <p class="text-[11px] text-outline">已选择 {{ form.serverIds.length }} 台服务器</p>
        <button @click="multiMode = false; form.serverIds = []"
          class="text-[11px] text-outline font-bold flex items-center gap-1 hover:text-on-surface-variant">
          <span class="material-symbols-outlined text-[14px]">arrow_back</span>
          切换回单服务器模式
        </button>
      </div>
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

    <!-- 高级选项 -->
    <div class="border border-outline-variant/30 rounded-xl overflow-hidden">
      <button @click="showAdvanced = !showAdvanced" class="w-full px-4 py-3 flex items-center justify-between hover:bg-surface-container-high/50 transition-colors">
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined text-[18px] text-on-surface-variant">tune</span>
          <span class="text-[12px] font-bold text-on-surface-variant">高级选项 — 选择性备份</span>
        </div>
        <span class="material-symbols-outlined text-[18px] text-on-surface-variant transition-transform" :class="showAdvanced ? 'rotate-180' : ''">expand_more</span>
      </button>
      <div v-show="showAdvanced" class="px-4 pb-4 space-y-4 border-t border-outline-variant/20">
        <p class="text-[11px] text-outline pt-3">留空则备份整个服务器（/），填写路径可只备份指定目录</p>

        <!-- 备份路径 -->
        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">备份路径</label>
          <div class="flex flex-wrap gap-2 mb-2">
            <button v-for="preset in presetPaths" :key="preset.value" @click="togglePresetPath(preset.value)"
              class="px-3 py-1.5 rounded-lg text-[11px] font-bold border transition-all"
              :class="form.paths.includes(preset.value)
                ? 'bg-primary/10 border-primary text-primary'
                : 'bg-white/50 border-outline-variant/30 text-on-surface-variant hover:border-primary/30'">
              {{ preset.label }}
            </button>
          </div>
          <div class="flex gap-2">
            <input v-model="customPath" @keydown.enter.prevent="addCustomPath"
              class="flex-1 px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
              placeholder="/custom/path" />
            <button @click="addCustomPath" class="px-3 py-2 bg-surface-container-high border border-outline-variant/30 rounded-lg text-[11px] font-bold text-on-surface-variant hover:bg-surface-container-highest transition-colors">
              添加
            </button>
          </div>
          <div v-if="form.paths.length > 0" class="flex flex-wrap gap-1.5">
            <span v-for="(p, i) in form.paths" :key="i"
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-primary/10 text-primary text-[10px] font-bold">
              {{ p }}
              <button @click="form.paths.splice(i, 1)" class="hover:text-error transition-colors">&times;</button>
            </span>
          </div>
        </div>

        <!-- 排除模式 -->
        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">排除模式</label>
          <div class="flex flex-wrap gap-2 mb-2">
            <button v-for="preset in presetExcludes" :key="preset.value" @click="togglePresetExclude(preset.value)"
              class="px-3 py-1.5 rounded-lg text-[11px] font-bold border transition-all"
              :class="form.excludes.includes(preset.value)
                ? 'bg-error/10 border-error text-error'
                : 'bg-white/50 border-outline-variant/30 text-on-surface-variant hover:border-error/30'">
              {{ preset.label }}
            </button>
          </div>
          <div class="flex gap-2">
            <input v-model="customExclude" @keydown.enter.prevent="addCustomExclude"
              class="flex-1 px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
              placeholder="*.log" />
            <button @click="addCustomExclude" class="px-3 py-2 bg-surface-container-high border border-outline-variant/30 rounded-lg text-[11px] font-bold text-on-surface-variant hover:bg-surface-container-highest transition-colors">
              添加
            </button>
          </div>
          <div v-if="form.excludes.length > 0" class="flex flex-wrap gap-1.5">
            <span v-for="(e, i) in form.excludes" :key="i"
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-error/10 text-error text-[10px] font-bold">
              {{ e }}
              <button @click="form.excludes.splice(i, 1)" class="hover:text-on-surface transition-colors">&times;</button>
            </span>
          </div>
        </div>
      </div>
    </div>
    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleCreate" :disabled="loading || (!form.server && !multiMode) || (multiMode && form.serverIds.length === 0) || !form.storageTarget" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50">{{ loading ? '创建中...' : (multiMode && form.serverIds.length > 1 ? '批量备份 (' + form.serverIds.length + ' 台)' : '立即备份') }}</button>
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
const form = ref({ server: null as number | null, serverIds: [] as number[], storageTarget: null as number | null, type: 'FULL', note: '', paths: [] as string[], excludes: [] as string[] })
const loading = ref(false)
const loadError = ref('')
const submitError = ref('')
const showAdvanced = ref(false)
const multiMode = ref(false)
const customPath = ref('')
const customExclude = ref('')

const presetPaths = [
  { label: '/etc', value: '/etc' },
  { label: '/var/www', value: '/var/www' },
  { label: '/home', value: '/home' },
  { label: '/opt', value: '/opt' },
  { label: '/var/lib', value: '/var/lib' },
  { label: 'Docker Compose', value: '/opt/docker-compose' },
]

const presetExcludes = [
  { label: '*.log', value: '*.log' },
  { label: 'node_modules', value: 'node_modules' },
  { label: '.git', value: '.git' },
  { label: '/proc', value: '/proc' },
  { label: '/sys', value: '/sys' },
  { label: '/dev', value: '/dev' },
  { label: '/tmp', value: '/tmp' },
  { label: '/var/cache', value: '/var/cache' },
]

function togglePresetPath(val: string) {
  const idx = form.value.paths.indexOf(val)
  if (idx >= 0) form.value.paths.splice(idx, 1)
  else form.value.paths.push(val)
}

function togglePresetExclude(val: string) {
  const idx = form.value.excludes.indexOf(val)
  if (idx >= 0) form.value.excludes.splice(idx, 1)
  else form.value.excludes.push(val)
}

function addCustomPath() {
  const v = customPath.value.trim()
  if (v && !form.value.paths.includes(v)) {
    form.value.paths.push(v)
    customPath.value = ''
  }
}

function addCustomExclude() {
  const v = customExclude.value.trim()
  if (v && !form.value.excludes.includes(v)) {
    form.value.excludes.push(v)
    customExclude.value = ''
  }
}

function toggleMultiServer(id: number) {
  const idx = form.value.serverIds.indexOf(id)
  if (idx >= 0) form.value.serverIds.splice(idx, 1)
  else form.value.serverIds.push(id)
}

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
  if (multiMode.value && form.value.serverIds.length === 0) {
    toast.error('请至少选择一台服务器')
    return
  }
  if (!multiMode.value && !form.value.server) {
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
    if (multiMode.value && form.value.serverIds.length > 1) {
      // Multi-server batch snapshot
      const batchId = await snapshotsApi.batch({
        serverIds: form.value.serverIds,
        storageTargetId: form.value.storageTarget || undefined,
        name: form.value.note || '批量快照',
      })
      toast.success('批量快照任务已创建 (ID: ' + batchId + ')')
    } else {
      // Single server snapshot
      const serverId = multiMode.value ? form.value.serverIds[0] : form.value.server
      await snapshotsApi.create({
        serverId: serverId!,
        storageTargetId: form.value.storageTarget || undefined,
        type: form.value.type,
        note: form.value.note,
        paths: form.value.paths.length > 0 ? form.value.paths : undefined,
        excludes: form.value.excludes.length > 0 ? form.value.excludes : undefined,
      })
      toast.success('备份任务已创建')
    }
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
