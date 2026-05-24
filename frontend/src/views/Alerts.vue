<template>
  <div class="p-[24px] space-y-[40px]">
    <!-- Quick Stats -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-[16px]">
      <div v-for="card in severityCards" :key="card.key" @click="toggleSeverityFilter(card.key)"
        class="glass-panel p-[20px] rounded-xl flex items-center gap-4 cursor-pointer transition-all"
        :class="[
          card.borderClass,
          activeSeverity === card.key ? 'ring-2 ring-primary shadow-lg shadow-primary/10' : 'hover:translate-y-[-2px]'
        ]">
        <div class="w-12 h-12 rounded-lg flex items-center justify-center" :class="card.iconBg">
          <span class="material-symbols-outlined" :class="card.iconColor">{{ card.icon }}</span>
        </div>
        <div>
          <p class="text-[12px] font-bold text-on-surface-variant">{{ card.label }}</p>
          <p class="text-[24px] font-bold">{{ card.value }}</p>
        </div>
      </div>
    </div>

    <!-- Filter Tabs -->
    <div class="flex items-center justify-between">
      <div class="flex gap-2 overflow-x-auto pb-2">
        <button v-for="filter in filters" :key="filter" @click="activeFilter = filter"
          class="px-4 py-1.5 rounded-full text-[12px] font-bold transition-all flex items-center gap-1.5"
          :class="activeFilter === filter ? 'bg-on-background text-background' : 'bg-surface-container hover:bg-surface-container-high text-on-surface-variant border border-outline-variant/30'">
          {{ filter }}
        </button>
      </div>
      <button @click="exportAlertHistory" class="text-primary text-[12px] font-bold flex items-center gap-1 hover:underline">
        <span class="material-symbols-outlined text-[18px]">history</span> 导出历史
      </button>
    </div>

    <!-- Alert Cards & Side Panel -->
    <div class="grid grid-cols-1 xl:grid-cols-12 gap-[16px] items-start">
      <!-- Alert Stream -->
      <section class="xl:col-span-8 space-y-4">
        <div v-for="alert in filteredAlerts" :key="alert.id"
          class="glass-panel rounded-xl border-l-4 overflow-hidden transition-all hover:translate-x-1"
          :class="severityColors[alert.severity] || 'border-outline-variant'">
          <div class="p-[20px] flex flex-col md:flex-row gap-6">
            <div class="flex-1">
              <div class="flex items-center gap-2 mb-2">
                <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider" :class="severityBadge[alert.severity] || 'bg-outline/10 text-outline'">{{ alert.severity || 'Info' }}</span>
                <span class="text-[12px] text-on-surface-variant">{{ alert.time || alert.createdAt || '' }} {{ alert.source ? '• ' + alert.source : '' }}</span>
              </div>
              <h3 class="text-lg font-semibold mb-2">{{ alert.title }}</h3>
              <p class="text-[14px] text-on-surface-variant mb-4">{{ alert.description || alert.message || '' }}</p>
              <div v-if="alert.aiAnalysis" class="bg-inverse-surface text-surface rounded-lg p-3 font-[Geist] text-[12px] opacity-90 border border-white/10">
                <p class="text-primary-fixed-dim">// Root Cause Analysis by AI</p>
                <p class="mt-1">{{ alert.aiAnalysis }}</p>
              </div>
            </div>
            <div class="flex flex-row md:flex-col gap-2 justify-end">
              <button v-if="alert.severity === 'CRITICAL'" @click="openRestartConfirm(alert.id)" class="flex-1 md:flex-none px-6 py-2.5 bg-error text-white rounded-lg text-[12px] font-bold hover:bg-error/90 transition-colors shadow-lg shadow-error/20 flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">restart_alt</span> 自动重启
              </button>
              <button v-if="alert.severity === 'PREDICTIVE'" @click="openExpandStorage(alert.id)" class="flex-1 md:flex-none px-6 py-2.5 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary/90 transition-colors shadow-lg shadow-primary/20 flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">add_task</span> 扩展存储
              </button>
              <button v-if="alert.severity === 'WARNING'" @click="openRollbackConfig(alert.id)" class="flex-1 md:flex-none px-6 py-2.5 bg-surface-container-highest text-on-surface text-[12px] font-bold rounded-lg hover:bg-outline-variant/30 transition-colors flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">undo</span> 回滚配置
              </button>
              <button @click="showAlertDetail(alert)" class="flex-1 md:flex-none px-6 py-2.5 bg-surface-container-highest text-on-surface text-[12px] font-bold rounded-lg hover:bg-outline-variant/30 transition-colors flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">visibility</span> 详情
              </button>
            </div>
          </div>
        </div>
        <div v-if="!alerts.length" class="glass-panel rounded-xl p-8 text-center">
          <p class="text-on-surface-variant">暂无告警数据</p>
        </div>
      </section>

      <!-- Right: Integrations & Event Stream -->
      <aside class="xl:col-span-4 space-y-[16px]">
        <!-- Notification Integrations -->
        <div class="glass-panel rounded-xl overflow-hidden">
          <div class="p-[20px] border-b border-outline-variant/20 bg-surface-container-low">
            <h3 class="font-bold text-on-surface flex items-center gap-2">
              <span class="material-symbols-outlined text-primary">hub</span> 通知集成
            </h3>
          </div>
          <div class="p-[20px] space-y-4">
            <div v-for="integration in integrations" :key="integration.name"
              @click="openIntegrationConfig(integration)"
              class="flex items-center justify-between p-3 rounded-lg border border-outline-variant/20 hover:bg-surface-container/30 transition-colors cursor-pointer">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded flex items-center justify-center text-white" :class="integration.iconBg">
                  <span v-if="integration.icon" class="material-symbols-outlined">{{ integration.icon }}</span>
                  <span v-else class="font-bold text-sm">{{ integration.initial }}</span>
                </div>
                <div>
                  <p class="text-[14px] font-bold">{{ integration.name }}</p>
                  <p class="text-[10px] text-on-surface-variant">{{ integration.desc }}</p>
                </div>
              </div>
              <div class="w-8 h-4 rounded-full relative" :class="integration.active ? 'bg-primary' : 'bg-outline-variant'">
                <div class="absolute top-1 w-2 h-2 bg-white rounded-full" :class="integration.active ? 'right-1' : 'left-1'"></div>
              </div>
            </div>
            <button @click="openAddIntegration" class="w-full py-2 border-2 border-dashed border-outline-variant/50 rounded-lg text-on-surface-variant text-[12px] font-bold hover:border-primary/50 hover:text-primary transition-all">
              + 添加新集成
            </button>
          </div>
        </div>

        <!-- AI Root Cause Summary -->
        <div class="glass-panel rounded-xl p-[20px] bg-gradient-to-br from-primary-container/5 to-secondary-container/10">
          <div class="flex items-center gap-3 mb-4">
            <span class="material-symbols-outlined text-primary">auto_awesome</span>
            <h3 class="font-bold">AI 异常模式分析</h3>
          </div>
          <p v-if="aiAnalysis" class="text-[14px] text-on-surface-variant leading-relaxed mb-4">
            {{ aiAnalysis }}
          </p>
          <p v-else class="text-[14px] text-on-surface-variant leading-relaxed mb-4">
            暂无 AI 分析数据。系统将自动分析异常模式并在此展示。
          </p>
          <button @click="generateAiReport" class="w-full py-2 bg-white text-primary border border-primary/20 rounded-lg text-[12px] font-bold hover:bg-primary/5 transition-all">
            生成详细分析报告
          </button>
        </div>

        <!-- Event Stream -->
        <div class="glass-panel rounded-xl overflow-hidden">
          <div class="p-[20px] bg-surface-container-high/50 flex items-center justify-between">
            <h3 class="font-bold text-on-surface text-sm uppercase tracking-wide">事件流 (实时)</h3>
            <span class="flex items-center gap-1 text-[10px] font-bold" :class="connected ? 'text-primary' : 'text-outline'">
              <span class="w-1.5 h-1.5 rounded-full animate-ping" :class="connected ? 'bg-primary' : 'bg-outline'"></span> {{ connected ? 'LIVE' : 'OFFLINE' }}
            </span>
          </div>
          <div class="p-[20px] space-y-3 max-h-[300px] overflow-y-auto">
            <div v-for="event in events" :key="event.time" class="flex gap-3 text-[11px] items-start border-b border-outline-variant/10 pb-2">
              <span class="text-outline shrink-0">{{ event.time }}</span>
              <span v-if="event.tag" class="font-bold shrink-0" :class="event.tagColor">{{ event.tag }}</span>
              <span class="text-on-surface-variant" v-html="event.text"></span>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- FAB -->
    <button @click="openAlertRule" class="fixed bottom-8 right-8 w-14 h-14 bg-primary text-white rounded-full shadow-2xl flex items-center justify-center hover:scale-105 active:scale-95 transition-all z-50">
      <span class="material-symbols-outlined text-[28px]" style="font-variation-settings: 'FILL' 1;">add</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { alertsApi } from '@/api/alerts'
