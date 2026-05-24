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
      <!-- Filters -->
      <div class="glass-panel p-4 rounded-xl flex flex-wrap gap-4 items-end">
        <div class="flex-1 min-w-[200px]">
          <label class="text-[12px] font-bold text-outline block mb-1">操作类型</label>
          <input v-model="logFilters.action" placeholder="搜索操作..." class="w-full bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-[14px]" />
        </div>
        <div>
          <label class="text-[12px] font-bold text-outline block mb-1">开始时间</label>
          <input v-model="logFilters.since" type="datetime-local" class="bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-[14px]" />
        </div>
        <div>
          <label class="text-[12px] font-bold text-outline block mb-1">结束时间</label>
          <input v-model="logFilters.until" type="datetime-local" class="bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-[14px]" />
        </div>
        <button @click="searchLogs" class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all">筛选</button>
        <button @click="resetFilters" class="px-4 py-2 text-outline text-[12px] font-bold hover:bg-surface-container-high rounded-lg transition-all">重置</button>
      </div>
      <!-- Log List -->
      <div v-for="log in auditLogs" :key="log.id" class="glass-panel p-4 rounded-xl flex items-center gap-4">
        <span class="material-symbols-outlined text-primary">receipt_long</span>
        <div class="flex-1">
          <p class="text-[14px]">{{ log.action }}</p>
          <p class="text-[12px] text-outline">{{ log.userName || '未知用户' }} - {{ log.createdAt }}</p>
        </div>
      </div>
      <div v-if="!auditLogs.length" class="glass-panel p-8 rounded-xl text-center text-on-surface-variant">暂无审计日志</div>
    </div>

    <!-- API Keys -->
    <div v-if="activeTab === 'API 密钥'" class="space-y-4">
      <div class="glass-panel p-6 rounded-xl">
        <h3 class="text-[18px] font-semibold mb-4">API 密钥管理</h3>
        <div class="space-y-3">
          <div v-for="key in apiKeys" :key="key.id || key.name" class="flex items-center justify-between p-3 rounded-lg border border-outline-variant/20">
            <div>
              <p class="text-[14px] font-bold">{{ key.name }}</p>
              <p class="text-[12px] text-outline font-[Geist]">{{ key.key ? key.key.slice(0, 8) : '' }}...</p>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-[12px] text-outline">{{ key.createdAt || '' }}</span>
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

    <!-- AI Config -->
    <div v-if="activeTab === 'AI 配置'" class="space-y-4">
      <div class="glass-panel p-6 rounded-xl space-y-6">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]">smart_toy</span>
          <div>
            <h3 class="text-[18px] font-semibold">AI 模型配置</h3>
            <p class="text-[12px] text-on-surface-variant">配置 AI 分析所使用的模型和 API 参数。兼容 OpenAI API 格式。</p>
          </div>
        </div>

        <!-- Enable Toggle -->
        <div class="flex items-center justify-between p-4 rounded-lg bg-surface-container border border-outline-variant/20">
          <div>
            <p class="text-[14px] font-bold">启用 AI 功能</p>
            <p class="text-[12px] text-on-surface-variant">关闭后所有 AI 分析功能将使用规则引擎替代</p>
          </div>
          <button @click="aiForm.enabled = !aiForm.enabled"
            class="w-12 h-6 rounded-full relative transition-colors"
            :class="aiForm.enabled ? 'bg-primary' : 'bg-outline-variant'">
            <div class="absolute top-0.5 w-5 h-5 bg-white rounded-full shadow transition-all"
              :class="aiForm.enabled ? 'right-0.5' : 'left-0.5'"></div>
          </button>
        </div>

        <!-- Base URL -->
        <div class="space-y-1.5">
          <label class="text-[12px] font-bold text-on-surface-variant">API Base URL</label>
          <input v-model="aiForm.baseUrl"
            class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]"
            placeholder="https://api.openai.com/v1" />
          <p class="text-[11px] text-outline">兼容 OpenAI API 格式的端点地址</p>
        </div>

        <!-- API Key -->
        <div class="space-y-1.5">
          <label class="text-[12px] font-bold text-on-surface-variant">API Key</label>
          <div class="relative">
            <input v-model="aiForm.apiKey" :type="showApiKey ? 'text' : 'password'"
              class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 pr-10 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]"
              placeholder="sk-..." />
            <button @click="showApiKey = !showApiKey" class="absolute right-2 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface transition-colors">
              <span class="material-symbols-outlined text-[18px]">{{ showApiKey ? 'visibility_off' : 'visibility' }}</span>
            </button>
          </div>
          <p class="text-[11px] text-outline">留空则不更新密钥。当前: {{ aiForm.apiKey.includes('*') ? aiForm.apiKey : '未设置' }}</p>
        </div>

        <!-- Model -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">模型名称</label>
            <input v-model="aiForm.model"
              class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]"
              placeholder="gpt-4o" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">Max Tokens</label>
            <input v-model.number="aiForm.maxTokens" type="number"
              class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]" />
          </div>
        </div>

        <!-- Temperature -->
        <div class="space-y-1.5">
          <div class="flex items-center justify-between">
            <label class="text-[12px] font-bold text-on-surface-variant">Temperature</label>
            <span class="text-[12px] font-[Geist] text-primary">{{ aiForm.temperature.toFixed(1) }}</span>
          </div>
          <input v-model.number="aiForm.temperature" type="range" min="0" max="2" step="0.1"
            class="w-full h-2 bg-surface-container-high rounded-full appearance-none cursor-pointer accent-primary" />
          <div class="flex justify-between text-[10px] text-outline">
            <span>精确 (0)</span>
            <span>平衡 (1)</span>
            <span>创意 (2)</span>
          </div>
        </div>

        <!-- Save Button -->
        <div class="flex justify-end pt-2">
          <button @click="saveAiConfig" :disabled="savingAi"
            class="px-6 py-2.5 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary/90 transition-all disabled:opacity-50">
            {{ savingAi ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useModalStore } from '@/stores/modal'
import { useToastStore } from '@/stores/toast'
import { settingsApi } from '@/api/settings'
import GenerateKeyModal from '@/components/modals/GenerateKeyModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import type { AiConfig } from '@/types'

const modal = useModalStore()
const toast = useToastStore()

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
const tabs = ['审计日志', 'API 密钥', 'AI 配置']

const logFilters = ref({ action: '', since: '', until: '' })

async function searchLogs() {
  try {
    const params: Record<string, any> = { page: 0, size: 50 }
    if (logFilters.value.action) params.action = logFilters.value.action
    if (logFilters.value.since) params.since = logFilters.value.since
    if (logFilters.value.until) params.until = logFilters.value.until
    const res = await settingsApi.searchAuditLogs(params)
    auditLogs.value = res?.content || res || []
  } catch (e) {
    console.error('Failed to search audit logs', e)
  }
}

function resetFilters() {
  logFilters.value = { action: '', since: '', until: '' }
  loadAuditLogs()
}

async function loadAuditLogs() {
  try {
    const logsRes = await settingsApi.getAuditLogs()
    auditLogs.value = logsRes || []
  } catch (e) {
    console.error('Failed to load audit logs', e)
  }
}

import type { AuditLog, ApiKey } from '@/types'

const auditLogs = ref<AuditLog[]>([])
const apiKeys = ref<ApiKey[]>([])

// AI Config
const aiForm = ref<AiConfig>({
  enabled: true,
  baseUrl: '',
  apiKey: '',
  model: '',
  maxTokens: 4096,
  temperature: 0.7,
})
const showApiKey = ref(false)
const savingAi = ref(false)

async function loadAiConfig() {
  try {
    const config = await settingsApi.getAiConfig()
    if (config) {
      aiForm.value = { ...aiForm.value, ...config }
    }
  } catch (e) {
    console.error('Failed to load AI config', e)
  }
}

async function saveAiConfig() {
  savingAi.value = true
  try {
    await settingsApi.updateAiConfig(aiForm.value)
    toast.success('AI 配置已保存')
    // Reload to get masked key
    await loadAiConfig()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingAi.value = false
  }
}

onMounted(async () => {
  try {
    const [logsRes, keysRes] = await Promise.all([
      settingsApi.getAuditLogs(),
      settingsApi.getApiKeys(),
    ])
    auditLogs.value = logsRes || []
    apiKeys.value = keysRes || []
  } catch (e) {
    console.error('Failed to load settings data', e)
  }
  loadAiConfig()
})
</script>
