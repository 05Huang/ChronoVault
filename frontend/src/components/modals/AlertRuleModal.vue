<template>
  <form @submit.prevent="handleConfirm" class="space-y-4">
    <div>
      <label class="text-[12px] font-bold text-on-surface-variant block mb-1">告警名称</label>
      <input v-model="name" placeholder="例：CPU 使用率过高告警"
        class="w-full bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary outline-none" />
    </div>
    <div>
      <label class="text-[12px] font-bold text-on-surface-variant block mb-1">监控指标</label>
      <select v-model="metric"
        class="w-full bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary outline-none appearance-none">
        <option>CPU 使用率</option>
        <option>内存使用率</option>
        <option>磁盘使用率</option>
        <option>网络延迟 (P99)</option>
        <option>错误率</option>
      </select>
    </div>
    <div class="grid grid-cols-2 gap-3">
      <div>
        <label class="text-[12px] font-bold text-on-surface-variant block mb-1">阈值</label>
        <input v-model="threshold" type="number" placeholder="90"
          class="w-full bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary outline-none" />
      </div>
      <div>
        <label class="text-[12px] font-bold text-on-surface-variant block mb-1">持续时间</label>
        <select v-model="duration"
          class="w-full bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary outline-none appearance-none">
          <option>1 分钟</option>
          <option>5 分钟</option>
          <option>15 分钟</option>
          <option>30 分钟</option>
        </select>
      </div>
    </div>
    <div>
      <label class="text-[12px] font-bold text-on-surface-variant block mb-1">严重级别</label>
      <div class="flex gap-2">
        <button v-for="level in levels" :key="level.value" type="button" @click="severity = level.value"
          class="flex-1 py-2 rounded-lg text-[12px] font-bold border transition-all"
          :class="severity === level.value ? level.activeClass : 'border-outline-variant/30 text-on-surface-variant hover:bg-surface-container-high'">
          {{ level.label }}
        </button>
      </div>
    </div>
    <div class="flex gap-3 pt-2">
      <button type="button" @click="modal.close()" class="flex-1 py-2.5 rounded-lg text-[12px] font-bold border border-outline-variant/30 text-on-surface-variant hover:bg-surface-container-high transition-all">
        取消
      </button>
      <button type="submit" :disabled="loading" class="flex-1 py-2.5 rounded-lg text-[12px] font-bold bg-primary text-white hover:opacity-90 transition-all disabled:opacity-50">
        {{ loading ? '创建中...' : '创建规则' }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useModalStore } from '@/stores/modal'
import { useToastStore } from '@/stores/toast'
import { alertsApi } from '@/api/alerts'

const modal = useModalStore()
const toast = useToastStore()

const name = ref('')
const metric = ref('CPU 使用率')
const threshold = ref('')
const duration = ref('5 分钟')
const severity = ref('warning')
const loading = ref(false)

const levels = [
  { value: 'critical', label: '严重', activeClass: 'bg-error text-white border-error' },
  { value: 'warning', label: '警告', activeClass: 'bg-tertiary text-white border-tertiary' },
  { value: 'info', label: '通知', activeClass: 'bg-primary text-white border-primary' },
]

async function handleConfirm() {
  if (!name.value.trim()) {
    toast.warning('请输入告警名称')
    return
  }
  loading.value = true
  try {
    const durationMap: Record<string, number> = { '1 分钟': 1, '5 分钟': 5, '15 分钟': 15, '30 分钟': 30 }
    await alertsApi.createRule({
      name: name.value,
      metric: metric.value,
      threshold: Number(threshold.value) || 90,
      durationMinutes: durationMap[duration.value] || 5,
      severity: severity.value,
    })
    toast.success(`告警规则「${name.value}」已创建`)
    modal.close()
  } catch (e: unknown) {
    toast.error((e instanceof Error ? e.message : null) || '创建失败')
  } finally {
    loading.value = false
  }
}
</script>
