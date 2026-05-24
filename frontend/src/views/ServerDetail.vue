<template>
  <div class="p-[24px] space-y-[40px] pb-20">
    <!-- Server Header Section -->
    <section class="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <h2 class="text-[32px] font-semibold text-on-surface">{{ server.name || '加载中...' }}</h2>
          <span class="px-2 py-0.5 rounded-full text-[10px] font-bold border uppercase tracking-wider flex items-center gap-1"
            :class="serverStatusClass">
            <span class="w-1.5 h-1.5 rounded-full animate-pulse" :class="serverStatusDotClass"></span>
            {{ serverStatusText }}
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
            已运行: {{ formatUptime(server.uptimeSeconds) }}
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
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 max-h-[400px] overflow-y-auto pr-1">
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

      <!-- Right Column: AI Insights + Volumes (row-span-2 to fill both rows) -->
      <div class="col-span-12 lg:col-span-4 lg:row-span-2 flex flex-col gap-[16px]">
      <!-- AI Insights Mini Card -->
      <div class="rounded-xl p-[20px] bg-gradient-to-br from-primary to-primary-container text-white shadow-xl shadow-primary/20 relative overflow-hidden group flex-1">
        <div class="absolute -right-10 -bottom-10 w-40 h-40 bg-white/10 rounded-full blur-3xl group-hover:scale-150 transition-transform duration-700"></div>
        <div class="relative z-10 flex flex-col h-full">
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-2xl" style="font-variation-settings: 'FILL' 1;">psychology</span>
              <h3 class="text-[20px] font-semibold">AI 智能观察</h3>
            </div>
            <div v-if="aiAnalysis" class="flex items-center gap-1.5">
              <div class="w-10 h-10 rounded-full border-2 border-white/40 flex items-center justify-center" :class="aiAnalysis.healthScore >= 80 ? 'border-green-300' : aiAnalysis.healthScore >= 60 ? 'border-amber-300' : 'border-red-300'">
                <span class="text-[14px] font-bold">{{ aiAnalysis.healthScore }}</span>
              </div>
            </div>
          </div>

          <div v-if="aiLoading" class="flex-1 flex items-center justify-center">
            <div class="flex items-center gap-2 text-white/70">
              <span class="material-symbols-outlined animate-spin text-lg">sync</span>
              <span class="text-[13px]">AI 分析中...</span>
            </div>
          </div>

          <div v-else-if="aiAnalysis" class="flex-1 space-y-3">
            <p class="text-[13px] text-white/90 leading-relaxed">{{ aiAnalysis.summary }}</p>

            <div v-if="aiAnalysis.findings?.length" class="space-y-1.5">
              <p class="text-[10px] font-bold uppercase tracking-widest text-white/50">发现</p>
              <div v-for="f in aiAnalysis.findings.slice(0, 3)" :key="f" class="flex items-start gap-1.5">
                <span class="material-symbols-outlined text-[14px] mt-0.5 text-amber-300">warning</span>
                <span class="text-[12px] text-white/80">{{ f }}</span>
              </div>
            </div>

            <div v-if="aiAnalysis.recommendations?.length" class="space-y-1.5">
              <p class="text-[10px] font-bold uppercase tracking-widest text-white/50">建议</p>
              <div v-for="r in aiAnalysis.recommendations.slice(0, 2)" :key="r" class="flex items-start gap-1.5">
                <span class="material-symbols-outlined text-[14px] mt-0.5 text-green-300">lightbulb</span>
                <span class="text-[12px] text-white/80">{{ r }}</span>
              </div>
            </div>
          </div>

          <div v-else class="flex-1 flex items-center justify-center">
            <div class="text-center">
              <p class="text-[13px] text-white/60 mb-2">{{ aiError || '暂无分析数据' }}</p>
              <button v-if="aiError" @click="retryAiAnalysis" class="px-3 py-1 bg-white/20 hover:bg-white/30 rounded text-[11px] text-white/80">重试</button>
            </div>
          </div>

          <button @click="$router.push('/ai-insights')" class="mt-4 px-4 py-2 bg-white/20 hover:bg-white/30 backdrop-blur-md rounded-lg text-[12px] font-bold transition-all w-fit">
            查看完整分析
          </button>
        </div>
      </div>

        <!-- Volume & Config Area -->
        <div class="glass-panel rounded-xl p-[20px] overflow-hidden flex flex-col flex-1">
          <h3 class="text-[24px] font-semibold flex items-center gap-2 mb-6">
            <span class="material-symbols-outlined text-primary">folder_managed</span>
            挂载卷与配置
          </h3>
          <div class="space-y-4 flex-1 overflow-y-auto">
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
          <button @click="openAddMount" class="w-full mt-4 py-2.5 rounded-lg border border-dashed border-outline-variant hover:border-primary/50 text-[14px] text-outline hover:text-primary transition-all flex items-center justify-center gap-2">
            <span class="material-symbols-outlined text-sm">add</span>
            添加挂载路径
          </button>
        </div>
      </div><!-- end right-col wrapper -->

      <!-- Topology Graph (Draggable Canvas) -->
      <div class="col-span-12 lg:col-span-8 glass-panel rounded-xl p-[20px] min-h-[400px] flex flex-col">
        <div class="flex justify-between items-center mb-4">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">hub</span>
            服务拓扑图
          </h3>
          <div class="flex gap-2">
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-secondary"></span> 运行中</span>
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-error"></span> 已停止</span>
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-primary"></span> 网络连接</span>
          </div>
        </div>
        <div ref="topologyCanvas" class="flex-1 bg-surface-container-lowest/50 rounded-lg border border-outline-variant/20 relative overflow-hidden cursor-grab"
          @mousedown="onCanvasMouseDown" @mousemove="onCanvasMouseMove" @mouseup="onCanvasMouseUp" @mouseleave="onCanvasMouseUp">
          <svg v-if="containers.length" class="absolute inset-0 w-full h-full" style="z-index: 1;">
            <defs>
              <marker id="arrowhead" markerWidth="6" markerHeight="4" refX="6" refY="2" orient="auto">
                <polygon points="0 0, 6 2, 0 4" fill="var(--color-primary)" opacity="0.5" />
              </marker>
              <marker id="arrowhead-weak" markerWidth="5" markerHeight="3" refX="5" refY="1.5" orient="auto">
                <polygon points="0 0, 5 1.5, 0 3" fill="var(--color-outline)" opacity="0.25" />
              </marker>
            </defs>
            <!-- Server-to-container edges (lighter, no label) -->
            <line v-for="(edge, ei) in topologyEdges.filter(e => !e.label)" :key="'se-'+ei"
              :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
              stroke="var(--color-outline)" stroke-width="1" stroke-dasharray="4 4" opacity="0.2"
              marker-end="url(#arrowhead-weak)" />
            <!-- Container-to-container network edges (prominent, with label) -->
            <line v-for="(edge, ei) in topologyEdges.filter(e => e.label)" :key="'ne-'+ei"
              :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
              stroke="var(--color-primary)" stroke-width="1.5" stroke-dasharray="6 3" opacity="0.45"
              marker-end="url(#arrowhead)" class="topology-line" />
            <text v-for="(edge, ei) in topologyEdges.filter(e => e.label)" :key="'nl-'+ei"
              :x="(edge.x1 + edge.x2) / 2" :y="(edge.y1 + edge.y2) / 2 - 6"
              text-anchor="middle" fill="var(--color-primary)" font-size="9" font-weight="bold" opacity="0.7">
              {{ edge.label }}
            </text>
          </svg>
          <div v-if="topologyNodes.length" class="absolute" style="z-index: 2;">
            <div v-for="(node, i) in topologyNodes" :key="node.name"
              class="absolute glass-panel rounded-xl flex flex-col items-center justify-center border-2 cursor-move select-none transition-all hover:shadow-lg hover:scale-105"
              :class="[
                node.isServer ? 'w-28 h-28 p-2 shadow-md' : 'w-24 h-24 p-1.5',
                node.status === 'RUNNING' || node.status === 'running' ? (node.isServer ? 'border-primary bg-primary/5' : 'border-secondary') : 'border-error'
              ]"
              :style="{ left: node.x + 'px', top: node.y + 'px', transform: 'translate(-50%, -50%)' }"
              @mousedown.stop="startDragNode(i, $event)">
              <span class="material-symbols-outlined mb-0.5"
                :class="[
                  node.isServer ? 'text-2xl' : 'text-xl',
                  node.status === 'RUNNING' || node.status === 'running' ? (node.isServer ? 'text-primary' : 'text-secondary') : 'text-error'
                ]">
                {{ node.icon }}
              </span>
              <span class="text-[9px] font-bold text-center truncate w-full leading-tight">{{ node.name }}</span>
              <span v-if="node.isServer" class="text-[8px] text-primary font-medium truncate w-full text-center">{{ node.networks }}</span>
              <span v-else class="text-[8px] text-outline truncate w-full text-center">{{ node.networks || '' }}</span>
            </div>
          </div>
          <div v-else class="flex items-center justify-center h-full">
            <div class="text-center">
              <span class="material-symbols-outlined text-outline text-[48px] mb-2">hub</span>
              <p class="text-on-surface-variant text-[14px]">暂无容器数据</p>
            </div>
          </div>
        </div>
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
            <textarea v-if="sshConfig.authMethod === 'KEY'" v-model="sshConfig.credential" rows="6" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface font-mono text-[12px] focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all resize-y" placeholder="粘贴完整的 SSH 私钥内容（含 BEGIN/END 行）"></textarea>
            <input v-else v-model="sshConfig.credential" type="password" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="输入密码" />
          </div>
        </div>
        <div v-if="sshTestResult" class="mt-3 p-3 rounded-lg text-[13px]" :class="sshTestResult.success ? 'bg-green-500/10 text-green-600 border border-green-600/20' : 'bg-error/10 text-error border border-error/20'">
          {{ sshTestResult.message }}
        </div>
      </div>

      <!-- Terminal Area (Resizable) -->
      <div class="col-span-12 glass-panel rounded-xl overflow-hidden flex flex-col" :style="{ height: terminalHeight + 'px' }">
        <div class="bg-surface-dim px-4 py-2 flex justify-between items-center border-b border-outline-variant/30 cursor-default">
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
            <p v-for="log in logs" :key="log.text" :class="log.color" class="opacity-80">
              {{ log.text }}
            </p>
          </div>
        </div>
        <div class="h-1.5 bg-surface-dim hover:bg-primary/30 cursor-row-resize flex items-center justify-center transition-colors"
          @mousedown="startResizeTerminal">
          <div class="w-8 h-0.5 bg-outline-variant/50 rounded-full"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { serversApi } from '@/api/servers'