import { integrationsApi } from '@/api/integrations'
import { aiApi } from '@/api/ai'
import { useWebSocket } from '@/composables/useWebSocket'
import AddIntegrationModal from '@/components/modals/AddIntegrationModal.vue'
import AlertRuleModal from '@/components/modals/AlertRuleModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'

const toast = useToastStore()
const modal = useModalStore()

function openAddIntegration() {
  modal.open({ component: AddIntegrationModal, title: '添加通知集成' })
}

function openAlertRule() {
  modal.open({ component: AlertRuleModal, title: '创建新告警规则', width: 'max-w-md' })
}

function openIntegrationConfig(integration: IntegrationDisplay) {
  modal.open({
    component: ConfirmModal,
    title: `${integration.name} 集成配置`,
    props: {
      message: `集成类型: ${integration.type || integration.name}\n状态: ${integration.active ? '已启用' : '已禁用'}\nURL: ${integration.url || '未配置'}\n\n可通过"添加新集成"按钮修改配置。`,
      confirmText: '关闭',
      confirmClass: 'bg-surface-container-highest text-on-surface',
    },
  })
}

function openRestartConfirm(alertId: number) {
  modal.open({
    component: ConfirmModal,
    title: '自动重启容器',
    props: {
      message: '将自动重启崩溃的容器实例。重启期间该服务将短暂不可用。是否继续？',
      confirmText: '确认重启',
      successMessage: '容器已自动重启',
      onConfirm: async () => { await alertsApi.restart(alertId) },
    },
  })
}

