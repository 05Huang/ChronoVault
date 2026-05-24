<template>
  <div class="p-[24px] space-y-[40px]">
    <!-- Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-[32px] font-semibold text-on-surface">风险中心</h2>
        <p class="text-on-surface-variant text-[16px] mt-1">实时监控基础设施的安全稳定性与数据一致性</p>
      </div>
      <div class="flex gap-3">
        <button @click="exportReport" class="bg-surface-container-high text-on-surface px-4 py-2 rounded-lg text-[12px] font-bold border border-outline-variant/30 hover:bg-surface-container-highest transition-all flex items-center gap-2">
          <span class="material-symbols-outlined text-[18px]">download</span> 导出报告
        </button>
        <button @click="openDeepScan" class="bg-primary text-white px-4 py-2 rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-2 shadow-sm active:scale-95">
          <span class="material-symbols-outlined text-[18px]">shutter_speed</span> 深度扫描
        </button>
      </div>
    </div>

    <!-- Dashboard Grid -->
    <div class="grid grid-cols-1 md:grid-cols-12 gap-[16px]">
      <!-- Global Risk Score Gauge -->
      <div class="md:col-span-4 glass-panel p-[20px] rounded-xl flex flex-col items-center justify-center relative shimmer-edge">
        <h3 class="text-[12px] font-bold text-outline uppercase tracking-widest self-start mb-4">全局风险评分</h3>
        <div class="relative w-48 h-48 flex items-center justify-center">
          <svg class="w-full h-full -rotate-90">
            <circle class="text-surface-container-highest" cx="96" cy="96" fill="transparent" r="80" stroke="currentColor" stroke-width="12"></circle>
            <circle class="text-primary" cx="96" cy="96" fill="transparent" r="80" stroke="currentColor" stroke-dasharray="502.4" :stroke-dashoffset="502.4 - (502.4 * (riskScore.overallScore ?? 0) / 100)" stroke-linecap="round" stroke-width="12"></circle>
          </svg>
          <div class="absolute inset-0 flex flex-col items-center justify-center">
            <span class="text-[48px] font-bold text-on-surface font-[Geist]">{{ riskScore.overallScore ?? 0 }}</span>
            <span class="text-[12px] font-bold text-on-surface-variant">{{ riskScore.level ?? '评估中' }}</span>
          </div>
        </div>
        <div class="mt-6 text-center">
          <p class="text-[14px] text-on-surface-variant">{{ riskScore.summary || '系统风险评估' }}</p>
          <div class="flex gap-1 mt-4 justify-center">
            <div class="h-1.5 w-8 rounded-full bg-secondary"></div>
            <div class="h-1.5 w-8 rounded-full bg-secondary"></div>
            <div class="h-1.5 w-8 rounded-full bg-secondary/30"></div>
            <div class="h-1.5 w-8 rounded-full bg-secondary/30"></div>
            <div class="h-1.5 w-8 rounded-full bg-secondary/30"></div>
          </div>
        </div>
      </div>

      <!-- Historical Risk Trend Chart -->
      <div class="md:col-span-8 glass-panel p-[20px] rounded-xl">
        <div class="flex justify-between items-center mb-6">
          <h3 class="text-[12px] font-bold text-outline uppercase tracking-widest">历史风险趋势 (30天)</h3>
          <div class="flex gap-2">
            <span class="flex items-center gap-1 text-[12px] text-on-surface-variant">
              <span class="w-2 h-2 rounded-full bg-primary"></span> 稳定性
            </span>
            <span class="flex items-center gap-1 text-[12px] text-on-surface-variant">
              <span class="w-2 h-2 rounded-full bg-tertiary"></span> 安全性
            </span>
          </div>
        </div>
        <div ref="chartRef" class="h-48 w-full"></div>
      </div>

      <!-- Infrastructure Health Map -->
      <div class="md:col-span-12 glass-panel p-[20px] rounded-xl overflow-hidden">
        <div class="flex justify-between items-center mb-6">
          <h3 class="text-[12px] font-bold text-outline uppercase tracking-widest">基础设施健康分布图</h3>
          <div class="flex gap-4">
            <div class="flex items-center gap-2"><div class="w-3 h-3 rounded-full bg-secondary"></div> <span class="text-[12px] text-on-surface-variant">运行正常</span></div>
            <div class="flex items-center gap-2"><div class="w-3 h-3 rounded-full bg-tertiary"></div> <span class="text-[12px] text-on-surface-variant">中等风险</span></div>
            <div class="flex items-center gap-2"><div class="w-3 h-3 rounded-full bg-error"></div> <span class="text-[12px] text-on-surface-variant">关键故障</span></div>
          </div>
        </div>
        <div class="grid grid-cols-4 md:grid-cols-10 gap-4">
          <div v-for="node in nodes" :key="node.id"
            @click="$router.push('/servers/' + node.id)"
            class="aspect-square rounded-lg flex items-center justify-center hover:scale-105 transition-transform cursor-pointer relative"
            :class="node.bgClass" :title="node.title">
            <span class="material-symbols-outlined" :class="node.iconColor">dns</span>
            <div v-if="node.pulse" class="absolute -top-1 -right-1 w-3 h-3 rounded-full animate-pulse" :class="node.pulseColor"></div>
            <div v-if="node.ping" class="absolute -top-1 -right-1 w-3 h-3 rounded-full animate-ping" :class="node.pingColor"></div>
          </div>
        </div>
      </div>

      <!-- Identified Risks & AI Mitigation -->
      <div class="md:col-span-12 space-y-4">
        <h3 class="text-[24px] font-semibold text-on-surface">待处理风险及 AI 缓解方案</h3>
        <div v-for="risk in risks" :key="risk.title"
          class="glass-panel rounded-xl p-[20px] flex flex-col md:flex-row gap-6"
          :class="risk.borderClass">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-2">
              <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase" :class="risk.badgeClass">{{ risk.level }}</span>
              <h4 class="text-[18px] font-bold text-on-surface">{{ risk.title }}</h4>
            </div>
            <p class="text-[14px] text-on-surface-variant">{{ risk.description }}</p>
            <div class="mt-4 flex gap-4 text-[12px] text-outline">
              <span class="flex items-center gap-1"><span class="material-symbols-outlined text-[16px]">schedule</span> {{ risk.discoveredAt || '' }}</span>
              <span class="flex items-center gap-1"><span class="material-symbols-outlined text-[16px]">tag</span> {{ risk.category }}</span>
            </div>
          </div>
          <div class="md:w-1/3 ai-glow bg-primary/5 rounded-lg p-4 flex flex-col justify-between">
            <div>
              <div class="flex items-center gap-2 mb-2">
                <span class="material-symbols-outlined text-primary text-[20px]">psychology</span>
                <span class="text-[12px] font-bold text-primary">AI 缓解建议</span>
              </div>
              <p class="text-[14px] italic text-on-surface-variant">{{ risk.aiSuggestion }}</p>
            </div>
            <button @click="openRiskAction(risk)" class="mt-4 w-full py-2 rounded-lg text-[12px] font-bold active:scale-95 transition-all"
              :class="risk.actionClass">
              {{ risk.actionText }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { riskApi } from '@/api/risk'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'

const toast = useToastStore()
const modal = useModalStore()

import type { RiskScore, RiskNode, Risk, RiskTrendPoint } from '@/types'

interface RiskNodeDisplay extends RiskNode {
  bgClass: string
  iconColor: string
  pulse?: boolean
  pulseColor?: string
  ping?: boolean
  pingColor?: string
  title: string
}

interface RiskDisplay extends Risk {
  borderClass: string
  badgeClass: string
  actionClass: string
  actionText?: string
}

interface TrendData {
  days: string[]
  stability: number[]
  security: number[]
}

const riskScore = ref<RiskScore>({} as RiskScore)
const nodes = ref<RiskNodeDisplay[]>([])
const risks = ref<RiskDisplay[]>([])
const trendData = ref<TrendData>({} as TrendData)
const chartRef = ref<HTMLElement>()

function openDeepScan() {
  modal.open({
    component: ConfirmModal,
    title: '深度扫描',
    props: {
      message: '将对所有基础设施节点进行深度安全与稳定性扫描，预计耗时 2 分钟。扫描期间不影响正常服务。是否开始？',
      confirmText: '开始扫描',
      successMessage: '深度扫描已启动，预计 2 分钟完成',
      onConfirm: async () => { await riskApi.scan() },
    },
  })
}

function openRiskAction(risk: RiskDisplay) {
  const isCritical = risk.level === 'CRITICAL' || risk.level === 'Critical'
  modal.open({
    component: ConfirmModal,
    title: risk.actionText || '执行操作',
    props: {
      message: `即将对风险「${risk.title}」执行操作。${isCritical ? '此为高风险操作，请确认已了解潜在影响。' : ''}是否继续？`,
      confirmText: risk.actionText || '确认',
      confirmClass: isCritical ? 'bg-error hover:bg-error/90' : undefined,
      successMessage: `${risk.actionText || '操作'} 已执行`,
      onConfirm: async () => { await riskApi.mitigate(risk.id) },
    },
  })
}

function exportReport() {
  const csv = ['级别,标题,描述,来源,状态,发现时间']
  risks.value.forEach((r: RiskDisplay) => {
    csv.push(`${r.level || ''},${r.title || ''},${(r.description || '').replace(/,/g, '，')},${r.category || ''},${r.status || ''},${r.discoveredAt || ''}`)
  })
  const blob = new Blob([csv.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `risk-report-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  toast.success('风险报告已导出')
}

const nodeHealthMap: Record<string, { bgClass: string; iconColor: string; pulse?: boolean; pulseColor?: string; ping?: boolean; pingColor?: string }> = {
  HEALTHY: { bgClass: 'bg-secondary/10 border border-secondary/30', iconColor: 'text-secondary' },
  healthy: { bgClass: 'bg-secondary/10 border border-secondary/30', iconColor: 'text-secondary' },
  WARNING: { bgClass: 'bg-tertiary/10 border border-tertiary/30', iconColor: 'text-tertiary', pulse: true, pulseColor: 'bg-tertiary' },
  warning: { bgClass: 'bg-tertiary/10 border border-tertiary/30', iconColor: 'text-tertiary', pulse: true, pulseColor: 'bg-tertiary' },
  CRITICAL: { bgClass: 'bg-error/10 border border-error/30', iconColor: 'text-error', ping: true, pingColor: 'bg-error' },
  critical: { bgClass: 'bg-error/10 border border-error/30', iconColor: 'text-error', ping: true, pingColor: 'bg-error' },
}

const riskLevelMap: Record<string, { borderClass: string; badgeClass: string; actionClass: string }> = {
  CRITICAL: { borderClass: 'border-l-4 border-error/60', badgeClass: 'bg-error-container text-on-error-container', actionClass: 'bg-primary text-white hover:bg-primary-container' },
  Critical: { borderClass: 'border-l-4 border-error/60', badgeClass: 'bg-error-container text-on-error-container', actionClass: 'bg-primary text-white hover:bg-primary-container' },
  WARNING: { borderClass: 'border-l-4 border-tertiary/60', badgeClass: 'bg-tertiary-container text-on-tertiary-container', actionClass: 'bg-primary-container text-on-primary-container hover:bg-primary-container/80' },
  Warning: { borderClass: 'border-l-4 border-tertiary/60', badgeClass: 'bg-tertiary-container text-on-tertiary-container', actionClass: 'bg-primary-container text-on-primary-container hover:bg-primary-container/80' },
  ANOMALOUS: { borderClass: 'border-l-4 border-tertiary/60', badgeClass: 'bg-tertiary-container text-on-tertiary-container', actionClass: 'border border-primary text-primary hover:bg-primary/5' },
  Anomalous: { borderClass: 'border-l-4 border-tertiary/60', badgeClass: 'bg-tertiary-container text-on-tertiary-container', actionClass: 'border border-primary text-primary hover:bg-primary/5' },
}

onMounted(async () => {
  try {
    const [scoreRes, trendRes, nodesRes, risksRes] = await Promise.all([
      riskApi.getScore(),
      riskApi.getTrend(),
      riskApi.getNodes(),
      riskApi.getRisks(),
    ])
    riskScore.value = scoreRes || {}
    const trendList: RiskTrendPoint[] = trendRes || []
    trendData.value = {
      days: trendList.map((t) => t.date),
      stability: trendList.map((t) => t.stability),
      security: trendList.map((t) => t.security),
    }
    const nodesData: RiskNode[] = nodesRes || []
    nodes.value = nodesData.map((n) => {
      const health = nodeHealthMap[n.status] || nodeHealthMap.HEALTHY
      return { ...n, ...health, title: `${n.name}: ${n.status}` }
    })
    const risksData: Risk[] = risksRes || []
    risks.value = risksData.map((r) => {
      const levelCfg = riskLevelMap[r.level] || riskLevelMap.WARNING
      return { ...r, ...levelCfg }
    })
  } catch (e) {
    console.error('Failed to load risk data', e)
  }

  if (chartRef.value) {
    const chart = echarts.init(chartRef.value)
    const days = trendData.value.days || []
    const stability = trendData.value.stability || []
    const security = trendData.value.security || []

    chart.setOption({
      grid: { top: 10, right: 10, bottom: 30, left: 40 },
      xAxis: {
        type: 'category',
        data: days,
        axisLine: { lineStyle: { color: '#c2c6d6' } },
        axisLabel: { color: '#727785', fontSize: 10, interval: 4 },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: '#e1e2ec', type: 'dashed' } },
        axisLabel: { color: '#727785', fontSize: 10 },
      },
      series: [
        { name: '稳定性', type: 'line', data: stability, smooth: true, lineStyle: { color: '#0058be', width: 2 }, areaStyle: { color: 'rgba(0, 88, 190, 0.1)' }, showSymbol: false },
        { name: '安全性', type: 'line', data: security, smooth: true, lineStyle: { color: '#924700', width: 2 }, areaStyle: { color: 'rgba(146, 71, 0, 0.05)' }, showSymbol: false },
      ],
      tooltip: { trigger: 'axis', backgroundColor: '#2e3038', textStyle: { color: '#eff0fa', fontSize: 12 } },
    })
    window.addEventListener('resize', () => chart.resize())
  }
})
</script>
