<template>
  <div class="p-[24px] space-y-[40px]">
    <!-- Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-[32px] font-semibold text-on-surface">设置</h2>
        <p class="text-on-surface-variant text-[16px] mt-1">审计日志和系统配置。</p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex gap-2 border-b border-outline-variant/20 pb-2">
      <button v-for="tab in tabs" :key="tab" @click="activeTab = tab"
        class="px-4 py-2 text-[12px] font-bold rounded-lg transition-all"
        :class="activeTab === tab ? 'bg-primary text-white' : 'text-outline hover:bg-surface-container-high'">
        {{ tab }}
      </button>
    </div>

    <!-- Audit Logs -->
    <div v-if="activeTab === '审计日志'" class="space-y-4">
      <div v-for="log in auditLogs" :key="log.time" class="glass-panel p-4 rounded-xl flex items-center gap-4">
        <span class="material-symbols-outlined text-primary">{{ log.icon }}</span>
        <div class="flex-1">
          <p class="text-[14px]">{{ log.action }}</p>
          <p class="text-[12px] text-outline">{{ log.user }} - {{ log.time }}</p>
        </div>
      </div>
    </div>

    <!-- API Keys -->
    <div v-if="activeTab === 'API 密钥'" class="space-y-4">
      <div class="glass-panel p-6 rounded-xl">
        <h3 class="text-[18px] font-semibold mb-4">API 密钥管理</h3>
        <div class="space-y-3">
          <div v-for="key in apiKeys" :key="key.id || key.name" class="flex items-center justify-between p-3 rounded-lg border border-outline-variant/20">
            <div>
              <p class="text-[14px] font-bold">{{ key.name }}</p>
              <p class="text-[12px] text-outline font-[Geist]">{{ key.keyPrefix || key.prefix || '' }}...</p>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-[12px] text-outline">{{ key.createdAt || key.created || '' }}</span>
              <button @click="openDeleteKey(key.id, key.name)" class="text-error hover:bg-error/10 p-1 rounded transition-colors">
                <span class="material-symbols-outlined text-[18px]">delete</span>
              </button>
            </div>
          </div>
        </div>
        <button @click="openGenerateKey" class="mt-4 px-4 py-2 border-2 border-dashed border-outline-variant/50 rounded-lg text-[12px] font-bold text-outline hover:border-primary/50 hover:text-primary transition-all">
          + 生成新密钥
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useModalStore } from '@/stores/modal'
import { settingsApi } from '@/api/settings'
import GenerateKeyModal from '@/components/modals/GenerateKeyModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'

const modal = useModalStore()

function openGenerateKey() {
  modal.open({ component: GenerateKeyModal, title: '生成新 API 密钥' })
}

function openDeleteKey(id: number, name: string) {
  modal.open({
    component: ConfirmModal,
    title: '删除 API 密钥',
    props: {
      message: `确定要删除密钥「${name}」吗？使用此密钥的所有集成将立即失效，此操作不可撤销。`,
      confirmText: '确认删除',
      confirmClass: 'bg-error hover:bg-error/90',
      successMessage: `密钥 ${name} 已删除`,
      onConfirm: async () => {
        await settingsApi.deleteKey(id)
        apiKeys.value = apiKeys.value.filter((k: any) => k.id !== id)
      },
    },
  })
}

const activeTab = ref('审计日志')
const tabs = ['审计日志', 'API 密钥']

const auditLogs = ref<any[]>([])
const apiKeys = ref<any[]>([])

onMounted(async () => {
  try {
    const [logsRes, keysRes] = await Promise.all([
      settingsApi.getAuditLogs(),
      settingsApi.getApiKeys(),
    ])
    auditLogs.value = (logsRes as any).data || logsRes || []
    apiKeys.value = (keysRes as any).data || keysRes || []
  } catch (e) {
    console.error('Failed to load settings data', e)
  }
})
</script>
