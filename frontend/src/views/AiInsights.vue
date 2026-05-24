<template>
  <div class="p-[24px] space-y-[40px]">
    <!-- Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-[32px] font-semibold text-on-surface">AI 洞察</h2>
        <p class="text-on-surface-variant text-[16px] mt-1">基于机器学习的智能分析与预测。</p>
      </div>
      <button @click="handleGenerateReport" class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-2 shadow-sm">
        <span class="material-symbols-outlined text-[18px]">auto_awesome</span> 生成报告
      </button>
    </div>

    <!-- Risk Score Radar & Quick Insights -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-[16px]">
      <!-- Risk Radar -->
      <div class="glass-panel p-[20px] rounded-xl">
        <h3 class="text-[12px] font-bold text-outline uppercase tracking-widest mb-4">风险雷达</h3>
        <div ref="radarRef" class="h-64 w-full"></div>
      </div>

      <!-- Quick Insights -->
      <div class="glass-panel p-[20px] rounded-xl lg:col-span-2">
        <h3 class="text-[12px] font-bold text-outline uppercase tracking-widest mb-6">快速洞察</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div v-for="insight in insights" :key="insight.title"
            @click="showInsightDetail(insight)"
            class="p-4 rounded-xl border-l-4 hover:translate-x-1 transition-transform cursor-pointer"
            :class="insight.borderClass">
            <div class="flex items-center gap-2 mb-2">
              <span class="material-symbols-outlined" :class="insight.iconColor">{{ insight.icon }}</span>
              <span class="text-[12px] font-bold" :class="insight.textColor">{{ insight.category }}</span>
            </div>
            <h4 class="text-[14px] font-bold mb-1">{{ insight.title }}</h4>
            <p class="text-[12px] text-on-surface-variant">{{ insight.description }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Storage Prediction Chart -->
    <div class="glass-panel p-[20px] rounded-xl">
      <div class="flex justify-between items-center mb-6">
        <h3 class="text-[24px] font-semibold">存储增长预测</h3>
        <div class="flex gap-2">
          <span class="flex items-center gap-1 text-[12px] text-on-surface-variant">
            <span class="w-2 h-2 rounded-full bg-primary"></span> 实际使用
          </span>
          <span class="flex items-center gap-1 text-[12px] text-on-surface-variant">
            <span class="w-2 h-2 rounded-full bg-primary/30 border border-dashed border-primary"></span> AI 预测
          </span>
        </div>
      </div>
      <div ref="storageRef" class="h-64 w-full"></div>
    </div>

    <!-- AI Recommendations -->
    <div class="glass-panel p-[20px] rounded-xl">
      <h3 class="text-[24px] font-semibold mb-6 flex items-center gap-2">
        <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">psychology</span>
        AI 优化建议
      </h3>
      <div class="space-y-4">
        <div v-for="rec in recommendations" :key="rec.title"
          class="flex items-start gap-4 p-4 rounded-xl border border-outline-variant/20 hover:bg-surface-container/30 transition-colors">
          <div class="p-2 rounded-lg shrink-0" :class="rec.iconBg">
            <span class="material-symbols-outlined" :class="rec.iconColor">{{ rec.icon }}</span>
          </div>
          <div class="flex-1">
            <h4 class="text-[14px] font-bold mb-1">{{ rec.title }}</h4>
            <p class="text-[12px] text-on-surface-variant">{{ rec.description }}</p>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-[12px] font-bold" :class="rec.impactColor">{{ rec.impact }}</span>
            <button @click="applyRecommendation(rec.id)" class="px-3 py-1.5 text-[10px] font-bold rounded-lg" :class="rec.actionClass">{{ rec.action }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { marked } from 'marked'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { aiApi } from '@/api/ai'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'

const toast = useToastStore()
const modal = useModalStore()

const radarRef = ref<HTMLElement>()
const storageRef = ref<HTMLElement>()
import type { AiInsight, AiRecommendation, RiskRadar, StoragePrediction } from '@/types'

const insights = ref<(AiInsight & { iconColor: string; textColor: string; borderClass: string })[]>([])
const recommendations = ref<(AiRecommendation & { iconBg: string; iconColor: string; impactColor: string; actionClass: string })[]>([])
const radarData = ref<RiskRadar>({} as RiskRadar)
const storagePrediction = ref<StoragePrediction>({} as StoragePrediction)

const insightStyles: Record<string, { iconColor: string; textColor: string; borderClass: string }> = {
  PERFORMANCE: { iconColor: 'text-primary', textColor: 'text-primary', borderClass: 'border-primary/40 bg-primary/5' },
  COST: { iconColor: 'text-tertiary', textColor: 'text-tertiary', borderClass: 'border-tertiary/40 bg-tertiary/5' },
  SECURITY: { iconColor: 'text-green-500', textColor: 'text-green-600', borderClass: 'border-green-500/40 bg-green-500/5' },
  DATABASE: { iconColor: 'text-secondary', textColor: 'text-secondary', borderClass: 'border-secondary/40 bg-secondary/5' },
  REPORT: { iconColor: 'text-primary', textColor: 'text-primary', borderClass: 'border-primary/40 bg-primary/5' },
  SYSTEM: { iconColor: 'text-secondary', textColor: 'text-secondary', borderClass: 'border-secondary/40 bg-secondary/5' },
  BACKUP: { iconColor: 'text-green-500', textColor: 'text-green-600', borderClass: 'border-green-500/40 bg-green-500/5' },
}

const recStyles: Record<string, { iconBg: string; iconColor: string; impactColor: string; actionClass: string }> = {
  COST: { iconBg: 'bg-primary/10', iconColor: 'text-primary', impactColor: 'text-green-600', actionClass: 'bg-primary text-white' },
  PERFORMANCE: { iconBg: 'bg-secondary/10', iconColor: 'text-secondary', impactColor: 'text-primary', actionClass: 'bg-secondary text-white' },
  CLEANUP: { iconBg: 'bg-tertiary/10', iconColor: 'text-tertiary', impactColor: 'text-tertiary', actionClass: 'bg-surface-container-high text-on-surface' },
}

async function handleGenerateReport() {
  try {
    const res = await aiApi.generateReport()
    const reportText = res || ''
    toast.success('AI 分析报告已生成')
    // Show report in modal with markdown rendering
    modal.open({
      component: ConfirmModal,
      title: 'AI 分析报告',
      props: {
        messageHtml: marked(reportText),
        confirmText: '关闭',
        confirmClass: 'bg-surface-container-highest text-on-surface',
      },
    })
    // Refresh insights list
    const insightsRes = await aiApi.getInsights()
    const insightsRaw = insightsRes || []
    insights.value = insightsRaw.map((i: AiInsight) => {
      const style = insightStyles[i.category] || insightStyles.PERFORMANCE
      return { ...i, ...style }
    })
  } catch (e) {
    toast.error('报告生成失败')
  }
}

async function applyRecommendation(id: number) {
  try {
    await aiApi.applyRecommendation(id)
    toast.success('建议已应用')
    recommendations.value = recommendations.value.filter((r: AiRecommendation) => r.id !== id)
  } catch (e) {
    toast.error('应用失败')
  }
}

function showInsightDetail(insight: any) {
  modal.open({
    component: ConfirmModal,
    title: insight.title,
    props: {
      messageHtml: marked(insight.description || ''),
      confirmText: '关闭',
      confirmClass: 'bg-surface-container-highest text-on-surface',
    },
  })
}

onMounted(async () => {
  try {
    const [insightsRes, recsRes, radarRes, storageRes] = await Promise.all([
      aiApi.getInsights(),
      aiApi.getRecommendations(),
      aiApi.getRiskRadar(),
      aiApi.getStoragePrediction(),
    ])
    const insightsRaw = insightsRes || []
    insights.value = insightsRaw.map((i: AiInsight) => {
      const style = insightStyles[i.category] || insightStyles.PERFORMANCE
      return { ...i, ...style }
    })
    const recsRaw = recsRes || []
    recommendations.value = recsRaw.map((r: AiRecommendation) => {
      const style = recStyles[r.category] || recStyles.COST
      return { ...r, ...style, action: r.action || '应用' }
    })
    radarData.value = radarRes || {}
    storagePrediction.value = storageRes || {}
  } catch (e) {
    console.error('Failed to load AI insights', e)
  }

  // Radar Chart
  if (radarRef.value) {
    const radar = echarts.init(radarRef.value)
    const indicators = radarData.value.indicators || []
    const values = radarData.value.values || []
    if (!indicators.length || !values.length) {
      radar.setOption({
        title: { text: '暂无风险雷达数据', left: 'center', top: 'center', textStyle: { color: '#727785', fontSize: 14 } },
      })
    } else {
    radar.setOption({
      radar: {
        indicator: indicators,
        shape: 'circle',
        splitArea: { areaStyle: { color: ['rgba(0,88,190,0.02)', 'rgba(0,88,190,0.04)'] } },
        axisLine: { lineStyle: { color: '#c2c6d6' } },
        splitLine: { lineStyle: { color: '#e1e2ec' } },
        axisName: { color: '#727785', fontSize: 10 },
      },
      series: [{
        type: 'radar',
        data: [{ value: values, name: '风险评分', areaStyle: { color: 'rgba(0, 88, 190, 0.15)' }, lineStyle: { color: '#0058be', width: 2 }, itemStyle: { color: '#0058be' } }],
      }],
      tooltip: { backgroundColor: '#2e3038', textStyle: { color: '#eff0fa', fontSize: 12 } },
    })
    window.addEventListener('resize', () => radar.resize())
    }
  }

  // Storage Prediction Chart
  if (storageRef.value) {
    const storage = echarts.init(storageRef.value)
    const months = storagePrediction.value.months || []
    const actual = storagePrediction.value.actual || []
    const predicted = storagePrediction.value.predicted || []

    if (!months.length) {
      storage.setOption({
        title: { text: '暂无存储预测数据', left: 'center', top: 'center', textStyle: { color: '#727785', fontSize: 14 } },
      })
    } else {
    storage.setOption({
      grid: { top: 20, right: 20, bottom: 40, left: 50 },
      xAxis: { type: 'category', data: months, axisLine: { lineStyle: { color: '#c2c6d6' } }, axisLabel: { color: '#727785', fontSize: 10 } },
      yAxis: { type: 'value', name: 'TB', axisLine: { show: false }, splitLine: { lineStyle: { color: '#e1e2ec', type: 'dashed' } }, axisLabel: { color: '#727785', fontSize: 10 } },
      series: [
        { name: '实际使用', type: 'line', data: actual, smooth: true, lineStyle: { color: '#0058be', width: 2 }, areaStyle: { color: 'rgba(0, 88, 190, 0.1)' }, itemStyle: { color: '#0058be' } },
        { name: 'AI 预测', type: 'line', data: predicted, smooth: true, lineStyle: { color: '#0058be', width: 2, type: 'dashed' }, areaStyle: { color: 'rgba(0, 88, 190, 0.05)' }, itemStyle: { color: '#0058be' } },
      ],
      tooltip: { trigger: 'axis', backgroundColor: '#2e3038', textStyle: { color: '#eff0fa', fontSize: 12 } },
    })
    window.addEventListener('resize', () => storage.resize())
    }
  }
})
</script>