function openExpandStorage(alertId: number) {
  modal.open({
    component: ConfirmModal,
    title: '扩展存储空间',
    props: {
      message: '将为存储卷扩展空间，扩展过程不中断服务。是否继续？',
      confirmText: '确认扩展',
      successMessage: '存储扩展任务已提交',
      onConfirm: async () => { await alertsApi.expandStorage(alertId) },
    },
  })
}

function openRollbackConfig(alertId: number) {
  modal.open({
    component: ConfirmModal,
    title: '回滚配置',
    props: {
      message: '即将回滚配置至上一稳定版本。回滚期间可能出现短暂延迟。是否继续？',
      confirmText: '确认回滚',
      confirmClass: 'bg-error hover:bg-error/90',
      successMessage: '配置已回滚至上一版本',
      onConfirm: async () => { await alertsApi.rollbackConfig(alertId) },
    },
  })
}

function showAlertDetail(alert: Alert) {
  modal.open({
    component: ConfirmModal,
    title: '告警详情',
    props: {
      message: `【${alert.severity}】${alert.title}\n\n${alert.description || '无详细描述'}\n\n来源: ${alert.source || '未知'}\n时间: ${alert.time || alert.createdAt || '未知'}`,
      confirmText: '关闭',
      confirmClass: 'bg-surface-container-highest text-on-surface',
    },
  })
}

