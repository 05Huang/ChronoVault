<template>
  <div class="p-[24px] space-y-[40px] pb-20">
    <!-- Server Header Section -->
    <section class="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <h2 class="text-[32px] font-semibold text-on-surface">{{ server.name || '加载中...' }}</h2>
          <span class="px-2 py-0.5 rounded-full bg-green-500/10 text-green-600 text-[10px] font-bold border border-green-600/20 uppercase tracking-wider flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>
            {{ server.status === 'ONLINE' || server.status === 'online' ? '运行中' : server.status || '-' }}
          </span>
        </div>
        <div class="flex flex-wrap gap-x-6 gap-y-2 text-on-surface-variant text-[14px] opacity-80">
          <div class="flex items-center gap-1.5">
            <span class="material-symbols-outlined text-sm">lan</span>
            {{ server.ip || '-' }}
          </div>
          <div class="flex items-center gap-1.5">
            <span class="material-symbols-outlined text-sm">laptop_windows</span>
            {{ server.os || '-' }}
          </div>
          <div class="flex items-center gap-1.5">
            <span class="material-symbols-outlined text-sm">schedule</span>
            已运行: {{ server.uptime || '-' }}
          </div>
        </div>
      </div>
      <div class="flex gap-3">
        <button @click="openRemoteConnect" class="px-4 py-2 rounded-lg bg-surface-container-lowest text-on-surface border border-outline-variant/50 text-[12px] font-bold flex items-center gap-2 hover:bg-surface-container-high transition-all">
          <span class="material-symbols-outlined text-lg">terminal</span>
          远程连接
        </button>
        <button @click="openNewSnapshot" class="px-4 py-2 rounded-lg bg-primary text-white text-[12px] font-bold flex items-center gap-2 hover:opacity-90 shadow-lg shadow-primary/20 transition-all">
          <span class="material-symbols-outlined text-lg">cached</span>
          立即快照
        </button>
      </div>
    </section>

    <!-- Bento Grid for Core Metrics & Topology -->
    <div class="grid grid-cols-12 gap-[16px]">
      <!-- Docker Containers Status -->
      <div class="col-span-12 lg:col-span-8 glass-panel rounded-xl p-[20px]">
        <div class="flex justify-between items-center mb-6">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">token</span>
            容器实例 (Docker)
          </h3>
          <span class="text-[14px] text-outline">{{ containers.length }} 个活动中</span>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div v-for="container in containers" :key="container.name" class="p-4 rounded-lg bg-surface-container-low border border-outline-variant/30 hover:border-primary/30 transition-all group">
            <div class="flex justify-between items-start mb-4">
              <div>
                <p class="text-[12px] font-bold text-outline">{{ container.type }}</p>
                <h4 class="text-lg font-bold">{{ container.name }}</h4>
              </div>
              <span class="w-3 h-3 rounded-full bg-green-500 shadow-[0_0_10px_rgba(16,185,129,0.4)]"></span>
            </div>
            <div class="space-y-3">
              <div class="space-y-1">
                <div class="flex justify-between text-[11px] font-bold uppercase tracking-tighter">
                  <span>{{ container.metric1.label }}</span>
                  <span class="text-primary">{{ container.metric1.value }}</span>
                </div>
                <div class="h-1.5 w-full bg-surface-container-highest rounded-full overflow-hidden">
                  <div class="h-full bg-primary rounded-full" :style="{ width: container.metric1.percent }"></div>
                </div>
              </div>
              <div class="space-y-1">
                <div class="flex justify-between text-[11px] font-bold uppercase tracking-tighter">
                  <span>{{ container.metric2.label }}</span>
                  <span class="text-secondary">{{ container.metric2.value }}</span>
                </div>
                <div class="h-1.5 w-full bg-surface-container-highest rounded-full overflow-hidden">
                  <div class="h-full bg-secondary rounded-full" :style="{ width: container.metric2.percent }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- AI Insights Mini Card -->
      <div class="col-span-12 lg:col-span-4 rounded-xl p-[20px] bg-gradient-to-br from-primary to-primary-container text-white shadow-xl shadow-primary/20 relative overflow-hidden group">
        <div class="absolute -right-10 -bottom-10 w-40 h-40 bg-white/10 rounded-full blur-3xl group-hover:scale-150 transition-transform duration-700"></div>
        <div class="relative z-10 flex flex-col h-full">
          <div class="flex items-center gap-2 mb-4">
            <span class="material-symbols-outlined text-2xl" style="font-variation-settings: 'FILL' 1;">psychology</span>
            <h3 class="text-[24px] font-semibold">AI 智能观察</h3>
          </div>
          <p class="text-[14px] text-white/90 flex-1 leading-relaxed">
            {{ aiInsight || 'AI 正在分析服务器状态，稍后将提供优化建议。' }}
          </p>
          <button @click="$router.push('/ai-insights')" class="mt-4 px-4 py-2 bg-white/20 hover:bg-white/30 backdrop-blur-md rounded-lg text-[14px] font-bold transition-all w-fit">
            优化建议中心
          </button>
        </div>
      </div>

      <!-- Topology Graph -->
      <div class="col-span-12 lg:col-span-8 glass-panel rounded-xl p-[20px] h-[400px] flex flex-col">
        <div class="flex justify-between items-center mb-4">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">hub</span>
            服务拓扑图
          </h3>
          <div class="flex gap-2">
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-primary"></span> 活跃连接</span>
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-outline-variant"></span> 配置中</span>
          </div>
        </div>
        <div class="flex-1 bg-surface-container-lowest/50 rounded-lg border border-outline-variant/20 relative overflow-hidden flex items-center justify-center">
          <div v-if="containers.length" class="flex flex-wrap gap-4 justify-center p-4">
            <div v-for="(c, i) in containers" :key="c.name"
              class="w-28 h-28 glass-panel rounded-xl flex flex-col items-center justify-center p-2 border-2 transition-all"
              :class="c.status === 'RUNNING' || c.status === 'running' ? 'border-secondary' : 'border-error'">
              <span class="material-symbols-outlined text-2xl mb-1" :class="c.status === 'RUNNING' || c.status === 'running' ? 'text-secondary' : 'text-error'">
                {{ c.type === 'Database' ? 'database' : c.type === 'Cache' ? 'memory' : 'token' }}
              </span>
              <span class="text-[10px] font-bold text-center truncate w-full">{{ c.name }}</span>
              <span class="text-[9px] text-outline">{{ c.status || '-' }}</span>
            </div>
          </div>
          <div v-else class="text-center">
            <span class="material-symbols-outlined text-outline text-[48px] mb-2">hub</span>
            <p class="text-on-surface-variant text-[14px]">暂无容器数据</p>
          </div>
        </div>
      </div>

      <!-- Volume & Config Area -->
      <div class="col-span-12 lg:col-span-4 glass-panel rounded-xl p-[20px] overflow-hidden">
        <h3 class="text-[24px] font-semibold flex items-center gap-2 mb-6">
          <span class="material-symbols-outlined text-primary">folder_managed</span>
          挂载卷与配置
        </h3>
        <div class="space-y-4">
          <div v-for="vol in volumes" :key="vol.name" class="flex items-center gap-3 p-3 rounded-lg hover:bg-surface-container-high/40 transition-colors">
            <div class="w-10 h-10 rounded-lg bg-surface-container-highest flex items-center justify-center" :class="vol.iconColor">
              <span class="material-symbols-outlined">{{ vol.icon }}</span>
            </div>
            <div class="flex-1">
              <h5 class="text-[14px] font-bold">{{ vol.name }}</h5>
              <p class="text-[11px] text-outline">{{ vol.path }}</p>
            </div>
            <div class="text-right">
              <p class="text-[14px] font-bold">{{ vol.size }}</p>
              <p class="text-[10px] font-bold" :class="vol.statusColor">{{ vol.status }}</p>
            </div>
          </div>
        </div>
        <button @click="openAddMount" class="w-full mt-6 py-2.5 rounded-lg border border-dashed border-outline-variant hover:border-primary/50 text-[14px] text-outline hover:text-primary transition-all flex items-center justify-center gap-2">
          <span class="material-symbols-outlined text-sm">add</span>
          添加挂载路径
        </button>
      </div>

      <!-- SSH Configuration -->
      <div class="col-span-12 glass-panel rounded-xl p-[20px]">
        <div class="flex justify-between items-center mb-4">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">vpn_key</span>
            SSH 连接配置
          </h3>
          <div class="flex gap-2">
            <button @click="testSshConnection" :disabled="sshTesting" class="px-4 py-2 rounded-lg border border-outline-variant/50 text-[12px] font-bold flex items-center gap-2 hover:bg-surface-container-high transition-all disabled:opacity-50">
              <span class="material-symbols-outlined text-lg" :class="sshTesting ? 'animate-spin' : ''">{{ sshTesting ? 'sync' : 'cell_tower' }}</span>
              {{ sshTesting ? '测试中...' : '测试连接' }}
            </button>
            <button @click="saveSshConfig" :disabled="sshSaving" class="px-4 py-2 rounded-lg bg-primary text-white text-[12px] font-bold flex items-center gap-2 hover:opacity-90 shadow-lg shadow-primary/20 transition-all disabled:opacity-50">
              {{ sshSaving ? '保存中...' : '保存配置' }}
            </button>
          </div>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">SSH 端口</label>
            <input v-model.number="sshConfig.port" type="number" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="22" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">用户名</label>
            <input v-model="sshConfig.username" type="text" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="root" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">认证方式</label>
            <select v-model="sshConfig.authMethod" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]">
              <option value="PASSWORD">密码</option>
              <option value="KEY">密钥</option>
            </select>
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">{{ sshConfig.authMethod === 'KEY' ? 'SSH 私钥' : '密码' }}</label>
            <input v-model="sshConfig.credential" :type="sshConfig.authMethod === 'KEY' ? 'text' : 'password'" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" :placeholder="sshConfig.authMethod === 'KEY' ? '粘贴私钥内容' : '输入密码'" />
          </div>
        </div>
        <div v-if="sshTestResult" class="mt-3 p-3 rounded-lg text-[13px]" :class="sshTestResult.success ? 'bg-green-500/10 text-green-600 border border-green-600/20' : 'bg-error/10 text-error border border-error/20'">
          {{ sshTestResult.message }}
        </div>
      </div>

      <!-- Terminal Area -->
      <div class="col-span-12 glass-panel rounded-xl overflow-hidden flex flex-col h-[300px]">
        <div class="bg-surface-dim px-4 py-2 flex justify-between items-center border-b border-outline-variant/30">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-on-surface-variant text-lg">terminal</span>
            <span class="text-[12px] font-bold text-on-surface-variant">日志终端 (Recent Logs)</span>
          </div>
          <div class="flex gap-4">
            <button @click="exportLogs" class="text-on-surface-variant hover:text-primary transition-colors">
              <span class="material-symbols-outlined text-lg">download</span>
            </button>
            <button @click="openClearLogs" class="text-on-surface-variant hover:text-primary transition-colors">
              <span class="material-symbols-outlined text-lg">delete</span>
            </button>
            <button @click="toggleFullscreen" class="text-on-surface-variant hover:text-primary transition-colors">
              <span class="material-symbols-outlined text-lg">open_in_full</span>
            </button>
          </div>
        </div>
        <div class="flex-1 bg-[#191b23] p-4 font-[Geist] text-[14px] overflow-y-auto">
          <div class="space-y-1">
            <p v-for="log in logs" :key="log.text" :class="log.color" class="opacity-80" :style="log.style">
              {{ log.text }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { serversApi } from '@/api/servers'
import { aiApi } from '@/api/ai'
import RemoteConnectModal from '@/components/modals/RemoteConnectModal.vue'
import AddMountPathModal from '@/components/modals/AddMountPathModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import NewBackupModal from '@/components/modals/NewBackupModal.vue'

const route = useRoute()
const toast = useToastStore()
const modal = useModalStore()
const serverId = Number(route.params.id)

const server = ref<any>({})
const containers = ref<any[]>([])
const volumes = ref<any[]>([])
const logs = ref<any[]>([])
const aiInsight = ref('')
const sshConfig = ref({ port: 22, username: 'root', authMethod: 'PASSWORD', credential: '' })
const sshTesting = ref(false)
const sshSaving = ref(false)
const sshTestResult = ref<{ success: boolean; message: string } | null>(null)

function openRemoteConnect() {
  modal.open({ component: RemoteConnectModal, title: `远程连接 — ${server.value.name || 'Server'}`, width: 'max-w-xl' })
}

function openAddMount() {
  modal.open({ component: AddMountPathModal, title: '添加挂载路径' })
}

function openClearLogs() {
  modal.open({
    component: ConfirmModal,
    title: '清空日志',
    props: {
      message: '确定要清空当前服务器的所有日志记录吗？清空后日志将无法恢复。建议先导出备份。',
      confirmText: '确认清空',
      confirmClass: 'bg-error hover:bg-error/90',
      successMessage: '日志已清空',
      onConfirm: async () => {
        await serversApi.clearLogs(serverId)
        logs.value = []
      },
    },
  })
}

function openNewSnapshot() {
  modal.open({ component: NewBackupModal, title: '发起新快照任务' })
}

async function testSshConnection() {
  sshTesting.value = true
  sshTestResult.value = null
  try {
    const res = await serversApi.testConnection(serverId) as any
    sshTestResult.value = res.data || res
  } catch (e: any) {
    sshTestResult.value = { success: false, message: e?.response?.data?.message || '测试失败' }
  } finally {
    sshTesting.value = false
  }
}

async function saveSshConfig() {
  sshSaving.value = true
  try {
    await serversApi.updateSshConfig(serverId, {
      port: sshConfig.value.port,
      username: sshConfig.value.username,
      authMethod: sshConfig.value.authMethod,
      credential: sshConfig.value.credential,
    })
    toast.success('SSH 配置已保存')
    sshConfig.value.credential = '' // Clear credential after save
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '保存失败')
  } finally {
    sshSaving.value = false
  }
}