import { aiApi } from '@/api/ai'
import { formatBytes } from '@/utils/format'
import TerminalModal from '@/components/modals/TerminalModal.vue'
import AddMountPathModal from '@/components/modals/AddMountPathModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import NewBackupModal from '@/components/modals/NewBackupModal.vue'

const route = useRoute()
const toast = useToastStore()
const modal = useModalStore()
const serverId = Number(route.params.id)

import type { Server, Container, Volume, AiServerAnalysis } from '@/types'

const server = ref<Server>({} as Server)
const containers = ref<(Container & { metric1: any; metric2: any })[]>([])
const volumes = ref<(Volume & { size: string; icon: string; iconColor: string; status: string; statusColor: string })[]>([])
const logs = ref<{ text: string; color: string }[]>([])
const aiAnalysis = ref<AiServerAnalysis | null>(null)
const aiLoading = ref(false)
const aiError = ref('')
const sshConfig = ref({ port: 22, username: 'root', authMethod: 'PASSWORD', credential: '' })
const sshTesting = ref(false)
const sshSaving = ref(false)
const sshTestResult = ref<{ success: boolean; message: string } | null>(null)

// Topology
const topologyCanvas = ref<HTMLElement | null>(null)
const topologyNodes = ref<Array<{ name: string; x: number; y: number; status: string; icon: string; networks: string; type: string; isServer?: boolean }>>([])
const topologyEdges = ref<Array<{ x1: number; y1: number; x2: number; y2: number; label: string }>>([])
const topologyRawEdges = ref<[string, string, string?][]>([])
let dragNodeIndex = -1
let dragOffsetX = 0
let dragOffsetY = 0