async function generateAiReport() {
  try {
    const res = await aiApi.generateReport()
    const report = res || '报告生成失败'
    modal.open({
      component: ConfirmModal,
      title: 'AI 分析报告',
      props: {
        message: typeof report === 'string' ? report : JSON.stringify(report, null, 2),
        confirmText: '关闭',
        confirmClass: 'bg-surface-container-highest text-on-surface',
      },
    })
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '生成报告失败')
  }
}

function exportAlertHistory() {
  const csv = ['时间,级别,标题,来源,状态']
  alerts.value.forEach((a: Alert) => {
    csv.push(`${a.time || a.createdAt || ''},${a.severity || ''},${a.title || ''},${a.source || ''},${a.status || ''}`)
  })
  const blob = new Blob([csv.join('\n')], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `alerts-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  toast.success('告警历史已导出')
}

const activeFilter = ref('全部来源')
const activeSeverity = ref<string | null>('CRITICAL')
const filters = ['全部来源', 'Docker', 'DB', 'System', 'AI']

const { connected, connect, subscribe, disconnect } = useWebSocket()

const severityCards = computed(() => [
  { key: 'CRITICAL', label: '未决严重告警', value: alertStats.value.critical ?? 0, icon: 'priority_high', iconBg: 'bg-error-container', iconColor: 'text-error', borderClass: '' },
  { key: 'WARNING', label: '系统警告', value: alertStats.value.warning ?? 0, icon: 'warning', iconBg: 'bg-tertiary-fixed', iconColor: 'text-tertiary', borderClass: 'border-l-4 border-tertiary' },
  { key: 'PREDICTIVE', label: '预测告警', value: alertStats.value.predictive ?? 0, icon: 'auto_awesome', iconBg: 'bg-primary-fixed', iconColor: 'text-primary', borderClass: '' },
  { key: 'FIXED', label: '已解决', value: alertStats.value.resolvedCount ?? 0, icon: 'check_circle', iconBg: 'bg-secondary-container', iconColor: 'text-on-secondary-container', borderClass: '' },
])

function toggleSeverityFilter(key: string) {
  activeSeverity.value = activeSeverity.value === key ? null : key
}

const filteredAlerts = computed(() => {
  let result = alerts.value

  // Filter by severity (stat card click)
  if (activeSeverity.value) {
    if (activeSeverity.value === 'FIXED') {
      result = result.filter((a: Alert) => (a.status || '').toUpperCase() === 'RESOLVED')
    } else {
      result = result.filter((a: Alert) => (a.severity || '').toUpperCase() === activeSeverity.value)
    }
  }

  // Filter by source (tab click)
  if (activeFilter.value !== '全部来源') {
    const filterMap: Record<string, string[]> = {
      'Docker': ['docker', 'container'],
      'DB': ['database', 'db', 'mysql', 'postgres', 'redis'],
      'System': ['system', 'cpu', 'memory', 'disk'],
      'AI': ['ai', 'predictive', 'anomaly'],
    }
    const keywords = filterMap[activeFilter.value] || []
    result = result.filter((a: Alert) => {
      const source = (a.source || '').toLowerCase()
      const title = (a.title || '').toLowerCase()
      return keywords.some(k => source.includes(k) || title.includes(k))
    })
  }

  return result
})

import type { Alert, AlertStats } from '@/types'

interface IntegrationDisplay {
  name: string
  type?: string
  desc?: string
  url?: string
  active: boolean
  initial: string | null
  icon: string | null
  iconBg: string
}

interface EventItem {
  time: string
  tag: string
  tagColor: string
  text: string
}

const alertStats = ref<AlertStats>({} as AlertStats)
const alerts = ref<Alert[]>([])
const integrations = ref<IntegrationDisplay[]>([])
const events = ref<EventItem[]>([])
const aiAnalysis = ref('')

const integrationIcons: Record<string, { initial: string | null; icon: string | null; iconBg: string }> = {
  SLACK: { initial: 'S', icon: null, iconBg: 'bg-[#4A154B]' },
  slack: { initial: 'S', icon: null, iconBg: 'bg-[#4A154B]' },
  EMAIL: { initial: null, icon: 'alternate_email', iconBg: 'bg-secondary' },
  email: { initial: null, icon: 'alternate_email', iconBg: 'bg-secondary' },
  WEBHOOK: { initial: null, icon: 'webhook', iconBg: 'bg-on-background' },
  webhook: { initial: null, icon: 'webhook', iconBg: 'bg-on-background' },
}

const severityColors: Record<string, string> = {
  CRITICAL: 'border-error',
  critical: 'border-error',
  WARNING: 'border-tertiary',
  warning: 'border-tertiary',
  INFO: 'border-primary',
  info: 'border-primary',
  PREDICTIVE: 'border-primary',
  predictive: 'border-primary',
}

const severityBadge: Record<string, string> = {
  CRITICAL: 'bg-error/10 text-error',
  critical: 'bg-error/10 text-error',
  WARNING: 'bg-tertiary/10 text-tertiary',
  warning: 'bg-tertiary/10 text-tertiary',
  INFO: 'bg-primary/10 text-primary',
  info: 'bg-primary/10 text-primary',
  PREDICTIVE: 'bg-primary/10 text-primary',
  predictive: 'bg-primary/10 text-primary',
}

onMounted(async () => {
  try {
    const [statsRes, alertsRes, integrationsRes] = await Promise.all([
      alertsApi.getStats(),
      alertsApi.getAll(),
      integrationsApi.getAll(),
    ])
    alertStats.value = statsRes || {}
    alerts.value = alertsRes || []
    const intData = integrationsRes || []
    integrations.value = intData.map((i: Record<string, any>) => {
      const iconCfg = integrationIcons[i.type] || integrationIcons.WEBHOOK
      return { ...i, ...iconCfg, active: i.active !== false }
    })
    // Populate initial events from alerts data
    const alertsData = alertsRes || []
    events.value = alertsData.slice(0, 10).map((a: Alert) => ({
      time: a.time || a.createdAt || '',
      tag: a.severity || 'INFO',
      tagColor: severityBadge[a.severity] || 'text-primary',
      text: a.title || a.message || '',
    }))

    // Connect to WebSocket for real-time events
    connect()
    setTimeout(() => {
      subscribe('/topic/events', (data: Record<string, any>) => {
        if (data.type === 'HEARTBEAT') return
        const newEvent = {
          time: data.createdAt || new Date().toLocaleTimeString('zh-CN'),
          tag: data.level || 'INFO',
          tagColor: severityBadge[data.level] || 'text-primary',
          text: data.message || data.title || '',
        }
        events.value.unshift(newEvent)
        if (events.value.length > 50) events.value.pop()
      })
    }, 1000)

    // Generate AI analysis from alerts
    const criticalCount = alertsData.filter((a: Alert) => a.severity === 'CRITICAL').length
    const warningCount = alertsData.filter((a: Alert) => a.severity === 'WARNING').length
    if (criticalCount > 0 || warningCount > 0) {
      aiAnalysis.value = `在过去 24 小时内，系统检测到 ${criticalCount} 次严重告警和 ${warningCount} 次警告。建议优先处理严重告警以确保系统稳定性。`
    }
  } catch (e) {
    console.error('Failed to load alerts data', e)
  }
})

onUnmounted(() => {
  disconnect()
})
</script>
