<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { snapshotsApi } from '@/api/snapshots'
import type { StateDiffResult, Snapshot } from '@/types'
import StateTree from '@/components/StateTree.vue'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const toast = useToastStore()

const fromId = ref(Number(route.query.from) || 0)
const toId = ref(Number(route.query.to) || 0)
const diff = ref<StateDiffResult | null>(null)
const fromSnapshot = ref<Snapshot | null>(null)
const toSnapshot = ref<Snapshot | null>(null)
const loading = ref(false)
const error = ref('')
const activeTab = ref<'packages' | 'services' | 'ports' | 'configs' | 'docker' | 'crontab'>('packages')
const rollingBack = ref(false)
const rollbackItem = ref<{ type: string; name?: string; path?: string; target_version?: string } | null>(null)
const showRollbackConfirm = ref(false)

const tabs = [
  { key: 'packages' as const, label: '软件包', icon: '📦' },
  { key: 'services' as const, label: '服务', icon: '⚙️' },
  { key: 'ports' as const, label: '端口', icon: '🔌' },
  { key: 'configs' as const, label: '配置文件', icon: '📄' },
  { key: 'docker' as const, label: 'Docker', icon: '🐳' },
  { key: 'crontab' as const, label: '定时任务', icon: '⏰' },
]

const totalChanges = computed(() => {
  if (!diff.value?.summary) return 0
  const s = diff.value.summary
  return s.packagesAdded + s.packagesRemoved + s.packagesUpgraded +
         s.servicesChanged + s.portsChanged + s.configsChanged + s.dockerChanged + s.crontabChanged
})

const riskLevel = computed(() => {
  if (!diff.value) return 'low'
  const s = diff.value.summary
  // HIGH: port changes or many service changes
  if (s.portsChanged > 0 || s.servicesChanged > 3) return 'high'
  // MEDIUM: config changes or service changes
  if (s.configsChanged > 0 || s.servicesChanged > 0) return 'medium'
  return 'low'
})

const rollbackDescription = computed(() => {
  if (!rollbackItem.value) return ''
  const item = rollbackItem.value
  if (item.type === 'package') {
    return `将包 "${item.name}" 回滚到版本 ${item.target_version}`
  } else if (item.type === 'config') {
    return `恢复配置文件 "${item.path}" 到旧版本`
  } else if (item.type === 'service') {
    return `重新启用服务 "${item.name}"`
  }
  return '执行选择性回滚'
})

const loadDiff = async () => {
  if (!fromId.value || !toId.value) {
    error.value = '请在 URL 中指定 from 和 to 参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [diffData, fromData, toData] = await Promise.all([
      snapshotsApi.getStateDiff(fromId.value, toId.value),
      snapshotsApi.get(fromId.value),
      snapshotsApi.get(toId.value),
    ])
    diff.value = diffData
    fromSnapshot.value = fromData
    toSnapshot.value = toData
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function handleRollback(item: { type: string; name?: string; path?: string; target_version?: string }) {
  rollbackItem.value = item
  showRollbackConfirm.value = true
}

async function confirmRollback() {
  if (!rollbackItem.value || !toId.value) return

  rollingBack.value = true
  showRollbackConfirm.value = false
  try {
    const result = await snapshotsApi.selectiveRollback(toId.value, [rollbackItem.value])
    toast.success(result || '选择性回滚完成')
    // Reload diff to reflect changes
    await loadDiff()
  } catch (e: any) {
    toast.error(e?.message || '选择性回滚失败')
  } finally {
    rollingBack.value = false
    rollbackItem.value = null
  }
}

function cancelRollback() {
  showRollbackConfirm.value = false
  rollbackItem.value = null
}

