<template>
  <div class="p-[24px] space-y-[24px] pb-20">
    <!-- Header -->
    <section class="flex justify-between items-center">
      <div>
        <h2 class="text-[28px] font-semibold text-on-surface font-[Geist]">服务器管理</h2>
        <p class="text-[14px] text-on-surface-variant">管理和监控所有已注册的服务器</p>
      </div>
      <button @click="router.push('/onboarding')" class="px-4 py-2 rounded-lg bg-primary text-white text-[12px] font-bold flex items-center gap-2 hover:opacity-90 shadow-lg shadow-primary/20 transition-all">
        <span class="material-symbols-outlined text-lg">add</span>
        添加服务器
      </button>
    </section>

    <!-- Loading -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div v-for="i in 3" :key="i" class="glass-panel rounded-xl p-5 animate-pulse">
        <div class="h-6 w-32 bg-surface-container-highest rounded mb-4"></div>
        <div class="h-4 w-48 bg-surface-container-highest rounded mb-2"></div>
        <div class="h-4 w-24 bg-surface-container-highest rounded"></div>
      </div>
    </div>

    <!-- Server Grid -->
    <div v-else-if="servers.length" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="server in servers"
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
            <span>已运行: {{ server.uptime }}</span>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { serversApi } from '@/api/servers'

const router = useRouter()
const loading = ref(true)
const servers = ref<any[]>([])

onMounted(async () => {
  try {
    const res = await serversApi.getAll()
    servers.value = (res as any).data || res || []
  } catch (e) {
    console.error('Failed to load servers', e)
  } finally {
    loading.value = false
  }
})
</script>
