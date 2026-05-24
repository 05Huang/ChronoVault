<template>
  <div class="p-[24px] space-y-[40px] relative">
    <!-- Background Decoration Particles -->
    <div class="absolute w-1 h-1 top-[10%] left-[20%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>
    <div class="absolute w-2 h-2 top-[30%] left-[80%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>
    <div class="absolute w-1.5 h-1.5 top-[60%] left-[15%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>
    <div class="absolute w-1 h-1 top-[80%] left-[70%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>

    <!-- Hero Section -->
    <section class="grid grid-cols-1 lg:grid-cols-12 gap-[16px] items-start">
      <!-- Vertical Timeline Column -->
      <div class="lg:col-span-5 relative">
        <div class="absolute left-6 top-0 bottom-0 w-[2px] opacity-30" style="background: linear-gradient(to bottom, transparent, #0058be, #924700, transparent);"></div>
        <div class="space-y-12">
          <div v-for="snap in snapshots" :key="snap.id" class="relative pl-16 group" :class="snap.status === 'ARCHIVED' ? 'opacity-70 hover:opacity-100' : ''">
            <div class="absolute left-[20px] top-1 w-3 h-3 rounded-full ring-4 z-10" :class="dotColors[snap.status] || dotColors.STABLE"></div>
            <div @click="selectSnapshot(snap)" class="glass-panel p-[20px] rounded-xl border-l-4 hover:translate-x-1 transition-transform cursor-pointer" :class="[(borderColors[snap.status] || borderColors.STABLE), selectedSnapshot?.id === snap.id ? 'shimmer-edge' : '']">
              <div class="flex justify-between items-start mb-2">
                <span class="text-[12px] font-bold tracking-wide" :class="snap.status === 'STABLE' ? 'text-primary' : snap.status === 'WARNING' ? 'text-tertiary' : 'text-outline'">{{ snap.createdAt ? new Date(snap.createdAt).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : snap.date || 'N/A' }}</span>
                <span class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase" :class="statusColors[snap.status] || statusColors.STABLE">{{ snap.status || 'Stable' }}</span>
              </div>
              <h3 class="text-[24px] font-semibold mb-1">{{ snap.name || snap.title || '快照' }}</h3>
              <p class="text-on-surface-variant text-[14px] mb-3">{{ snap.description || snap.note || '' }}</p>
              <!-- Tags -->
              <div v-if="snap.tags && snap.tags.length" class="flex flex-wrap gap-1.5 mb-3">
                <span
                  v-for="tag in snap.tags"
                  :key="tag.id"
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium"
                  :style="{ backgroundColor: tag.color + '20', color: tag.color, border: '1px solid ' + tag.color + '40' }"
                >
                  {{ tag.name }}
                  <button
                    @click.stop="handleRemoveTag(snap.id, tag.name)"
                    class="ml-0.5 hover:opacity-70 transition-opacity"
                    title="移除标签"
                  >
                    <span class="material-symbols-outlined text-[12px]">close</span>
                  </button>
                </span>
              </div>
              <div class="flex gap-2 items-center">
                <span v-if="snap.hash" class="text-[10px] text-outline">Hash: {{ snap.hash }}</span>
              </div>
            </div>
          </div>
          <div v-if="!snapshots.length" class="relative pl-16 group">
            <div class="absolute left-[20px] top-1 w-3 h-3 rounded-full bg-outline ring-4 ring-outline/10 z-10"></div>
            <div class="glass-panel p-[20px] rounded-xl border-l-4 border-l-outline-variant">
              <p class="text-on-surface-variant text-[14px]">暂无快照数据</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Details & Diff View Column -->
      <div class="lg:col-span-7 space-y-[16px]">
        <!-- Time Travel Control Card -->
        <div class="glass-panel p-6 rounded-2xl bg-gradient-to-br from-primary/5 to-transparent relative overflow-hidden">
          <div class="absolute -right-10 -top-10 w-40 h-40 bg-primary/10 rounded-full blur-3xl"></div>
          <div class="relative z-10 flex flex-col md:flex-row justify-between items-center gap-6">
            <div>
              <div class="flex items-center gap-2 mb-2">
                <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">auto_awesome</span>
                <h2 class="text-[24px] font-semibold">时间穿梭控制台</h2>
              </div>
              <p class="text-on-surface-variant text-[14px] max-w-md">当前选定：{{ selectedSnapshot?.name || '未选择' }} {{ selectedSnapshot?.createdAt ? '(' + new Date(selectedSnapshot.createdAt).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) + ')' : '' }}。您可以快速回滚到任何历史状态，ChronoVault 将自动处理网络路由与持久化卷映射。</p>
            </div>
            <button @click="openRollbackConfirm" class="w-full md:w-auto bg-primary text-white font-bold px-8 py-4 rounded-2xl shadow-xl shadow-primary/30 hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3">
              <span class="material-symbols-outlined">settings_backup_restore</span>
              立即执行回滚 (Rollback)
            </button>
          </div>
        </div>

        <!-- Tags Panel -->
        <div v-if="selectedSnapshot" class="glass-panel p-5 rounded-2xl border border-outline-variant/20">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-primary text-[20px]">label</span>
              <span class="text-[12px] font-bold uppercase tracking-wider text-on-surface-variant">标签</span>
            </div>
            <button @click="openAddTagModal" class="text-[12px] font-bold text-primary flex items-center gap-1 hover:gap-2 transition-all">
              <span class="material-symbols-outlined text-[16px]">add_circle</span>
              添加标签
            </button>
          </div>
          <div v-if="selectedSnapshot.tags && selectedSnapshot.tags.length" class="flex flex-wrap gap-2">
            <span
              v-for="tag in selectedSnapshot.tags"
              :key="tag.id"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[12px] font-medium"
              :style="{ backgroundColor: tag.color + '20', color: tag.color, border: '1px solid ' + tag.color + '40' }"
            >
              {{ tag.name }}
              <button
                @click="handleRemoveTag(selectedSnapshot!.id, tag.name)"
                class="hover:opacity-70 transition-opacity"
                title="移除标签"
              >
                <span class="material-symbols-outlined text-[14px]">close</span>
              </button>
            </span>
          </div>
          <p v-else class="text-[13px] text-outline">暂无标签，点击上方按钮添加</p>
        </div>

        <!-- Diff Compare View -->
        <div class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
          <div class="bg-surface-container-high/50 px-6 py-4 flex justify-between items-center border-b border-outline-variant/20">
            <div class="flex items-center gap-4">
              <span class="text-[12px] font-bold uppercase tracking-wider text-on-surface-variant">差异对比</span>
              <div class="flex items-center gap-2 bg-surface px-3 py-1 rounded-full border border-outline-variant/30 text-[12px]">
                <span class="text-outline">当前</span>
                <span class="material-symbols-outlined text-[14px]">arrow_right_alt</span>
                <span class="font-bold text-primary">{{ selectedSnapshot?.name || '快照' }}</span>
              </div>
            </div>
            <div class="flex gap-1">
              <div class="w-2.5 h-2.5 rounded-full bg-error/40"></div>
              <div class="w-2.5 h-2.5 rounded-full bg-tertiary/40"></div>
              <div class="w-2.5 h-2.5 rounded-full bg-primary/40"></div>
            </div>
          </div>
          <div class="p-6 font-[Geist] text-[14px] space-y-4">
            <div class="grid grid-cols-12 gap-4 border-b border-outline-variant/10 pb-2">
              <div class="col-span-4 text-outline">配置路径</div>
              <div class="col-span-4 text-outline">变更前</div>
              <div class="col-span-4 text-outline">变更后</div>
            </div>
            <div v-for="diff in diffs" :key="diff.path" class="grid grid-cols-12 gap-4 items-center">
              <div class="col-span-4 text-on-surface-variant">{{ diff.path }}</div>
              <div class="col-span-4 bg-error/5 text-error px-2 py-1 rounded border border-error/10 overflow-hidden text-ellipsis">{{ diff.prev }}</div>
              <div class="col-span-4 bg-primary/5 text-primary px-2 py-1 rounded border border-primary/10 overflow-hidden text-ellipsis">{{ diff.next }}</div>
            </div>
            <div class="pt-4 mt-4 border-t border-outline-variant/10">
              <div class="flex items-center justify-between">
                <div class="text-[12px] text-outline">
                  <span v-if="diffs.length" class="text-primary font-bold">{{ diffs.length }} 项变更</span>
                  <span v-else class="text-outline">暂无差异数据</span>
                </div>
                <button @click="showFullDiff" class="text-primary text-[12px] font-bold flex items-center gap-1 hover:underline">
                  查看完整 JSON 差异
                  <span class="material-symbols-outlined text-[16px]">open_in_new</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- AI Insight Card -->
        <div class="glass-panel p-6 rounded-2xl border-l-4 border-l-primary/40 bg-gradient-to-r from-surface-container-lowest to-transparent">
          <div class="flex gap-4">
            <div class="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center shrink-0 text-primary">
              <span class="material-symbols-outlined text-[28px]" style="font-variation-settings: 'FILL' 1;">psychology</span>
            </div>
            <div>
              <h4 class="text-[24px] font-semibold text-primary mb-1">AI 架构师洞察</h4>
              <p v-if="selectedSnapshot && diffs.length" class="text-on-surface-variant text-[14px] leading-relaxed mb-3">
                当前快照包含 {{ diffs.length }} 项文件变更。建议在回滚前确认变更内容，以避免潜在的服务中断。
              </p>
              <p v-else class="text-on-surface-variant text-[14px] leading-relaxed mb-3">
                选择一个快照查看 AI 分析洞察。
              </p>
              <button @click="showOperationSteps" class="text-[14px] font-bold text-primary flex items-center gap-1 hover:gap-2 transition-all">
                查看建议的操作流程
                <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Footer Stats -->
    <footer class="grid grid-cols-2 md:grid-cols-4 gap-[16px] pt-8 border-t border-outline-variant/20">
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">总大小</p>
        <p class="text-[32px] font-semibold">{{ stats.totalSize ? formatBytes(stats.totalSize) : '-' }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">快照数</p>
        <p class="text-[32px] font-semibold">{{ snapshots.length }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">平均大小</p>
        <p class="text-[32px] font-semibold">{{ stats.avgSize || '-' }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">最新快照</p>
        <p class="text-[32px] font-semibold text-primary">{{ stats.latestDate || '-' }}</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { snapshotsApi } from '@/api/snapshots'
import { formatBytes } from '@/utils/format'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import AddTagModal from '@/components/modals/AddTagModal.vue'

const toast = useToastStore()
const modal = useModalStore()

import type { Snapshot, SnapshotDiff } from '@/types'

const snapshots = ref<Snapshot[]>([])
const selectedSnapshot = ref<Snapshot | null>(null)
const diffs = ref<SnapshotDiff[]>([])
const stats = ref({ totalSize: 0, avgSize: '-', latestDate: '' })

function openRollbackConfirm() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  modal.open({
    component: ConfirmModal,
    title: '确认回滚',
    props: {
      message: `即将回滚至 ${selectedSnapshot.value?.name || '选定快照'}。此操作将覆盖当前系统状态，期间 API 服务将短暂离线约 45-60 秒。是否继续？`,
      confirmText: '执行回滚',
      confirmClass: 'bg-error hover:bg-error/90',
      successMessage: '回滚任务已提交，预计 60 秒完成',
      onConfirm: async () => {
        if (selectedSnapshot.value) {
          await snapshotsApi.rollback(selectedSnapshot.value.id)
        }
      },
    },
  })
}

function showFullDiff() {
  if (!diffs.value.length) {
    toast.error('暂无差异数据')
    return
  }
  const json = JSON.stringify(diffs.value, null, 2)
  modal.open({
    component: ConfirmModal,
    title: '完整 JSON 差异',
    props: {
      message: json,
      confirmText: '关闭',
      confirmClass: 'bg-surface-container-highest text-on-surface',
    },
  })
}

function showOperationSteps() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  const steps = [
    `1. 确认当前服务状态：检查 ${selectedSnapshot.value.serverName || '目标服务器'} 上的服务是否正常运行`,
    `2. 创建当前状态备份：在回滚前先创建一个应急快照`,
    `3. 执行回滚：将系统恢复至 ${selectedSnapshot.value.name || '选定快照'}`,
    '4. 验证服务：确认所有服务已正常启动并通过健康检查',
    '5. 通知团队：告知相关成员回滚已完成',
  ]
  modal.open({
    component: ConfirmModal,
    title: '建议的操作流程',
    props: {
      message: steps.join('\n\n'),
      confirmText: '已了解',
      confirmClass: 'bg-primary text-white',
    },
  })
}

function openAddTagModal() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  const snapId = selectedSnapshot.value.id
  modal.open({
    component: AddTagModal,
    title: '添加标签',
    width: 'max-w-md',
    props: {
      snapshotId: snapId,
      existingTags: selectedSnapshot.value.tags || [],
      onAdded: () => {
        refreshSnapshotTags(snapId)
      },
    },
  })
}

