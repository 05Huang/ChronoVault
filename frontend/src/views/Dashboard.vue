<template>
  <div class="p-[24px] space-y-[40px] pb-20">
    <!-- Getting Started Banner -->
    <section v-if="!loading && (!stats.activeServers || stats.activeServers === 0)" class="glass-panel rounded-xl p-6 border-primary/20 bg-gradient-to-r from-primary/5 to-secondary/5">
      <div class="flex items-start gap-4">
        <div class="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center shrink-0">
          <span class="material-symbols-outlined text-primary text-[24px]">explore</span>
        </div>
        <div class="flex-1">
          <h2 class="text-[18px] font-semibold text-on-surface mb-1 font-[Geist]">快速开始使用 ChronoVault</h2>
          <p class="text-[14px] text-on-surface-variant mb-4">按照以下步骤开始保护您的服务器：</p>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <router-link to="/onboarding" class="flex items-center gap-3 p-3 rounded-lg bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors">
              <span class="material-symbols-outlined text-primary">dns</span>
              <div>
                <p class="text-[13px] font-bold text-on-surface">1. 添加服务器</p>
                <p class="text-[11px] text-on-surface-variant">注册您的第一台服务器</p>
              </div>
            </router-link>
            <router-link to="/storage" class="flex items-center gap-3 p-3 rounded-lg bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors">
              <span class="material-symbols-outlined text-primary">cloud_upload</span>
              <div>
                <p class="text-[13px] font-bold text-on-surface">2. 配置存储</p>
                <p class="text-[11px] text-on-surface-variant">添加 S3/本地存储目标</p>
              </div>
            </router-link>
            <router-link to="/snapshots" class="flex items-center gap-3 p-3 rounded-lg bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors">
              <span class="material-symbols-outlined text-primary">photo_camera</span>
              <div>
                <p class="text-[13px] font-bold text-on-surface">3. 创建快照</p>
                <p class="text-[11px] text-on-surface-variant">首次备份服务器状态</p>
              </div>
            </router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- Key Stats Grid -->
    <section class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-[16px]">
      <div class="glass-panel p-[20px] rounded-xl hover:shadow-lg transition-all group border-b-2 border-b-transparent hover:border-b-primary">
        <div class="flex justify-between items-start mb-3">
          <div class="p-2 bg-primary/5 rounded-lg text-primary">
            <span class="material-symbols-outlined">dns</span>
          </div>
        </div>
        <div v-if="loading" class="h-12 w-20 bg-surface-container-highest rounded-lg animate-pulse mb-1"></div>
        <div v-else class="text-[48px] font-bold leading-none mb-1 font-[Geist]">{{ stats.activeServers ?? 0 }}</div>
        <div class="text-[14px] text-outline">在线服务器 (Online)</div>
      </div>
      <div class="glass-panel p-[20px] rounded-xl hover:shadow-lg transition-all border-b-2 border-b-transparent hover:border-b-secondary">
        <div class="flex justify-between items-start mb-3">
          <div class="p-2 bg-secondary/5 rounded-lg text-secondary">
            <span class="material-symbols-outlined">layers</span>
          </div>
        </div>
        <div v-if="loading" class="h-12 w-20 bg-surface-container-highest rounded-lg animate-pulse mb-1"></div>
        <div v-else class="text-[48px] font-bold leading-none mb-1 font-[Geist]">{{ stats.totalContainers ?? 0 }}</div>
        <div class="text-[14px] text-outline">活跃容器 (Active)</div>
      </div>
      <div class="glass-panel p-[20px] rounded-xl hover:shadow-lg transition-all border-b-2 border-b-transparent hover:border-b-tertiary">
        <div class="flex justify-between items-start mb-3">
          <div class="p-2 bg-tertiary/5 rounded-lg text-tertiary">
            <span class="material-symbols-outlined">cloud_upload</span>
          </div>
        </div>
        <div v-if="loading" class="h-12 w-20 bg-surface-container-highest rounded-lg animate-pulse mb-1"></div>
        <div v-else class="text-[48px] font-bold leading-none mb-1 font-[Geist]">{{ stats.todayBackups ?? 0 }}</div>
        <div class="text-[14px] text-outline">今日备份数 (Backups)</div>
      </div>
      <div class="glass-panel p-[20px] rounded-xl hover:shadow-lg transition-all border-b-2 border-b-transparent hover:border-b-primary">
        <div class="flex justify-between items-start mb-3">
          <div class="p-2 bg-primary/5 rounded-lg text-primary">
            <span class="material-symbols-outlined">verified</span>
          </div>
          <div class="flex gap-0.5">
            <div class="w-1 h-3 bg-primary rounded-full"></div>
            <div class="w-1 h-3 bg-primary rounded-full"></div>
            <div class="w-1 h-3 bg-primary rounded-full"></div>
          </div>
        </div>
        <div v-if="loading" class="h-12 w-20 bg-surface-container-highest rounded-lg animate-pulse mb-1"></div>
        <div v-else class="text-[48px] font-bold leading-none mb-1 font-[Geist]">{{ stats.recoveryRate ?? '-' }}</div>
        <div class="text-[14px] text-outline">恢复成功率 (Recovery)</div>
      </div>
    </section>

    <!-- Secondary Row -->
    <section class="grid grid-cols-1 lg:grid-cols-3 gap-[16px]">
      <!-- Storage -->
      <div class="glass-panel p-[20px] rounded-xl col-span-1 flex flex-col">
        <div class="flex justify-between items-center mb-6">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">storage</span>
            存储占用
          </h3>
          <span class="text-[12px] font-bold text-outline">{{ storagePercent }}% Used</span>
        </div>
        <div class="flex-1 flex flex-col justify-center gap-4">
          <div class="relative pt-1">
            <div class="flex mb-2 items-center justify-between">
              <span class="text-xs font-semibold inline-block py-1 px-2 uppercase rounded-full text-primary bg-primary/10">{{ storageUsedFormatted }}</span>
              <span class="text-xs font-semibold inline-block text-primary">{{ storageTotalFormatted }} Total</span>
            </div>
            <div class="overflow-hidden h-3 mb-4 text-xs flex rounded-full bg-surface-container-highest">
              <div class="shadow-none flex flex-col text-center whitespace-nowrap text-white justify-center bg-primary" :style="{ width: storagePercent + '%' }"></div>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="bg-surface-container/50 p-3 rounded-lg border border-outline-variant/20">
              <p class="text-[10px] text-outline uppercase font-bold mb-1">块存储</p>
              <p class="text-[24px] font-semibold">{{ blockStorageFormatted }}</p>
            </div>
            <div class="bg-surface-container/50 p-3 rounded-lg border border-outline-variant/20">
              <p class="text-[10px] text-outline uppercase font-bold mb-1">冷归档</p>
              <p class="text-[24px] font-semibold">{{ coldStorageFormatted }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- AI Risk Score -->
      <div class="glass-panel p-[20px] rounded-xl col-span-1 shimmer-edge overflow-hidden">
        <div class="flex justify-between items-center mb-4">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">security</span>
            AI 风险评分
          </h3>
        </div>
        <div class="flex flex-col items-center justify-center py-4">
          <div v-if="loading" class="w-40 h-40 rounded-full bg-surface-container-highest animate-pulse"></div>
          <div v-else class="relative w-40 h-40 flex items-center justify-center">
            <svg class="w-full h-full -rotate-90">
              <circle class="text-surface-container-highest" cx="80" cy="80" fill="transparent" r="70" stroke="currentColor" stroke-width="12"></circle>
              <circle class="text-primary" cx="80" cy="80" fill="transparent" r="70" stroke="currentColor" :stroke-dasharray="440" :stroke-dashoffset="440 - (440 * (riskScore.overallScore ?? 0) / 100)" stroke-linecap="round" stroke-width="12"></circle>
            </svg>
            <div class="absolute inset-0 flex flex-col items-center justify-center">
              <span class="text-[44px] font-black leading-none text-primary">{{ riskScore.overallScore ?? 0 }}</span>
              <span class="text-[12px] text-outline font-bold">{{ riskScore.level ?? '评估中' }}</span>
            </div>
          </div>
          <p class="mt-4 text-[14px] text-center px-4 text-on-surface-variant">
            {{ riskScore.summary || '正在评估系统安全状态...' }}
          </p>
        </div>
      </div>

      <!-- AI Suggestions -->
      <div class="bg-gradient-to-br from-primary/5 to-secondary/5 border border-primary/20 p-[20px] rounded-xl col-span-1 shadow-sm relative overflow-hidden">
        <div class="absolute top-0 right-0 p-4 opacity-10">
          <span class="material-symbols-outlined text-[80px]" style="font-variation-settings: 'FILL' 1;">auto_awesome</span>
        </div>
        <h3 class="text-[24px] font-semibold flex items-center gap-2 mb-4">
          <span class="material-symbols-outlined text-primary">psychology</span>
          AI 深度洞察
        </h3>
        <div class="space-y-3">
          <div v-for="rec in aiRecommendations" :key="rec.title" class="glass-panel p-3 rounded-lg border-l-4 border-l-primary flex gap-3">
            <span class="material-symbols-outlined text-primary shrink-0">tips_and_updates</span>
            <p class="text-[14px] leading-relaxed">
              <span class="font-bold">{{ rec.title }}</span> {{ rec.desc }}
            </p>
          </div>
          <div v-if="!aiRecommendations.length" class="glass-panel p-3 rounded-lg border-l-4 border-l-outline-variant flex gap-3">
            <span class="material-symbols-outlined text-outline shrink-0">check_circle</span>
            <p class="text-[14px] leading-relaxed">系统运行良好，暂无优化建议。</p>
          </div>
          <button @click="openApplyOptimizations" class="w-full mt-2 py-2 bg-primary text-white rounded-lg font-bold text-[12px] hover:shadow-lg active:scale-95 transition-all">
            应用所有优化建议
          </button>
        </div>
      </div>
    </section>

    <!-- Charts & Anomalies -->
    <section class="grid grid-cols-1 xl:grid-cols-3 gap-[16px]">
      <!-- Activity Chart -->
      <div class="glass-panel p-[20px] rounded-xl xl:col-span-2">
        <div class="flex justify-between items-center mb-6">
          <h3 class="text-[24px] font-semibold">动态趋势图 (Activity Trend)</h3>
          <div class="flex gap-2">
            <button @click="timeRange = '24H'" :class="timeRange === '24H' ? 'bg-surface-container-high text-on-surface' : 'text-outline'" class="px-3 py-1 rounded-full text-[12px] font-bold transition-all">24H</button>
            <button @click="timeRange = '7D'" :class="timeRange === '7D' ? 'bg-surface-container-high text-on-surface' : 'text-outline'" class="px-3 py-1 rounded-full text-[12px] font-bold transition-all hover:bg-surface-container-high">7D</button>
          </div>
        </div>
        <div ref="chartRef" class="h-64 w-full"></div>
      </div>

      <!-- Anomalies -->
      <div class="glass-panel p-[20px] rounded-xl xl:col-span-1">
        <h3 class="text-[24px] font-semibold mb-4 flex items-center gap-2">
          <span class="material-symbols-outlined text-error">warning</span>
          最近异常 (Anomalies)
        </h3>
        <div class="space-y-4">
          <div v-for="anomaly in anomalies" :key="anomaly.title" @click="$router.push('/alerts')" class="flex items-center gap-4 p-3 hover:bg-error/5 rounded-lg transition-colors border border-transparent hover:border-error/20 cursor-pointer">
            <div class="w-10 h-10 rounded-full bg-error/10 flex items-center justify-center text-error shrink-0">
              <span class="material-symbols-outlined">{{ anomaly.icon }}</span>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-[14px] font-bold truncate">{{ anomaly.title }}</p>
              <p class="text-[12px] text-outline">{{ anomaly.time }}</p>
            </div>
            <span class="material-symbols-outlined text-outline">chevron_right</span>
          </div>
        </div>
        <button @click="$router.push('/alerts')" class="w-full mt-6 py-2 border border-outline-variant/30 text-outline rounded-lg font-bold text-[12px] hover:bg-surface-container-high transition-all">
          查看完整日志
        </button>
      </div>
    </section>

    <!-- Topology -->
    <section class="glass-panel p-[20px] rounded-xl overflow-hidden min-h-[400px] flex flex-col relative">
      <div class="flex justify-between items-center mb-8">
        <div>
          <h3 class="text-[24px] font-semibold">服务关系拓扑图 (Topology)</h3>
          <p class="text-[14px] text-outline">基于服务器和容器的实时拓扑</p>
        </div>
        <div class="flex gap-4">
          <div class="flex items-center gap-2">
            <span class="w-3 h-3 rounded-full bg-secondary"></span>
            <span class="text-[12px]">运行中</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="w-3 h-3 rounded-full bg-error"></span>
            <span class="text-[12px]">异常</span>
          </div>
        </div>
      </div>
      <div class="flex-1 flex items-center justify-center">
        <div v-if="topologyNodes.length" class="flex flex-wrap gap-6 justify-center">
          <div v-for="node in topologyNodes" :key="node.id"
            @click="$router.push('/servers/' + node.id)"
            class="w-32 h-32 glass-panel rounded-2xl flex flex-col items-center justify-center p-4 border-2 cursor-pointer hover:scale-105 transition-transform"
            :class="node.status === 'RUNNING' ? 'border-secondary shadow-secondary/10' : 'border-error shadow-error/10'">
            <span class="material-symbols-outlined text-3xl mb-2" :class="node.status === 'RUNNING' ? 'text-secondary' : 'text-error'" style="font-variation-settings: 'FILL' 1;">dns</span>
            <span class="text-[12px] font-bold text-center truncate w-full">{{ node.name }}</span>
            <span class="text-[10px] text-outline">{{ node.ip }}</span>
          </div>
        </div>
        <div v-else class="text-center">
          <span class="material-symbols-outlined text-outline text-[48px] mb-2">hub</span>
          <p class="text-on-surface-variant">暂无服务器数据</p>
        </div>
      </div>
      <div class="absolute bottom-4 left-4 right-4 flex justify-between text-[12px] font-bold text-outline bg-surface-container/30 py-2 px-4 rounded-lg">
        <span>在线服务器: {{ topologyNodes.filter(n => n.status === 'RUNNING').length }} / {{ topologyNodes.length }}</span>
        <span>{{ new Date().toLocaleString('zh-CN') }}</span>
      </div>
    </section>

    <!-- FAB -->
    <button @click="openNewBackup" class="fixed bottom-8 right-8 w-14 h-14 bg-primary text-white rounded-full shadow-2xl flex items-center justify-center transition-all hover:scale-110 active:scale-95 group z-50">
      <span class="material-symbols-outlined text-2xl group-hover:rotate-90 transition-transform">add</span>
      <div class="absolute right-16 px-3 py-2 bg-inverse-surface text-inverse-on-surface rounded-lg text-[12px] opacity-0 group-hover:opacity-100 pointer-events-none whitespace-nowrap transition-opacity">
        发起新备份任务
      </div>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import * as echarts from 'echarts'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { dashboardApi } from '@/api/dashboard'
import { aiApi } from '@/api/ai'
import { serversApi } from '@/api/servers'
import NewBackupModal from '@/components/modals/NewBackupModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'

const toast = useToastStore()
const modal = useModalStore()

function openNewBackup() {
  modal.open({ component: NewBackupModal, title: '发起新备份任务' })
}

function openApplyOptimizations() {
  const recs = aiRecommendations.value
  const recNames = recs.map((r: any) => r.title).join('、')
  modal.open({
    component: ConfirmModal,
    title: '应用优化建议',
    props: {
      message: recNames
        ? `将应用以下 AI 推荐的优化建议：${recNames}。是否继续？`
        : '当前没有可应用的优化建议。',
      confirmText: '应用所有建议',
      successMessage: '已应用所有优化建议',
      onConfirm: recs.length ? async () => {
        for (const rec of recs) {
          if (rec.id) await aiApi.applyRecommendation(rec.id)
        }
      } : undefined,
    },
  })
}
const chartRef = ref<HTMLElement>()
const timeRange = ref('24H')
const loading = ref(true)
const stats = ref<any>({})
const anomalies = ref<any[]>([])
const riskScore = ref<any>({})
const storageSummary = ref<any[]>([])
const activityTrend = ref<any[]>([])
const aiRecommendations = ref<any[]>([])
const topologyNodes = ref<any[]>([])

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 2 ? 1 : 0) + ' ' + units[i]
}

