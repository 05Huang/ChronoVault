<script setup lang="ts">
import { ref } from 'vue'
import type { StateDiffResult } from '@/types'

const props = defineProps<{
  diff: StateDiffResult
  activeTab: string
  snapshotId?: number
}>()

const emit = defineEmits<{
  rollback: [item: { type: string; name?: string; path?: string; target_version?: string }]
}>()

// Collapsible section state: collapsedSections[sectionKey] = true means collapsed
const collapsedSections = ref<Record<string, boolean>>({})

function toggleSection(key: string) {
  collapsedSections.value[key] = !collapsedSections.value[key]
}

function isCollapsed(key: string): boolean {
  return collapsedSections.value[key] ?? false
}

// Auto-collapse sections with > 20 items
function shouldAutoCollapse(count: number): boolean {
  return count > 20
}

function rollbackPackage(name: string, fromVersion: string) {
  emit('rollback', { type: 'package', name, target_version: fromVersion })
}

function rollbackConfig(path: string) {
  emit('rollback', { type: 'config', path })
}

function rollbackService(name: string) {
  emit('rollback', { type: 'service', name })
}
</script>

<template>
  <div>
    <!-- Packages Tab -->
    <div v-if="activeTab === 'packages'">
      <div v-if="!diff.packages || (diff.packages.added.length === 0 && diff.packages.removed.length === 0 && diff.packages.upgraded.length === 0)"
        class="text-gray-400 text-sm py-4">
        无软件包变更
      </div>

      <!-- Added -->
      <div v-if="diff.packages?.added.length" class="mb-4">
        <button
          @click="toggleSection('pkg-added')"
          class="flex items-center gap-1 text-sm font-medium text-green-700 mb-2 hover:text-green-900"
        >
          <span class="text-xs transition-transform" :class="isCollapsed('pkg-added') ? '' : 'rotate-90'">▶</span>
          新增包 ({{ diff.packages.added.length }})
          <span v-if="diff.packages.added.length > 20" class="text-xs text-gray-400 ml-1">点击展开/折叠</span>
        </button>
        <div v-if="!isCollapsed('pkg-added')"
          v-for="pkg in diff.packages.added" :key="pkg.name"
          class="flex items-center gap-2 px-3 py-1.5 bg-green-50 rounded mb-1">
          <span class="text-green-600 font-mono text-sm">+</span>
          <span class="text-sm font-medium">{{ pkg.name }}</span>
          <span class="text-xs text-gray-500">{{ pkg.version }}</span>
        </div>
      </div>

      <!-- Removed -->
      <div v-if="diff.packages?.removed.length" class="mb-4">
        <button
          @click="toggleSection('pkg-removed')"
          class="flex items-center gap-1 text-sm font-medium text-red-700 mb-2 hover:text-red-900"
        >
          <span class="text-xs transition-transform" :class="isCollapsed('pkg-removed') ? '' : 'rotate-90'">▶</span>
          删除包 ({{ diff.packages.removed.length }})
        </button>
        <div v-if="!isCollapsed('pkg-removed')"
          v-for="pkg in diff.packages.removed" :key="pkg.name"
          class="flex items-center gap-2 px-3 py-1.5 bg-red-50 rounded mb-1">
          <span class="text-red-600 font-mono text-sm">-</span>
          <span class="text-sm font-medium">{{ pkg.name }}</span>
          <span class="text-xs text-gray-500">{{ pkg.version }}</span>
        </div>
      </div>

      <!-- Upgraded -->
      <div v-if="diff.packages?.upgraded.length" class="mb-4">
        <h4 class="text-sm font-medium text-yellow-700 mb-2">版本变更 ({{ diff.packages.upgraded.length }})</h4>
        <div v-for="pkg in diff.packages.upgraded" :key="pkg.name"
          class="flex items-center gap-2 px-3 py-1.5 bg-yellow-50 rounded mb-1">
          <span class="text-yellow-600 font-mono text-sm">~</span>
          <span class="text-sm font-medium">{{ pkg.name }}</span>
          <span class="text-xs text-gray-500">{{ pkg.fromVersion }} → {{ pkg.toVersion }}</span>
          <button v-if="snapshotId" @click="rollbackPackage(pkg.name, pkg.fromVersion)"
            class="ml-auto text-xs text-yellow-700 hover:text-yellow-900 hover:bg-yellow-100 px-2 py-0.5 rounded transition-colors"
            title="回滚到旧版本">
            回滚
          </button>
        </div>
      </div>
    </div>

    <!-- Services Tab -->
    <div v-if="activeTab === 'services'">
      <div v-if="!diff.services || (diff.services.added.length === 0 && diff.services.removed.length === 0 && diff.services.changed.length === 0)"
        class="text-gray-400 text-sm py-4">
        无服务变更
      </div>

      <div v-if="diff.services?.added.length" class="mb-4">
        <h4 class="text-sm font-medium text-green-700 mb-2">新增服务 ({{ diff.services.added.length }})</h4>
        <div v-for="svc in diff.services.added" :key="svc"
          class="flex items-center gap-2 px-3 py-1.5 bg-green-50 rounded mb-1">
          <span class="text-green-600 font-mono text-sm">+</span>
          <span class="text-sm font-medium">{{ svc }}</span>
        </div>
      </div>

      <div v-if="diff.services?.removed.length" class="mb-4">
        <h4 class="text-sm font-medium text-red-700 mb-2">删除服务 ({{ diff.services.removed.length }})</h4>
        <div v-for="svc in diff.services.removed" :key="svc"
          class="flex items-center gap-2 px-3 py-1.5 bg-red-50 rounded mb-1">
          <span class="text-red-600 font-mono text-sm">-</span>
          <span class="text-sm font-medium">{{ svc }}</span>
        </div>
      </div>

      <div v-if="diff.services?.changed.length" class="mb-4">
        <h4 class="text-sm font-medium text-yellow-700 mb-2">状态变更 ({{ diff.services.changed.length }})</h4>
        <div v-for="svc in diff.services.changed" :key="svc.name"
          class="flex items-center gap-2 px-3 py-1.5 bg-yellow-50 rounded mb-1">
          <span class="text-yellow-600 font-mono text-sm">~</span>
          <span class="text-sm font-medium">{{ svc.name }}</span>
          <span class="text-xs text-gray-500">
            {{ svc.fromStatus }}→{{ svc.toStatus }}
            <span v-if="svc.fromEnabled !== svc.toEnabled">
              ({{ svc.fromEnabled ? 'enabled' : 'disabled' }} → {{ svc.toEnabled ? 'enabled' : 'disabled' }})
            </span>
          </span>
          <button v-if="snapshotId && !svc.toEnabled" @click="rollbackService(svc.name)"
            class="ml-auto text-xs text-yellow-700 hover:text-yellow-900 hover:bg-yellow-100 px-2 py-0.5 rounded transition-colors"
            title="重新启用服务">
            回滚
          </button>
        </div>
      </div>
    </div>

    <!-- Ports Tab -->
    <div v-if="activeTab === 'ports'">
      <div v-if="!diff.ports || (diff.ports.added.length === 0 && diff.ports.removed.length === 0)"
        class="text-gray-400 text-sm py-4">
        无端口变更
      </div>

      <div v-if="diff.ports?.added.length" class="mb-4">
        <h4 class="text-sm font-medium text-green-700 mb-2">新开放端口 ({{ diff.ports.added.length }})</h4>
        <div v-for="port in diff.ports.added" :key="port"
          class="flex items-center gap-2 px-3 py-1.5 bg-green-50 rounded mb-1">
          <span class="text-green-600 font-mono text-sm">+</span>
          <span class="text-sm font-mono">{{ port }}</span>
          <span class="text-xs text-gray-500">⚠️ 高风险端口</span>
        </div>
      </div>

      <div v-if="diff.ports?.removed.length" class="mb-4">
        <h4 class="text-sm font-medium text-red-700 mb-2">关闭端口 ({{ diff.ports.removed.length }})</h4>
        <div v-for="port in diff.ports.removed" :key="port"
          class="flex items-center gap-2 px-3 py-1.5 bg-red-50 rounded mb-1">
          <span class="text-red-600 font-mono text-sm">-</span>
          <span class="text-sm font-mono">{{ port }}</span>
        </div>
      </div>
    </div>

    <!-- Configs Tab -->
    <div v-if="activeTab === 'configs'">
      <div v-if="!diff.configs || (diff.configs.added.length === 0 && diff.configs.removed.length === 0 && diff.configs.changed.length === 0)"
        class="text-gray-400 text-sm py-4">
        无配置文件变更
      </div>

      <div v-if="diff.configs?.added.length" class="mb-4">
        <button
          @click="toggleSection('cfg-added')"
          class="flex items-center gap-1 text-sm font-medium text-green-700 mb-2 hover:text-green-900"
        >
          <span class="text-xs transition-transform" :class="isCollapsed('cfg-added') ? '' : 'rotate-90'">▶</span>
          新增配置文件 ({{ diff.configs.added.length }})
        </button>
        <div v-if="!isCollapsed('cfg-added')"
          v-for="path in diff.configs.added" :key="path"
          class="flex items-center gap-2 px-3 py-1.5 bg-green-50 rounded mb-1">
          <span class="text-green-600 font-mono text-sm">+</span>
          <span class="text-sm font-mono">{{ path }}</span>
        </div>
      </div>

      <div v-if="diff.configs?.changed.length" class="mb-4">
        <h4 class="text-sm font-medium text-yellow-700 mb-2">内容变更 ({{ diff.configs.changed.length }})</h4>
        <div v-for="path in diff.configs.changed" :key="path"
          class="flex items-center gap-2 px-3 py-1.5 bg-yellow-50 rounded mb-1">
          <span class="text-yellow-600 font-mono text-sm">~</span>
          <span class="text-sm font-mono">{{ path }}</span>
          <button v-if="snapshotId" @click="rollbackConfig(path)"
            class="ml-auto text-xs text-yellow-700 hover:text-yellow-900 hover:bg-yellow-100 px-2 py-0.5 rounded transition-colors"
            title="恢复旧版本配置">
            回滚
          </button>
        </div>
      </div>

      <div v-if="diff.configs?.removed.length" class="mb-4">
        <h4 class="text-sm font-medium text-red-700 mb-2">删除配置文件 ({{ diff.configs.removed.length }})</h4>
        <div v-for="path in diff.configs.removed" :key="path"
          class="flex items-center gap-2 px-3 py-1.5 bg-red-50 rounded mb-1">
          <span class="text-red-600 font-mono text-sm">-</span>
          <span class="text-sm font-mono">{{ path }}</span>
        </div>
      </div>
    </div>

    <!-- Docker Tab -->
    <div v-if="activeTab === 'docker'">
      <div v-if="!diff.docker || (diff.docker.containersAdded.length === 0 && diff.docker.containersRemoved.length === 0 && diff.docker.containersChanged.length === 0)"
        class="text-gray-400 text-sm py-4">
        无 Docker 容器变更
      </div>

      <div v-if="diff.docker?.containersAdded.length" class="mb-4">
        <h4 class="text-sm font-medium text-green-700 mb-2">新增容器 ({{ diff.docker.containersAdded.length }})</h4>
        <div v-for="name in diff.docker.containersAdded" :key="name"
          class="flex items-center gap-2 px-3 py-1.5 bg-green-50 rounded mb-1">
          <span class="text-green-600 font-mono text-sm">+</span>
          <span class="text-sm font-medium">{{ name }}</span>
        </div>
      </div>

      <div v-if="diff.docker?.containersChanged.length" class="mb-4">
        <h4 class="text-sm font-medium text-yellow-700 mb-2">状态变更 ({{ diff.docker.containersChanged.length }})</h4>
        <div v-for="name in diff.docker.containersChanged" :key="name"
          class="flex items-center gap-2 px-3 py-1.5 bg-yellow-50 rounded mb-1">
          <span class="text-yellow-600 font-mono text-sm">~</span>
          <span class="text-sm font-medium">{{ name }}</span>
        </div>
      </div>

      <div v-if="diff.docker?.containersRemoved.length" class="mb-4">
        <h4 class="text-sm font-medium text-red-700 mb-2">删除容器 ({{ diff.docker.containersRemoved.length }})</h4>
        <div v-for="name in diff.docker.containersRemoved" :key="name"
          class="flex items-center gap-2 px-3 py-1.5 bg-red-50 rounded mb-1">
          <span class="text-red-600 font-mono text-sm">-</span>
          <span class="text-sm font-medium">{{ name }}</span>
        </div>
      </div>
    </div>

    <!-- Crontab Tab -->
    <div v-if="activeTab === 'crontab'">
      <div v-if="!diff.crontab || (diff.crontab.added.length === 0 && diff.crontab.removed.length === 0)"
        class="text-gray-400 text-sm py-4">
        无定时任务变更
      </div>

      <div v-if="diff.crontab?.added.length" class="mb-4">
        <h4 class="text-sm font-medium text-green-700 mb-2">新增定时任务 ({{ diff.crontab.added.length }})</h4>
        <div v-for="entry in diff.crontab.added" :key="entry"
          class="flex items-center gap-2 px-3 py-1.5 bg-green-50 rounded mb-1">
          <span class="text-green-600 font-mono text-sm">+</span>
          <span class="text-sm font-mono">{{ entry }}</span>
        </div>
      </div>

      <div v-if="diff.crontab?.removed.length" class="mb-4">
        <h4 class="text-sm font-medium text-red-700 mb-2">删除定时任务 ({{ diff.crontab.removed.length }})</h4>
        <div v-for="entry in diff.crontab.removed" :key="entry"
          class="flex items-center gap-2 px-3 py-1.5 bg-red-50 rounded mb-1">
          <span class="text-red-600 font-mono text-sm">-</span>
          <span class="text-sm font-mono">{{ entry }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