async function handleRemoveTag(snapshotId: number, tagName: string) {
  try {
    await snapshotsApi.removeTag(snapshotId, tagName)
    toast.success(`标签 "${tagName}" 已移除`)
    await refreshSnapshotTags(snapshotId)
  } catch (e: any) {
    toast.error(e?.message || '移除标签失败')
  }
}

async function refreshSnapshotTags(snapshotId: number) {
  try {
    const updated = await snapshotsApi.get(snapshotId)
    // Update the tags in the snapshots list
    const idx = snapshots.value.findIndex(s => s.id === snapshotId)
    if (idx >= 0) {
      snapshots.value[idx] = { ...snapshots.value[idx], tags: updated.tags || [] }
    }
    // Update selected snapshot
    if (selectedSnapshot.value?.id === snapshotId) {
      selectedSnapshot.value = { ...selectedSnapshot.value, tags: updated.tags || [] }
    }
  } catch (e) {
    console.error('Failed to refresh tags', e)
  }
}

function selectSnapshot(snap: Snapshot) {
  selectedSnapshot.value = snap
  loadDiff(snap.id)
}

async function loadDiff(id: number) {
  try {
    const res = await snapshotsApi.getDiff(id)
    diffs.value = res || []
  } catch (e) {
    console.error('Failed to load diff', e)
  }
}