const topologyTypeConfig: Record<string, { icon: string; color: string; label: string }> = {
  'HTTP Server': { icon: 'language', color: 'text-blue-500', label: 'Web 服务' },
  'Web Server': { icon: 'language', color: 'text-blue-500', label: 'Web 服务' },
  'Database': { icon: 'database', color: 'text-amber-500', label: '数据库' },
  'Cache': { icon: 'memory', color: 'text-green-500', label: '缓存' },
  'Queue': { icon: 'swap_horiz', color: 'text-purple-500', label: '消息队列' },
  'Other': { icon: 'token', color: 'text-outline', label: '其他' },
}

// Terminal resize
const terminalHeight = ref(300)
let resizingTerminal = false
let resizeStartY = 0
let resizeStartH = 0

// Server status computed
const serverStatusClass = computed(() => {
  const s = server.value.status
  if (s === 'RUNNING') return 'bg-green-500/10 text-green-600 border-green-600/20'
  if (s === 'STOPPED') return 'bg-red-500/10 text-red-600 border-red-600/20'
  return 'bg-amber-500/10 text-amber-600 border-amber-600/20'
})
const serverStatusDotClass = computed(() => {
  const s = server.value.status
  if (s === 'RUNNING') return 'bg-green-500'
  if (s === 'STOPPED') return 'bg-red-500'
  return 'bg-amber-500'
})
const serverStatusText = computed(() => {
  const s = server.value.status
  if (s === 'RUNNING') return '运行中'
  if (s === 'STOPPED') return '已停止'
  return s || '检测中'
})

