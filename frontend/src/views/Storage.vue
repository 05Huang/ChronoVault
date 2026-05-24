<template>
  <div class="p-[24px] space-y-[40px]">
    <!-- Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-[32px] font-semibold text-on-surface">存储管理</h2>
        <p class="text-on-surface-variant text-[16px] mt-1">监控和管理所有存储资源的分配与使用。</p>
      </div>
      <div class="flex gap-3">
        <button @click="exportReport" class="px-4 py-2 bg-surface-container-high text-on-surface rounded-lg text-[12px] font-bold border border-outline-variant/30 hover:bg-surface-container-highest transition-all flex items-center gap-2">
          <span class="material-symbols-outlined text-[18px]">download</span> 导出报告
        </button>
        <button @click="openAddStorage" class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-2 shadow-sm">
          <span class="material-symbols-outlined text-[18px]">add</span> 添加存储
        </button>
      </div>
    </div>

    <!-- Storage Overview Cards -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-[16px]">
      <div v-for="card in storageCards" :key="card.label" class="glass-panel p-[20px] rounded-xl">
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2 rounded-lg" :class="card.iconBg">
            <span class="material-symbols-outlined" :class="card.iconColor">{{ card.icon }}</span>
          </div>
          <span class="text-[12px] font-bold text-outline uppercase">{{ card.label }}</span>
        </div>
        <div class="text-[48px] font-bold font-[Geist] mb-1">{{ card.used }}</div>
        <div class="text-[14px] text-outline mb-4">已使用 / {{ card.total }} 总计</div>
        <div class="h-2 bg-surface-container-highest rounded-full overflow-hidden">
          <div class="h-full rounded-full" :class="card.barColor" :style="{ width: card.percent + '%' }"></div>
        </div>
      </div>
      <div v-if="!storageCards.length" class="col-span-3 glass-panel p-[20px] rounded-xl text-center py-12">
        <span class="material-symbols-outlined text-outline text-[48px] mb-2">storage</span>
        <p class="text-on-surface-variant">暂无存储数据，请先添加存储目标</p>
      </div>
    </div>

    <!-- Storage Distribution & Health -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-[16px]">
      <!-- Distribution Chart Placeholder -->
      <div class="glass-panel p-[20px] rounded-xl">
        <h3 class="text-[24px] font-semibold mb-6">存储分布</h3>
        <div class="space-y-4">
          <div v-for="item in distribution" :key="item.name" class="flex items-center gap-4">
            <div class="w-3 h-3 rounded-full" :class="item.color"></div>
            <span class="text-[14px] flex-1">{{ item.name }}</span>
            <span class="text-[14px] font-bold">{{ item.size }}</span>
            <span class="text-[12px] text-outline">{{ item.percent }}%</span>
          </div>
        </div>
      </div>

      <!-- Health Check -->
      <div class="glass-panel p-[20px] rounded-xl">
        <h3 class="text-[24px] font-semibold mb-6">健康检查</h3>
        <div class="space-y-4">
          <div v-for="check in healthChecks" :key="check.name" class="flex items-center justify-between p-3 rounded-lg hover:bg-surface-container/50 transition-colors">
            <div class="flex items-center gap-3">
              <span class="material-symbols-outlined" :class="check.iconColor">{{ check.icon }}</span>
              <div>
                <p class="text-[14px] font-bold">{{ check.name }}</p>
                <p class="text-[12px] text-outline">{{ check.desc }}</p>
              </div>
            </div>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold" :class="check.badgeClass">{{ check.status }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { storageApi } from '@/api/storage'
import { formatBytes } from '@/utils/format'
import AddStorageModal from '@/components/modals/AddStorageModal.vue'

const toast = useToastStore()
const modal = useModalStore()

function openAddStorage() {
  modal.open({ component: AddStorageModal, title: '添加存储' })
}