const storageUsedFormatted = computed(() => {
  const s = storageSummary.value
  if (!s || !s.length) return '-'
  const total = s.reduce((acc: number, item: any) => acc + (item.usedBytes || 0), 0)
  return formatBytes(total)
})

const storageTotalFormatted = computed(() => {
  const s = storageSummary.value
  if (!s || !s.length) return '-'
  const total = s.reduce((acc: number, item: any) => acc + (item.totalBytes || 0), 0)
  return formatBytes(total)
})

const storagePercent = computed(() => {
  const s = storageSummary.value
  if (!s || !s.length) return 0
  const used = s.reduce((acc: number, item: any) => acc + (item.usedBytes || 0), 0)
  const total = s.reduce((acc: number, item: any) => acc + (item.totalBytes || 0), 0)
  return total > 0 ? Math.round((used / total) * 100) : 0
})

const blockStorageFormatted = computed(() => {
  const s = storageSummary.value
  if (!s || !s.length) return '-'
  const block = s.find((item: any) => item.type === 'BLOCK' || item.type === 'block')
  return block ? formatBytes(block.usedBytes) : '-'
})

const coldStorageFormatted = computed(() => {
  const s = storageSummary.value
  if (!s || !s.length) return '-'
  const cold = s.find((item: any) => item.type === 'COLD' || item.type === 'cold' || item.type === 'ARCHIVE')
  return cold ? formatBytes(cold.usedBytes) : '-'
})

