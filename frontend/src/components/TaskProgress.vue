<template>
  <Transition name="slide">
    <div v-if="activeTasks.length" class="fixed bottom-6 right-6 z-50 w-80 space-y-2">
      <div v-for="task in activeTasks" :key="task.id"
        class="glass-panel rounded-xl p-4 shadow-2xl border border-outline-variant/30 backdrop-blur-xl">
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-primary text-[18px]" :class="task.status === 'RUNNING' ? 'animate-spin' : ''">
              {{ taskIcon(task.type) }}
            </span>
            <span class="text-[12px] font-bold text-on-surface">{{ taskLabel(task.type) }}</span>
          </div>
          <button @click="dismiss(task.id)" class="text-outline hover:text-on-surface transition-colors">
            <span class="material-symbols-outlined text-[16px]">close</span>
          </button>
        </div>
        <div class="space-y-2">
          <div class="flex justify-between text-[11px]">
            <span class="text-on-surface-variant">{{ task.message || '处理中...' }}</span>
            <span class="font-bold text-primary">{{ task.progress }}%</span>
          </div>
          <div class="h-1.5 w-full bg-surface-container-highest rounded-full overflow-hidden">
            <div class="h-full bg-primary rounded-full transition-all duration-500"
              :class="task.status === 'FAILED' ? 'bg-error' : ''"
              :style="{ width: task.progress + '%' }"></div>
          </div>
        </div>
        <div v-if="task.status === 'COMPLETED'" class="mt-2 flex items-center gap-1 text-[11px] text-green-600">
          <span class="material-symbols-outlined text-[14px]">check_circle</span>
          任务完成
        </div>
        <div v-if="task.status === 'FAILED'" class="mt-2 flex items-center gap-1 text-[11px] text-error">
          <span class="material-symbols-outlined text-[14px]">error</span>
          {{ task.error || '任务失败' }}
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'

interface ActiveTask {
  id: number
  type: string
  status: string
  progress: number
  message: string
  error?: string
}

const activeTasks = ref<ActiveTask[]>([])
const { connect, subscribe, unsubscribe, disconnect } = useWebSocket()

const taskIcons: Record<string, string> = {
  SNAPSHOT: 'backup',
  RECOVER: 'settings_backup_restore',
  MIGRATE: 'swap_horiz',
  SCAN: 'shutter_speed',
  RESTORE: 'restore',
  EXPORT: 'download',
  HEALTH_CHECK: 'monitor_heart',
}

const taskLabels: Record<string, string> = {
  SNAPSHOT: '快照任务',
  RECOVER: '恢复任务',
  MIGRATE: '迁移任务',
  SCAN: '扫描任务',
  RESTORE: '还原任务',
  EXPORT: '导出任务',
  HEALTH_CHECK: '健康检查',
}

function taskIcon(type: string) {
  return taskIcons[type] || 'task'
}

function taskLabel(type: string) {
  return taskLabels[type] || '后台任务'
}

function dismiss(id: number) {
  activeTasks.value = activeTasks.value.filter((t) => t.id !== id)
}

interface TaskUpdateData {
  id?: number
  taskId?: number
  taskType?: string
  type?: string
  status?: string
  progress?: number
  message?: string
  error?: string
}

function handleTaskUpdate(data: TaskUpdateData) {
  const taskId = data.id || data.taskId
  if (!taskId) return

  const task: ActiveTask = {
    id: taskId,
    type: data.taskType || data.type || 'UNKNOWN',
    status: data.status || 'RUNNING',
    progress: data.progress || 0,
    message: data.message || '',
    error: data.error,
  }

  const idx = activeTasks.value.findIndex((t) => t.id === taskId)
  if (idx >= 0) {
    activeTasks.value[idx] = { ...activeTasks.value[idx], ...task }
    if (task.status === 'COMPLETED' || task.status === 'FAILED') {
      setTimeout(() => dismiss(taskId), 5000)
    }
  } else if (task.status === 'RUNNING' || task.status === 'PENDING') {
    activeTasks.value.push(task)
  }
}

onMounted(() => {
  connect()
  subscribe('/topic/tasks', handleTaskUpdate)
})

onUnmounted(() => {
  unsubscribe('/topic/tasks')
  disconnect()
})
</script>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