onMounted(loadDiff)
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <!-- Header -->
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">状态 Diff</h1>
      <p class="text-sm text-gray-500 mt-1">对比两个快照之间的系统状态变更</p>
    </div>

    <!-- Snapshot selector -->
    <div class="flex items-center gap-4 mb-6 bg-gray-50 rounded-lg p-4">
      <div class="flex-1">
        <label class="block text-xs font-medium text-gray-500 mb-1">基准快照 (A)</label>
        <input
          v-model.number="fromId"
          type="number"
          class="block w-full rounded border-gray-300 shadow-sm text-sm"
          placeholder="快照 ID"
        />
        <p v-if="fromSnapshot" class="text-xs text-gray-500 mt-1">
          {{ fromSnapshot.name || '快照 #' + fromSnapshot.id }} — {{ fromSnapshot.createdAt }}
        </p>
      </div>
      <div class="text-gray-400 text-xl mt-4">→</div>
      <div class="flex-1">
        <label class="block text-xs font-medium text-gray-500 mb-1">对比快照 (B)</label>
        <input
          v-model.number="toId"
          type="number"
          class="block w-full rounded border-gray-300 shadow-sm text-sm"
          placeholder="快照 ID"
        />
        <p v-if="toSnapshot" class="text-xs text-gray-500 mt-1">
          {{ toSnapshot.name || '快照 #' + toSnapshot.id }} — {{ toSnapshot.createdAt }}
        </p>
      </div>
      <button
        @click="loadDiff"
        :disabled="loading || !fromId || !toId"
        class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium disabled:bg-gray-300 mt-4"
      >
        {{ loading ? '计算中...' : '对比' }}
      </button>
    </div>

    <!-- Error -->
    <div v-if="error" class="bg-red-50 border border-red-200 rounded-lg p-4 mb-6 text-red-700 text-sm">
      {{ error }}
    </div>

    <!-- Loading -->
    <div v-if="loading && !diff" class="text-center py-12 text-gray-500">计算 Diff 中...</div>

    <!-- Results -->
    <div v-if="diff && !loading">
      <!-- Summary cards -->
      <div class="grid grid-cols-4 gap-4 mb-6">
        <div class="bg-white rounded-lg border p-4">
          <div class="text-sm text-gray-500">风险等级</div>
          <div class="text-lg font-bold mt-1"
            :class="{
              'text-red-600': riskLevel === 'high',
              'text-yellow-600': riskLevel === 'medium',
              'text-green-600': riskLevel === 'low',
            }"
          >{{ riskLevel === 'high' ? '⚠ 高' : riskLevel === 'medium' ? '中' : '✓ 低' }}</div>
        </div>
        <div class="bg-white rounded-lg border p-4">
          <div class="text-sm text-gray-500">总变更数</div>
          <div class="text-lg font-bold text-gray-900 mt-1">{{ totalChanges }}</div>
        </div>
        <div class="bg-white rounded-lg border p-4">
          <div class="text-sm text-gray-500">包变更</div>
          <div class="text-lg font-bold text-blue-600 mt-1">
            +{{ diff.summary?.packagesAdded || 0 }}
            -{{ diff.summary?.packagesRemoved || 0 }}
            ~{{ diff.summary?.packagesUpgraded || 0 }}
          </div>
        </div>
        <div class="bg-white rounded-lg border p-4">
          <div class="text-sm text-gray-500">配置变更</div>
          <div class="text-lg font-bold text-orange-600 mt-1">
            {{ diff.summary?.configsChanged || 0 }} 个文件
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <div class="border-b border-gray-200 mb-6">
        <nav class="flex gap-4">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            @click="activeTab = tab.key"
            class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
            :class="[
              activeTab === tab.key
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            ]"
          >
            {{ tab.icon }} {{ tab.label }}
          </button>
        </nav>
      </div>

      <!-- Tab content -->
      <div class="bg-white rounded-lg border p-6">
        <StateTree :diff="diff" :active-tab="activeTab" :snapshot-id="toId" @rollback="handleRollback" />
      </div>
    </div>

    <!-- Rollback Confirmation Dialog -->
    <div v-if="showRollbackConfirm" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div class="bg-white rounded-xl p-6 max-w-md w-full mx-4 shadow-2xl">
        <h3 class="text-lg font-bold text-gray-900 mb-2">确认选择性回滚</h3>
        <p class="text-sm text-gray-600 mb-4">{{ rollbackDescription }}</p>
        <div class="bg-yellow-50 border border-yellow-200 rounded-lg p-3 mb-4">
          <p class="text-xs text-yellow-800">
            ⚠️ 此操作将修改服务器上的文件或包版本。请确保您了解回滚的影响。
          </p>
        </div>
        <div class="flex gap-3 justify-end">
          <button @click="cancelRollback"
            class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200">
            取消
          </button>
          <button @click="confirmRollback" :disabled="rollingBack"
            class="px-4 py-2 text-sm font-medium text-white bg-yellow-600 rounded-lg hover:bg-yellow-700 disabled:opacity-50">
            {{ rollingBack ? '回滚中...' : '确认回滚' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
