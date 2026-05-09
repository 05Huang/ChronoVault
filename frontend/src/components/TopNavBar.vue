<template>
  <header class="sticky top-0 z-50 h-16 flex justify-between items-center w-full px-[24px] bg-surface-bright/80 backdrop-blur-xl border-b border-outline-variant/30 shadow-sm">
    <div class="flex items-center gap-4">
      <div class="md:hidden w-8 h-8 rounded bg-primary flex items-center justify-center text-white cursor-pointer" @click="layout.toggleSidebar()">
        <span class="material-symbols-outlined text-sm">menu</span>
      </div>
      <div class="flex items-center gap-2">
        <span class="font-[Geist] text-[24px] font-bold tracking-tighter text-on-surface">{{ pageTitle }}</span>
        <template v-if="breadcrumb">
          <span class="text-outline-variant">/</span>
          <span class="text-[14px] text-outline">{{ breadcrumb }}</span>
        </template>
      </div>
    </div>
    <div class="flex items-center gap-4">
      <div class="relative hidden sm:block">
        <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
        <input
          class="bg-surface-container-low border-none rounded-full pl-10 pr-4 py-1.5 text-[14px] focus:ring-2 focus:ring-primary/20 w-64"
          placeholder="搜索快照、版本或标记..."
          type="text"
        />
      </div>
      <button class="w-10 h-10 flex items-center justify-center rounded-full hover:bg-surface-container-high transition-all text-on-surface-variant active:scale-90" @click="$router.push('/alerts')">
        <span class="material-symbols-outlined">notifications</span>
      </button>
      <button class="w-10 h-10 flex items-center justify-center rounded-full hover:bg-surface-container-high transition-all text-on-surface-variant active:scale-90" @click="$router.push('/ai-insights')">
        <span class="material-symbols-outlined">search_check</span>
      </button>
      <div class="w-8 h-8 rounded-full overflow-hidden border border-outline-variant/50 cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all bg-primary-container flex items-center justify-center text-on-primary-container text-xs font-bold" @click="$router.push('/settings')">
        A
      </div>
      <button class="w-10 h-10 flex items-center justify-center rounded-full hover:bg-error/10 transition-all text-error active:scale-90" title="退出登录" @click="handleLogout">
        <span class="material-symbols-outlined">logout</span>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useLayoutStore } from '@/stores/layout'

const route = useRoute()
const auth = useAuthStore()
const layout = useLayoutStore()

function handleLogout() {
  auth.logout()
}

const pageTitles: Record<string, { title: string; breadcrumb?: string }> = {
  '/dashboard': { title: '总览', breadcrumb: '仪表盘' },
  '/snapshots': { title: '快照', breadcrumb: '时间线视图' },
  '/recovery': { title: '恢复' },
  '/storage': { title: '存储' },
  '/team': { title: '团队' },
  '/settings': { title: '设置' },
  '/risk': { title: '风险中心' },
  '/alerts': { title: '告警' },
  '/ai-insights': { title: 'AI 洞察' },
  '/onboarding': { title: '引导' },
}

const pageTitle = computed(() => pageTitles[route.path]?.title || 'ChronoVault')
const breadcrumb = computed(() => pageTitles[route.path]?.breadcrumb)
</script>