function openRemoteConnect() {
  modal.open({
    component: TerminalModal,
    title: `SSH 终端 — ${server.value.name || 'Server'}`,
    width: 'max-w-4xl',
    props: { serverId, serverName: server.value.name }
  })
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

function startResizeTerminal(e: MouseEvent) {
  resizingTerminal = true
  resizeStartY = e.clientY
  resizeStartH = terminalHeight.value
  document.addEventListener('mousemove', onResizeTerminal)
  document.addEventListener('mouseup', stopResizeTerminal)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
}

function onResizeTerminal(e: MouseEvent) {
  if (!resizingTerminal) return
  const delta = resizeStartY - e.clientY
  terminalHeight.value = Math.max(150, Math.min(800, resizeStartH + delta))
}

function stopResizeTerminal() {
  resizingTerminal = false
  document.removeEventListener('mousemove', onResizeTerminal)
  document.removeEventListener('mouseup', stopResizeTerminal)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function retryAiAnalysis() {
  aiError.value = ''
  aiLoading.value = true
  aiApi.analyzeServer(serverId).then(res => {
    const data = (res as any).data || res || null
    if (data && data.healthScore !== undefined) {
      aiAnalysis.value = data
      aiError.value = ''
    } else {
      aiError.value = '分析结果格式异常'
    }
  }).catch(e => {
    aiError.value = '分析失败: ' + (e?.message || '网络错误')
  }).finally(() => { aiLoading.value = false })
}

function formatUptime(seconds: number): string {
  if (!seconds || seconds <= 0) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${mins} 分钟`
  return `${mins} 分钟`
}

function initTopologyNodes() {
  if (!topologyCanvas.value || !containers.value.length) return
  const rect = topologyCanvas.value.getBoundingClientRect()
  const w = rect.width
  const h = rect.height
  const cx = w / 2
  const cy = h / 2

  // Group containers by type
  const groups: Record<string, any[]> = {}
  containers.value.forEach((c: any) => {
    const t = c.type || 'Other'
    if (!groups[t]) groups[t] = []
    groups[t].push(c)
  })

  // Layout zones: server in center, groups arranged around it
  // Web types at top, DB at bottom, Cache/Queue on sides
  const typeOrder = ['HTTP Server', 'Web Server', 'Queue', 'Cache', 'Database', 'Other']
  const groupKeys = Object.keys(groups).sort((a, b) => {
    const ai = typeOrder.indexOf(a) === -1 ? 99 : typeOrder.indexOf(a)
    const bi = typeOrder.indexOf(b) === -1 ? 99 : typeOrder.indexOf(b)
    return ai - bi
  })

  const nodes: typeof topologyNodes.value = []

  // Server node in the center
  nodes.push({
    name: server.value.name || 'Server',
    x: cx,
    y: cy,
    status: server.value.status || 'RUNNING',
    icon: 'dns',
    networks: server.value.ip || '',
    type: 'Server',
    isServer: true,
  })

  // Position groups in arcs around the center
  const groupCount = groupKeys.length
  const minRadius = Math.min(w, h) * 0.28
  const maxRadius = Math.min(w, h) * 0.42

  groupKeys.forEach((type, gi) => {
    const group = groups[type]
    const cfg = topologyTypeConfig[type] || topologyTypeConfig['Other']

    // Each group gets an angular sector
    const sectorStart = (2 * Math.PI * gi) / groupCount - Math.PI / 2
    const sectorEnd = (2 * Math.PI * (gi + 1)) / groupCount - Math.PI / 2

    group.forEach((c: any, ci: number) => {
      // Spread containers within the sector
      const spread = group.length === 1 ? 0 : (sectorEnd - sectorStart) * 0.7
      const angle = sectorStart + (sectorEnd - sectorStart) * 0.15 + (spread * ci) / Math.max(group.length - 1, 1)
      const radius = minRadius + (maxRadius - minRadius) * (0.6 + 0.4 * Math.sin(ci * 1.2))

      nodes.push({
        name: c.name,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
        status: c.status || 'RUNNING',
        icon: cfg.icon,
        networks: c.networks || '',
        type: type,
      })
    })
  })

  topologyNodes.value = nodes
  recalcTopologyEdges()
}

function recalcTopologyEdges() {
  if (!topologyCanvas.value) return
  const nameToNode: Record<string, typeof topologyNodes.value[0]> = {}
  topologyNodes.value.forEach(n => { nameToNode[n.name] = n })

  const serverNode = topologyNodes.value.find(n => n.isServer)
  const edges: typeof topologyEdges.value = []

  // Edges from raw topology data (container-to-container network connections)
  topologyRawEdges.value.forEach(edge => {
    const a = nameToNode[edge[0]]
    const b = nameToNode[edge[1]]
    if (a && b) {
      edges.push({ x1: a.x, y1: a.y, x2: b.x, y2: b.y, label: edge[2] || '' })
    }
  })

  // Edges from server to each container (hosting relationship)
  if (serverNode) {
    topologyNodes.value.forEach(node => {
      if (node.isServer) return
      // Only add if not already connected via raw edges
      const alreadyConnected = edges.some(e =>
        (e.x1 === serverNode.x && e.y1 === serverNode.y && e.x2 === node.x && e.y2 === node.y) ||
        (e.x2 === serverNode.x && e.y2 === serverNode.y && e.x1 === node.x && e.y1 === node.y)
      )
      if (!alreadyConnected) {
        edges.push({ x1: serverNode.x, y1: serverNode.y, x2: node.x, y2: node.y, label: '' })
      }
    })
  }

  topologyEdges.value = edges
}

function startDragNode(index: number, e: MouseEvent) {
  dragNodeIndex = index
  const node = topologyNodes.value[index]
  dragOffsetX = e.clientX - node.x
  dragOffsetY = e.clientY - node.y
}

function onCanvasMouseDown(_e: MouseEvent) {}

function onCanvasMouseMove(e: MouseEvent) {
  if (dragNodeIndex < 0) return
  const node = topologyNodes.value[dragNodeIndex]
  node.x = e.clientX - dragOffsetX
  node.y = e.clientY - dragOffsetY
  recalcTopologyEdges()
}

function onCanvasMouseUp() {
  dragNodeIndex = -1
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

function mapContainers(cData: any[]) {
  return cData.map((c: any) => {
    const defaults = containerDefaults[c.type as keyof typeof containerDefaults] || containerDefaults['HTTP Server']
    return {
      ...c,
      metric1: { label: defaults.metric1Label, value: c.cpuUsage || '0%', percent: c.cpuUsage || '0%' },
      metric2: { label: defaults.metric2Label, value: c.memoryUsage || '0MB', percent: c.memoryPercent || '0%' },
    }
  })
}

async function refreshContainers() {
  try {
    const res = await serversApi.getContainers(serverId)
    const cData = res || []
    containers.value = mapContainers(cData)
  } catch (e) {
    console.warn('Failed to refresh containers', e)
  }
}

let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  try {
    const [serverRes, containersRes, volumesRes, logsRes] = await Promise.all([
      serversApi.get(serverId),
      serversApi.getContainers(serverId),
      serversApi.getVolumes(serverId),
      serversApi.getLogs(serverId),
    ])

    // AI analysis runs separately (may be slow, up to 30s)
    aiLoading.value = true
    aiApi.analyzeServer(serverId).then(res => {
      const data = res || null
      if (data && data.healthScore !== undefined) {
        aiAnalysis.value = data
        aiError.value = ''
      } else {
        aiError.value = '分析结果格式异常'
        console.warn('AI analysis unexpected format:', res)
      }
    }).catch(e => {
      aiError.value = '分析失败: ' + (e?.message || '网络错误')
      console.warn('AI analysis failed:', e)
    }).finally(() => { aiLoading.value = false })
    server.value = serverRes || {}
    // Load SSH config from server data
    if (server.value.sshPort) sshConfig.value.port = server.value.sshPort
    if (server.value.sshUsername) sshConfig.value.username = server.value.sshUsername
    if (server.value.sshAuthMethod) sshConfig.value.authMethod = server.value.sshAuthMethod

    // Trigger health check to get real-time status
    serversApi.refreshHealth(serverId).then(healthRes => {
      const health = healthRes || {}
      if (health.status === 'ONLINE') {
        server.value.status = 'RUNNING'
        if (health.uptimeSeconds) server.value.uptimeSeconds = health.uptimeSeconds
        if (health.os) server.value.os = health.os
      }
    }).catch(() => {})
    const cData = containersRes || []
    containers.value = mapContainers(cData)
    const vData = volumesRes || []
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
    const lData = logsRes || []
    logs.value = lData.map((l: any) => ({
      text: l.message || l.text || '',
      color: l.level === 'ERROR' ? 'text-red-400' : l.level === 'WARN' ? 'text-amber-400' : l.level === 'DEBUG' ? 'text-blue-300' : 'text-green-400',
    }))

    // Fetch topology edges
    try {
      const topoRes = await serversApi.getTopology(serverId)
      const edges = topoRes || []
      topologyRawEdges.value = edges
      await nextTick()
      initTopologyNodes()
      window.addEventListener('resize', () => { initTopologyNodes() })
    } catch (e) {
      console.warn('Failed to load topology', e)
    }

    // Poll container stats every 5 seconds
    refreshTimer = setInterval(refreshContainers, 5000)
  } catch (e) {
    console.error('Failed to load server data', e)
  }
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  window.removeEventListener('resize', initTopologyNodes)
})
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
