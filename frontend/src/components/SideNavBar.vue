<template>
  <!-- Mobile overlay -->
  <div v-if="layout.sidebarOpen" class="fixed inset-0 bg-black/50 z-30 md:hidden" @click="layout.closeSidebar()"></div>

  <aside
    class="flex flex-col h-screen w-60 left-0 fixed z-40 bg-surface-container-low border-r border-outline-variant/20 py-6 space-y-2 transition-transform duration-300"
    :class="layout.sidebarOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'"
  >
    <div class="px-6 mb-8">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-white shadow-lg shadow-primary/20">
          <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1;">restore</span>
        </div>
        <div>
          <h1 class="font-[Geist] text-[24px] font-extrabold text-on-surface leading-none">ChronoVault</h1>
          <p class="text-[12px] font-medium text-primary tracking-widest uppercase mt-0.5">服务器时间机器</p>
        </div>
      </div>
    </div>
    <nav class="flex-1 px-3 space-y-1">
      <router-link
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-3 px-4 py-3 rounded-xl transition-colors group"
        :class="isActive(item.path)
          ? 'bg-primary-container/10 text-primary border-r-2 border-primary'
          : 'text-on-surface-variant hover:bg-surface-container-high'"
        @click="layout.closeSidebar()"
      >
        <span
          class="material-symbols-outlined text-[20px] group-hover:translate-x-1 transition-transform"
          :style="isActive(item.path) ? 'font-variation-settings: FILL 1' : ''"
        >{{ item.icon }}</span>
        <span class="text-[12px] font-medium">{{ item.label }}</span>
      </router-link>

      <div class="pt-4 pb-2 px-4">
        <span class="text-[10px] font-bold text-outline uppercase tracking-[0.2em]">智能分析</span>
      </div>
      <router-link
        v-for="item in intelligenceItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-3 px-4 py-3 rounded-xl transition-colors group"
        :class="isActive(item.path)
          ? 'bg-primary-container/10 text-primary border-r-2 border-primary'
          : 'text-on-surface-variant hover:bg-surface-container-high'"
        @click="layout.closeSidebar()"
      >
        <span
          class="material-symbols-outlined text-[20px] group-hover:translate-x-1 transition-transform"
          :style="isActive(item.path) ? 'font-variation-settings: FILL 1' : ''"
        >{{ item.icon }}</span>
        <span class="text-[12px] font-medium">{{ item.label }}</span>
      </router-link>
    </nav>

    <!-- Bottom: Guide Link -->
    <div class="px-3 mt-auto">
      <router-link
        to="/onboarding"
        class="flex items-center gap-3 px-4 py-3 rounded-xl transition-colors group text-on-surface-variant hover:bg-surface-container-high"
        @click="layout.closeSidebar()"
      >
        <span class="material-symbols-outlined text-[20px] group-hover:translate-x-1 transition-transform">help</span>
        <span class="text-[12px] font-medium">使用指南</span>
      </router-link>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useLayoutStore } from '@/stores/layout'

const route = useRoute()
const layout = useLayoutStore()

const navItems = [
  { path: '/dashboard', icon: 'dashboard', label: '总览' },
  { path: '/servers', icon: 'dns', label: '服务器' },
  { path: '/snapshots', icon: 'history', label: '快照' },
  { path: '/recovery', icon: 'restore', label: '恢复' },
  { path: '/storage', icon: 'storage', label: '存储' },
]

const intelligenceItems = [
  { path: '/ai-insights', icon: 'psychology', label: 'AI 洞察' },
  { path: '/risk', icon: 'security', label: '风险中心' },
  { path: '/alerts', icon: 'notifications_active', label: '告警' },
  { path: '/team', icon: 'groups', label: '团队' },
  { path: '/settings', icon: 'settings', label: '设置' },
]

function isActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}
</script>