function exportReport() {
  const csv = ['类型,已使用,总计,使用率']
  storageCards.value.forEach((card: any) => {
    csv.push(`${card.label},${card.used},${card.total},${card.percent}%`)
  })
  const blob = new Blob([csv.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `storage-report-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  toast.success('存储报告已导出')
}

import type { StorageOverview, StorageDistribution, StorageHealthCheck } from '@/types'

const overview = ref<StorageOverview[]>([])
const distribution = ref<(StorageDistribution & { size: string; color: string })[]>([])
const healthChecks = ref<(StorageHealthCheck & { icon: string; iconColor: string; badgeClass: string })[]>([])

const typeConfig: Record<string, { label: string; icon: string; iconBg: string; iconColor: string; barColor: string }> = {
  BLOCK: { label: '块存储', icon: 'hard_drive', iconBg: 'bg-primary/10', iconColor: 'text-primary', barColor: 'bg-primary' },
  block: { label: '块存储', icon: 'hard_drive', iconBg: 'bg-primary/10', iconColor: 'text-primary', barColor: 'bg-primary' },
  OBJECT: { label: '对象存储', icon: 'cloud_queue', iconBg: 'bg-secondary/10', iconColor: 'text-secondary', barColor: 'bg-secondary' },
  object: { label: '对象存储', icon: 'cloud_queue', iconBg: 'bg-secondary/10', iconColor: 'text-secondary', barColor: 'bg-secondary' },
  COLD: { label: '冷归档', icon: 'archive', iconBg: 'bg-tertiary/10', iconColor: 'text-tertiary', barColor: 'bg-tertiary' },
  cold: { label: '冷归档', icon: 'archive', iconBg: 'bg-tertiary/10', iconColor: 'text-tertiary', barColor: 'bg-tertiary' },
  ARCHIVE: { label: '冷归档', icon: 'archive', iconBg: 'bg-tertiary/10', iconColor: 'text-tertiary', barColor: 'bg-tertiary' },
}

const storageCards = computed(() => {
  if (!overview.value.length) {
    return []
  }
  return overview.value.map((item: any) => {
    const cfg = typeConfig[item.type] || typeConfig.BLOCK
    const used = item.usedBytes || 0
    const total = item.totalBytes || 1
    return {
      ...cfg,
      used: formatBytes(used),
      total: formatBytes(total),
      percent: Math.round((used / total) * 100),
    }
  })
})

const distColors = ['bg-primary', 'bg-secondary', 'bg-tertiary', 'bg-outline', 'bg-surface-container-highest']

const healthIconMap: Record<string, { icon: string; iconColor: string; badgeClass: string }> = {
  HEALTHY: { icon: 'check_circle', iconColor: 'text-green-500', badgeClass: 'bg-green-500/10 text-green-600' },
  DEGRADED: { icon: 'schedule', iconColor: 'text-tertiary', badgeClass: 'bg-tertiary/10 text-tertiary' },
  CRITICAL: { icon: 'error', iconColor: 'text-error', badgeClass: 'bg-error/10 text-error' },
  healthy: { icon: 'check_circle', iconColor: 'text-green-500', badgeClass: 'bg-green-500/10 text-green-600' },
  degraded: { icon: 'schedule', iconColor: 'text-tertiary', badgeClass: 'bg-tertiary/10 text-tertiary' },
  critical: { icon: 'error', iconColor: 'text-error', badgeClass: 'bg-error/10 text-error' },
}

onMounted(async () => {
  try {
    const [overviewRes, distRes, healthRes] = await Promise.all([
      storageApi.getOverview(),
      storageApi.getDistribution(),
      storageApi.getHealth(),
    ])
    overview.value = overviewRes || []
    const distData = distRes || []
    distribution.value = distData.map((d: StorageDistribution, i: number) => ({
      ...d,
      size: formatBytes(d.bytes || d.sizeBytes || 0),
      percent: d.percent || 0,
      color: distColors[i % distColors.length],
    }))
    const healthData = healthRes || []
    healthChecks.value = healthData.map((h: StorageHealthCheck) => {
      const mapped = healthIconMap[h.status] || healthIconMap.HEALTHY
      return { ...h, ...mapped, status: h.status === 'HEALTHY' || h.status === 'healthy' ? '正常' : h.status === 'DEGRADED' || h.status === 'degraded' ? '延迟' : '异常' }
    })
  } catch (e) {
    console.error('Failed to load storage data', e)
  }
})
</script>
