<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { snapshotsApi } from '@/api/snapshots'
import { serversApi } from '@/api/servers'
import type { Snapshot, Server } from '@/types'

const router = useRouter()
const route = useRoute()

const servers = ref<Server[]>([])
const selectedServerId = ref<number>(Number(route.query.serverId) || 0)
const snapshots = ref<Snapshot[]>([])
const loading = ref(false)
const page = ref(0)
const hasMore = ref(true)
const selectedSnapshotIds = ref<Set<number>>(new Set())

interface ChangeSummary {
  packages_added?: number
  packages_removed?: number
  packages_upgraded?: number
  services_changed?: number
  ports_changed?: number
  configs_changed?: number
}

const formatTimestamp = (ts: string) => {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const formatSummaryBadges = (summaryJson: string | null | undefined): string[] => {
  if (!summaryJson) return []
  try {
    const s: ChangeSummary = JSON.parse(summaryJson)
    const badges: string[] = []
    const added = (s.packages_added || 0) + (s.services_changed || 0)
    const removed = (s.packages_removed || 0)
    const upgraded = (s.packages_upgraded || 0)
    const configs = (s.configs_changed || 0)
    const ports = (s.ports_changed || 0)

    if (added > 0) badges.push(`+${added}`)
    if (removed > 0) badges.push(`-${removed}`)
    if (upgraded > 0) badges.push(`~${upgraded} upg`)
    if (configs > 0) badges.push(`⚙ ${configs} cfg`)
    if (ports > 0) badges.push(`⚠ ${ports} port`)
    return badges
  } catch { return [] }
}

const loadServers = async () => {
  try {
    servers.value = await serversApi.getAll() as unknown as Server[]
    if (servers.value.length > 0 && !selectedServerId.value) {
      selectedServerId.value = servers.value[0].id
    }
  } catch (e) {
    console.error('Failed to load servers:', e)
  }
}

const loadTimeline = async (reset = false) => {
  if (!selectedServerId.value) return
  if (reset) { page.value = 0; snapshots.value = []; hasMore.value = true }
  if (!hasMore.value) return
  loading.value = true
  try {
    const data = await snapshotsApi.getTimeline(selectedServerId.value, page.value, 50)
    if (reset) snapshots.value = data
    else snapshots.value.push(...data)
    hasMore.value = data.length === 50
    page.value++
  } catch (e) {
    console.error('Failed to load timeline:', e)
  } finally {
    loading.value = false
  }
}

const viewSnapshot = (id: number) => {
  router.push(`/snapshots?id=${id}`)
}

const selectForDiff = (id: number) => {
  if (selectedSnapshotIds.value.has(id)) {
    selectedSnapshotIds.value.delete(id)
  } else {
    if (selectedSnapshotIds.value.size >= 2) {
      // Replace the oldest selection
      const ids = Array.from(selectedSnapshotIds.value)
      selectedSnapshotIds.value.delete(ids[0])
    }
    selectedSnapshotIds.value.add(id)
  }
  selectedSnapshotIds.value = new Set(selectedSnapshotIds.value)
}

const goToDiff = () => {
  const ids = Array.from(selectedSnapshotIds.value)
  if (ids.length === 2) {
    router.push(`/snapshots/diff?from=${ids[0]}&to=${ids[1]}`)
  }
}

const isSelected = (id: number) => selectedSnapshotIds.value.has(id)

onMounted(async () => {
  await loadServers()
  await loadTimeline(true)
})
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">快照时间线</h1>
        <p class="text-sm text-gray-500 mt-1">像 Git log 一样查看服务器状态变更历史</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          v-if="selectedSnapshotIds.size === 2"
          @click="goToDiff"
          class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium"
        >
          对比选中的 {{ selectedSnapshotIds.size }} 个快照
        </button>
      </div>
    </div>

    <!-- Server selector -->
    <div class="mb-6">
      <label class="block text-sm font-medium text-gray-700 mb-2">选择服务器</label>
      <select
        v-model="selectedServerId"
        @change="loadTimeline(true)"
        class="block w-64 rounded-lg border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 text-sm"
      >
        <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name || s.ip }}</option>
      </select>
    </div>

    <!-- Timeline -->
    <div v-if="loading && snapshots.length === 0" class="text-center py-12 text-gray-500">
      加载中...
    </div>

    <div v-else-if="snapshots.length === 0" class="text-center py-12 text-gray-400">
      暂无快照数据
    </div>

    <div v-else class="relative">
      <!-- Timeline line -->
      <div class="absolute left-5 top-0 bottom-0 w-0.5 bg-gray-200"></div>

      <!-- Snapshot nodes -->
      <div
        v-for="snap in snapshots"
        :key="snap.id"
        class="relative pl-12 pb-8"
      >
        <!-- Timeline dot -->
        <div
          class="absolute left-3 top-1 w-4 h-4 rounded-full border-2 transition-colors"
          :class="[
            isSelected(snap.id)
              ? 'bg-blue-600 border-blue-600'
              : snap.status === 'WARNING'
                ? 'bg-yellow-400 border-yellow-400'
                : 'bg-white border-gray-300'
          ]"
        ></div>

        <!-- Snapshot card -->
        <div
          class="bg-white rounded-lg border p-4 hover:shadow-md transition-shadow cursor-pointer"
          :class="{ 'ring-2 ring-blue-500': isSelected(snap.id) }"
          @click="selectForDiff(snap.id)"
        >
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <!-- Title & time -->
              <div class="flex items-center gap-2 mb-1">
                <span class="font-mono text-xs text-gray-400">{{ snap.hash?.substring(0, 7) || '------' }}</span>
                <span class="text-xs text-gray-500">[{{ formatTimestamp(snap.createdAt) }}]</span>
                <span
                  v-if="snap.status === 'WARNING'"
                  class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800"
                >⚠</span>
              </div>
              <h3 class="text-sm font-medium text-gray-900">{{ snap.name || '快照 #' + snap.id }}</h3>

              <!-- Change summary badges -->
              <div class="flex items-center gap-2 mt-2">
                <span
                  v-for="(badge, i) in formatSummaryBadges(snap.changeSummaryJson)"
                  :key="i"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                  :class="[
                    badge.startsWith('+') ? 'bg-green-100 text-green-800' :
                    badge.startsWith('-') ? 'bg-red-100 text-red-800' :
                    badge.startsWith('⚠') ? 'bg-yellow-100 text-yellow-800' :
                    'bg-gray-100 text-gray-800'
                  ]"
                >{{ badge }}</span>
                <span
                  v-if="!formatSummaryBadges(snap.changeSummaryJson).length"
                  class="text-xs text-gray-400 italic"
                >(no changes)</span>
              </div>
            </div>

            <div class="flex items-center gap-2 ml-4">
              <span v-if="snap.sizeBytes" class="text-xs text-gray-400">
                {{ (snap.sizeBytes / 1024 / 1024).toFixed(1) }}MB
              </span>
              <button
                @click.stop="viewSnapshot(snap.id)"
                class="text-xs text-blue-600 hover:text-blue-800"
              >详情</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Load more -->
      <div v-if="hasMore" class="pl-12 pb-4">
        <button
          @click="loadTimeline(false)"
          :disabled="loading"
          class="text-sm text-blue-600 hover:text-blue-800 disabled:text-gray-400"
        >
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </div>
  </div>
</template>
