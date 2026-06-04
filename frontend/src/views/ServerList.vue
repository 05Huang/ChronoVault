<template>
  <div class="p-[24px] space-y-[24px] pb-20">
    <!-- Header -->
    <section class="flex justify-between items-center">
      <div>
        <h2 class="text-[28px] font-semibold text-on-surface font-[Geist]">服务器管理</h2>
        <p class="text-[14px] text-on-surface-variant">管理和监控所有已注册的服务器</p>
      </div>
      <div class="flex gap-2">
        <button v-if="servers.length > 0" @click="showCloneWizard = true" class="px-4 py-2 rounded-lg bg-secondary-container text-on-secondary-container text-[12px] font-bold flex items-center gap-2 hover:bg-secondary-container/80 transition-all">
          <span class="material-symbols-outlined text-lg">content_copy</span>
          克隆服务器
        </button>
        <button @click="router.push('/onboarding')" class="px-4 py-2 rounded-lg bg-primary text-white text-[12px] font-bold flex items-center gap-2 hover:opacity-90 shadow-lg shadow-primary/20 transition-all">
          <span class="material-symbols-outlined text-lg">add</span>
          添加服务器
        </button>
      </div>
    </section>

    <!-- Group Filter -->
    <div v-if="groups.length > 0 || servers.length > 0" class="flex flex-wrap items-center gap-2">
      <button @click="selectedGroupId = null"
        class="px-3 py-1.5 rounded-lg text-[11px] font-bold border transition-all"
        :class="selectedGroupId === null ? 'bg-primary/10 border-primary text-primary' : 'bg-white/50 border-outline-variant/30 text-on-surface-variant hover:border-primary/30'">
        全部 ({{ servers.length }})
      </button>
      <button v-for="group in groups" :key="group.id" @click="selectedGroupId = group.id"
        class="px-3 py-1.5 rounded-lg text-[11px] font-bold border transition-all"
        :class="selectedGroupId === group.id ? 'border-primary text-primary' : 'border-outline-variant/30 text-on-surface-variant hover:border-primary/30'"
        :style="selectedGroupId === group.id ? { backgroundColor: group.color + '15' } : {}">
        <span class="inline-block w-2 h-2 rounded-full mr-1.5" :style="{ backgroundColor: group.color }"></span>
        {{ group.name }}
      </button>
      <button @click="showGroupPanel = !showGroupPanel" class="px-3 py-1.5 rounded-lg text-[11px] font-bold border border-outline-variant/30 text-on-surface-variant hover:border-primary/30 transition-all flex items-center gap-1">
        <span class="material-symbols-outlined text-[14px]">settings</span>
        管理分组
      </button>
    </div>

    <!-- Group Management Panel -->
    <div v-if="showGroupPanel" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">folder_special</span>
          <div>
            <h3 class="text-[20px] font-semibold">服务器分组</h3>
            <p class="text-[12px] text-on-surface-variant">按环境类型组织服务器</p>
          </div>
        </div>
        <button @click="showGroupPanel = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>
      <div class="p-6 space-y-5">
        <!-- Group Form -->
        <div class="bg-surface-container/50 rounded-xl p-4 border border-outline-variant/20 space-y-3">
          <p class="text-[12px] font-bold text-on-surface-variant">{{ editingGroup ? '编辑分组' : '创建新分组' }}</p>
          <div class="grid grid-cols-3 gap-3">
            <input v-model="groupForm.name" class="px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="分组名称" />
            <select v-model="groupForm.environmentType" class="px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none appearance-none">
              <option value="PRODUCTION">生产环境</option>
              <option value="STAGING">预发布</option>
              <option value="DEVELOPMENT">开发环境</option>
              <option value="TESTING">测试环境</option>
            </select>
            <input v-model="groupForm.color" type="color" class="w-full h-[38px] rounded-lg border border-outline-variant cursor-pointer" />
          </div>
          <input v-model="groupForm.description" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="描述（可选）" />
          <div class="flex justify-end gap-2">
            <button v-if="editingGroup" @click="cancelGroupEdit" class="px-3 py-1.5 text-[11px] font-bold text-outline hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
            <button @click="saveGroup" :disabled="!groupForm.name"
              class="px-4 py-1.5 bg-primary text-white rounded-lg text-[11px] font-bold hover:bg-primary/90 transition-all disabled:opacity-50">
              {{ editingGroup ? '更新' : '创建' }}
            </button>
          </div>
        </div>

        <!-- Groups List -->
        <div v-if="groups.length === 0" class="text-center py-6">
          <p class="text-[13px] text-outline">暂无分组</p>
        </div>
        <div v-else class="space-y-2">
          <div v-for="group in groups" :key="group.id"
            class="flex items-center gap-3 p-3 rounded-xl border border-outline-variant/20 hover:bg-surface-container/30 transition-colors">
            <span class="w-4 h-4 rounded-full shrink-0" :style="{ backgroundColor: group.color }"></span>
            <div class="flex-1 min-w-0">
              <p class="text-[13px] font-bold">{{ group.name }}</p>
              <p class="text-[11px] text-outline">{{ group.description || group.environmentType }}</p>
            </div>
            <span class="px-2 py-0.5 rounded-full text-[9px] font-bold" :class="envColors[group.environmentType] || envColors.DEVELOPMENT">
              {{ group.environmentType }}
            </span>
            <button @click="editGroup(group)" class="p-1.5 rounded-lg hover:bg-surface-container-high text-outline transition-colors">
              <span class="material-symbols-outlined text-[14px]">edit</span>
            </button>
            <button @click="deleteGroup(group.id)" class="p-1.5 rounded-lg hover:bg-error/10 text-outline hover:text-error transition-colors">
              <span class="material-symbols-outlined text-[14px]">delete</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div v-for="i in 3" :key="i" class="glass-panel rounded-xl p-5 animate-pulse">
        <div class="h-6 w-32 bg-surface-container-highest rounded mb-4"></div>
        <div class="h-4 w-48 bg-surface-container-highest rounded mb-2"></div>
        <div class="h-4 w-24 bg-surface-container-highest rounded"></div>
      </div>
    </div>

    <!-- Server Grid -->
    <div v-else-if="filteredServers.length" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="server in filteredServers"
        :key="server.id"
        @click="router.push('/servers/' + server.id)"
        class="glass-panel rounded-xl p-5 cursor-pointer hover:shadow-lg hover:scale-[1.01] transition-all border-2"
        :class="server.status === 'RUNNING' ? 'border-transparent hover:border-secondary/30' : 'border-transparent hover:border-error/30'"
      >
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <span
              class="material-symbols-outlined text-[22px]"
              :class="server.status === 'RUNNING' ? 'text-secondary' : 'text-error'"
              style="font-variation-settings: 'FILL' 1;"
            >dns</span>
            <h3 class="text-[16px] font-bold text-on-surface">{{ server.name }}</h3>
          </div>
          <span
            class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider flex items-center gap-1"
            :class="server.status === 'RUNNING'
              ? 'bg-green-500/10 text-green-600 border border-green-600/20'
              : 'bg-error/10 text-error border border-error/20'"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="server.status === 'RUNNING' ? 'bg-green-500 animate-pulse' : 'bg-error'"></span>
            {{ server.status === 'RUNNING' ? '运行中' : '异常' }}
          </span>
        </div>
        <div class="space-y-2 text-[13px] text-on-surface-variant">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-[16px]">lan</span>
            <span>{{ server.ip }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-[16px]">laptop_windows</span>
            <span>{{ server.os }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-[16px]">schedule</span>
            <span>已运行: {{ server.uptimeSeconds ? Math.floor(server.uptimeSeconds / 3600) + 'h' : 'N/A' }}</span>
          </div>
        </div>
        <div class="mt-4 pt-3 border-t border-outline-variant/20 flex justify-end">
          <span class="text-[12px] text-primary font-bold flex items-center gap-1">
            查看详情
            <span class="material-symbols-outlined text-[16px]">arrow_forward</span>
          </span>
        </div>
      </div>
    </div>

    <!-- Clone Wizard Panel -->
    <div v-if="showCloneWizard" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">content_copy</span>
          <div>
            <h3 class="text-[20px] font-semibold">克隆服务器</h3>
            <p class="text-[12px] text-on-surface-variant">将源服务器的完整状态复制到新服务器</p>
          </div>
        </div>
        <button @click="showCloneWizard = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>
      <div class="p-6 space-y-5">
        <!-- Source Server -->
        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">源服务器</label>
          <select v-model="cloneForm.sourceServerId" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px] appearance-none">
            <option :value="0" disabled>选择源服务器</option>
            <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }} ({{ s.ip }})</option>
          </select>
        </div>

        <!-- Target IP -->
        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">目标服务器 IP</label>
          <input v-model="cloneForm.targetServerIp"
            class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]"
            placeholder="例如 192.168.1.100" />
        </div>

        <!-- Target Name -->
        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">目标名称（可选）</label>
          <input v-model="cloneForm.targetName"
            class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]"
            placeholder="留空将自动生成" />
        </div>

        <p class="text-[12px] text-on-surface-variant">克隆将自动创建源服务器快照，然后恢复到目标服务器。目标服务器需要已安装 restic 或可自动安装。</p>

        <div class="flex justify-end gap-3">
          <button @click="showCloneWizard = false" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
          <button @click="handleClone" :disabled="!cloneForm.sourceServerId || !cloneForm.targetServerIp || cloning"
            class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">{{ cloning ? 'hourglass_empty' : 'content_copy' }}</span>
            {{ cloning ? '提交中...' : '开始克隆' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="glass-panel rounded-xl p-12 text-center">
      <span class="material-symbols-outlined text-outline text-[48px] mb-3 block">dns</span>
      <p class="text-[16px] text-on-surface-variant mb-4">还没有添加任何服务器</p>
      <button @click="router.push('/onboarding')" class="px-6 py-2.5 rounded-lg bg-primary text-white text-[13px] font-bold hover:opacity-90 transition-all">
        添加第一台服务器
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { serversApi } from '@/api/servers'
import { groupsApi } from '@/api/groups'
import type { Server, ServerGroup } from '@/types'

/** Extract a safe error message from an unknown catch value */
function getErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object' && 'message' in e) return (e as { message: string }).message
  if (typeof e === 'string') return e
  return fallback
}

const router = useRouter()
const toast = useToastStore()
const loading = ref(true)
const servers = ref<Server[]>([])

// Group filter state
const groups = ref<ServerGroup[]>([])
const selectedGroupId = ref<number | null>(null)
const showGroupPanel = ref(false)
const editingGroup = ref<ServerGroup | null>(null)
const groupForm = ref({ name: '', description: '', environmentType: 'DEVELOPMENT' as ServerGroup['environmentType'], color: '#0058BE' })

const filteredServers = computed(() => {
  if (selectedGroupId.value === null) return servers.value
  return servers.value.filter(s => s.groupId === selectedGroupId.value)
})

const envColors: Record<string, string> = {
  PRODUCTION: 'bg-red-500/10 text-red-600 border-red-600/20',
  STAGING: 'bg-amber-500/10 text-amber-600 border-amber-600/20',
  DEVELOPMENT: 'bg-blue-500/10 text-blue-600 border-blue-600/20',
  TESTING: 'bg-green-500/10 text-green-600 border-green-600/20',
}

async function loadGroups() {
  try {
    const res = await groupsApi.getAll()
    groups.value = res || []
  } catch (e) {
    console.warn('Failed to load groups', e)
  }
}

async function saveGroup() {
  try {
    if (editingGroup.value) {
      await groupsApi.update(editingGroup.value.id, groupForm.value)
      toast.success('分组已更新')
    } else {
      await groupsApi.create(groupForm.value)
      toast.success('分组已创建')
    }
    editingGroup.value = null
    groupForm.value = { name: '', description: '', environmentType: 'DEVELOPMENT', color: '#0058BE' }
    await loadGroups()
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '保存失败'))
  }
}