const statusColors: Record<string, string> = {
  STABLE: 'bg-primary/10 text-primary',
  WARNING: 'bg-tertiary/10 text-tertiary',
  ARCHIVED: 'bg-outline/10 text-outline',
}

const dotColors: Record<string, string> = {
  STABLE: 'bg-primary ring-primary/20 shadow-[0_0_15px_rgba(0,88,190,0.5)]',
  WARNING: 'bg-tertiary ring-tertiary/20 shadow-[0_0_15px_rgba(146,71,0,0.5)]',
  ARCHIVED: 'bg-outline ring-outline/10',
}

const borderColors: Record<string, string> = {
  STABLE: 'border-l-primary',
  WARNING: 'border-l-tertiary',
  ARCHIVED: 'border-l-outline-variant',
}

onMounted(async () => {
  try {
    const [snapshotsRes] = await Promise.all([
      snapshotsApi.getAll(),
    ])
    const data = snapshotsRes || []
    snapshots.value = data
    if (data.length > 0) {
      selectSnapshot(data[0])
    }
    const totalSize = data.reduce((acc: number, s: Snapshot) => acc + (s.sizeBytes || 0), 0)
    const avgSize = data.length > 0 ? Math.round(totalSize / data.length) : 0
    const latestDate = data.length > 0 && data[0].createdAt
      ? new Date(data[0].createdAt).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
      : ''
    stats.value = {
      totalSize,
      avgSize: avgSize ? formatBytes(avgSize) : '-',
      latestDate,
    }
  } catch (e) {
    console.error('Failed to load snapshots', e)
  }
})
</script>