onMounted(async () => {
  try {
    const [statsRes, anomaliesRes, riskRes, storageRes, trendRes, recsRes, serversRes] = await Promise.all([
      dashboardApi.getStats(),
      dashboardApi.getAnomalies(),
      dashboardApi.getRiskScore(),
      dashboardApi.getStorageSummary(),
      dashboardApi.getActivityTrend(),
      aiApi.getRecommendations(),
      serversApi.getAll().catch(() => ({ data: [] })),
    ])
    stats.value = (statsRes as any).data || {}
    anomalies.value = (anomaliesRes as any).data || []
    riskScore.value = (riskRes as any).data || {}
    storageSummary.value = (storageRes as any).data || []
    activityTrend.value = (trendRes as any).data || []
    aiRecommendations.value = ((recsRes as any).data || recsRes || []).slice(0, 2)
    topologyNodes.value = (serversRes as any).data || serversRes || []
  } catch (e) {
    console.error('Failed to load dashboard data', e)
  } finally {
    loading.value = false
  }

  if (chartRef.value) {
    const chart = echarts.init(chartRef.value)
    const xLabels = activityTrend.value.map((d: any) => d.label) || []
    const yData = activityTrend.value.map((d: any) => d.snapshots) || []
    chart.setOption({
      grid: { top: 20, right: 20, bottom: 40, left: 50 },
      xAxis: {
        type: 'category',
        data: xLabels,
        axisLine: { lineStyle: { color: '#c2c6d6' } },
        axisLabel: { color: '#727785', fontSize: 10 },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: '#e1e2ec', type: 'dashed' } },
        axisLabel: { color: '#727785', fontSize: 10 },
      },
      series: [{
        type: 'bar',
        data: yData,
        itemStyle: {
          color: (params: any) => {
            const val = params.value
            if (val > 50) return 'rgba(0, 88, 190, 0.6)'
            if (val > 35) return 'rgba(0, 88, 190, 0.4)'
            return 'rgba(0, 88, 190, 0.2)'
          },
          borderRadius: [4, 4, 0, 0],
        },
      }],
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#2e3038',
        textStyle: { color: '#eff0fa', fontSize: 12 },
      },
    })
    window.addEventListener('resize', () => chart.resize())
  }
})
</script>