function exportLogs() {
  if (!logs.value.length) {
    toast.error('暂无日志数据')
    return
  }
  const text = logs.value.map((l: any) => l.text).join('\n')
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `logs-${server.value.name || 'server'}-${new Date().toISOString().slice(0, 10)}.txt`
  a.click()
  URL.revokeObjectURL(url)
  toast.success('日志已导出')
}

function toggleFullscreen() {
  const el = document.querySelector('.bg-\\[\\#191b23\\]')
  if (el) {
    if (!document.fullscreenElement) {
      el.requestFullscreen?.()
    } else {
      document.exitFullscreen?.()
    }
  }
}

const containerDefaults = {
  'HTTP Server': { metric1Label: 'CPU 占用', metric2Label: '内存 占用' },
  Database: { metric1Label: 'CPU 占用', metric2Label: '磁盘 I/O' },
  Cache: { metric1Label: 'CPU 占用', metric2Label: '内存 占用' },
}

const volumeIcons: Record<string, { icon: string; iconColor: string }> = {
  database: { icon: 'database', iconColor: 'text-primary' },
  config: { icon: 'settings_ethernet', iconColor: 'text-secondary' },
  log: { icon: 'description', iconColor: 'text-tertiary' },
}

onMounted(async () => {
  try {
    const [serverRes, containersRes, volumesRes, logsRes, aiRes] = await Promise.all([
      serversApi.get(serverId),
      serversApi.getContainers(serverId),
      serversApi.getVolumes(serverId),
      serversApi.getLogs(serverId),
      aiApi.getInsights().catch(() => ({ data: [] })),
    ])
    server.value = (serverRes as any).data || serverRes || {}
    // Load SSH config from server data
    if (server.value.sshPort) sshConfig.value.port = server.value.sshPort
    if (server.value.sshUsername) sshConfig.value.username = server.value.sshUsername
    if (server.value.sshAuthMethod) sshConfig.value.authMethod = server.value.sshAuthMethod
    const cData = (containersRes as any).data || containersRes || []
    containers.value = cData.map((c: any) => {
      const defaults = containerDefaults[c.type as keyof typeof containerDefaults] || containerDefaults['HTTP Server']
      return {
        ...c,
        metric1: { label: defaults.metric1Label, value: c.cpuUsage || '0%', percent: c.cpuUsage || '0%' },
        metric2: { label: defaults.metric2Label, value: c.memoryUsage || '0MB', percent: c.memoryPercent || '0%' },
      }
    })
    const vData = (volumesRes as any).data || volumesRes || []
    volumes.value = vData.map((v: any) => {
      const iconCfg = volumeIcons[v.type] || volumeIcons.database
      return {
        ...v,
        ...iconCfg,
        size: v.sizeBytes ? formatBytes(v.sizeBytes) : v.size || '',
        status: v.status || 'RW',
        statusColor: v.status === 'RW' ? 'text-green-600' : 'text-outline',
      }
    })
    const lData = (logsRes as any).data || logsRes || []
    logs.value = lData.map((l: any) => ({
      text: l.message || l.text || '',
      color: l.level === 'ERROR' ? 'text-red-400' : l.level === 'WARN' ? 'text-amber-400' : l.level === 'DEBUG' ? 'text-blue-300' : 'text-green-400',
    }))
    const aiData = (aiRes as any).data || aiRes || []
    if (aiData.length > 0) {
      aiInsight.value = aiData[0].desc || aiData[0].title || ''
    }
  } catch (e) {
    console.error('Failed to load server data', e)
  }
})

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 2 ? 1 : 0) + ' ' + units[i]
}
</script>

<style scoped>
@keyframes pulse {
  0%, 100% { opacity: 0.8; }
  50% { opacity: 0.4; }
}
.topology-line {
  stroke-dasharray: 4;
  animation: dash 20s linear infinite;
}
@keyframes dash {
  to { stroke-dashoffset: -1000; }
}
</style>