function editGroup(group: ServerGroup) {
  editingGroup.value = group
  groupForm.value = { name: group.name, description: group.description || '', environmentType: group.environmentType, color: group.color }
}

async function deleteGroup(id: number) {
  try {
    await groupsApi.delete(id)
    groups.value = groups.value.filter(g => g.id !== id)
    if (selectedGroupId.value === id) selectedGroupId.value = null
    // Reload servers to clear group assignments
    const res = await serversApi.getAll()
    servers.value = res || []
    toast.success('分组已删除')
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '删除失败'))
  }
}

function cancelGroupEdit() {
  editingGroup.value = null
  groupForm.value = { name: '', description: '', environmentType: 'DEVELOPMENT', color: '#0058BE' }
}

// Clone state
const showCloneWizard = ref(false)
const cloning = ref(false)
const cloneForm = ref({
  sourceServerId: 0,
  targetServerIp: '',
  targetName: '',
})

async function handleClone() {
  if (!cloneForm.value.sourceServerId || !cloneForm.value.targetServerIp) {
    toast.error('请选择源服务器并输入目标IP')
    return
  }
  cloning.value = true
  try {
    await serversApi.clone({
      sourceServerId: cloneForm.value.sourceServerId,
      targetServerIp: cloneForm.value.targetServerIp,
      targetName: cloneForm.value.targetName || undefined,
    })
    toast.success('克隆任务已提交，正在后台执行')
    showCloneWizard.value = false
    cloneForm.value = { sourceServerId: 0, targetServerIp: '', targetName: '' }
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '克隆任务提交失败'))
  } finally {
    cloning.value = false
  }
}

onMounted(async () => {
  try {
    const [serversRes, groupsRes] = await Promise.all([
      serversApi.getAll(),
      groupsApi.getAll().catch(() => []),
    ])
    servers.value = serversRes || []
    groups.value = groupsRes || []
  } catch (e) {
    console.error('Failed to load servers', e)
  } finally {
    loading.value = false
  }
})
</script>
