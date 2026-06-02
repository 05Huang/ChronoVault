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

    <!-- Scheduled Backups -->
    <div v-if="activeTab === '定时备份'" class="space-y-4">
      <div class="glass-panel p-6 rounded-xl">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-[18px] font-semibold">定时备份任务</h3>
          <button @click="showBackupForm = !showBackupForm"
            class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">{{ showBackupForm ? 'close' : 'add' }}</span>
            {{ showBackupForm ? '取消' : '新建任务' }}
          </button>
        </div>

        <!-- Create Form -->
        <div v-if="showBackupForm" class="bg-surface-container/50 rounded-xl p-4 border border-outline-variant/20 space-y-3 mb-4">
          <div class="grid grid-cols-2 gap-3">
            <input v-model="backupForm.name" class="px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="任务名称" />
            <select v-model.number="backupForm.serverId" class="px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none appearance-none">
              <option :value="0" disabled>选择服务器</option>
              <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }} ({{ s.ip }})</option>
            </select>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <input v-model="backupForm.cronExpression" class="px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] font-[Geist] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="0 2 * * *" />
            <div class="flex items-center gap-2">
              <label class="text-[12px] text-on-surface-variant">启用</label>
              <button @click="backupForm.enabled = !backupForm.enabled"
                class="w-10 h-5 rounded-full relative transition-colors"
                :class="backupForm.enabled ? 'bg-primary' : 'bg-outline-variant'">
                <div class="absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all"
                  :class="backupForm.enabled ? 'right-0.5' : 'left-0.5'"></div>
              </button>
            </div>
          </div>
          <button @click="saveScheduledBackup" :disabled="!backupForm.name || !backupForm.serverId"
            class="px-4 py-2 bg-primary text-white rounded-lg text-[11px] font-bold hover:bg-primary/90 transition-all disabled:opacity-50">
            创建
          </button>
        </div>

        <!-- Backup List -->
        <div v-if="scheduledBackups.length === 0" class="text-center py-8">
          <span class="material-symbols-outlined text-outline text-[48px] mb-2">schedule</span>
          <p class="text-[14px] text-on-surface-variant">暂无定时备份任务</p>
        </div>

        <div v-else class="space-y-3">
          <div v-for="backup in scheduledBackups" :key="backup.id"
            class="flex items-center gap-4 p-4 rounded-lg border border-outline-variant/20 hover:bg-surface-container/30 transition-colors">
            <button @click="toggleScheduledBackup(backup.id)"
              class="w-10 h-5 rounded-full relative transition-colors shrink-0"
              :class="backup.enabled ? 'bg-primary' : 'bg-outline-variant'">
              <div class="absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all"
                :class="backup.enabled ? 'right-0.5' : 'left-0.5'"></div>
            </button>
            <div class="flex-1 min-w-0">
              <p class="text-[14px] font-bold">{{ backup.name }}</p>
              <p class="text-[11px] text-outline">
                服务器 #{{ backup.serverId }} · {{ backup.cronExpression }}
                <span v-if="backup.lastRunAt"> · 上次: {{ new Date(backup.lastRunAt).toLocaleString('zh-CN') }}</span>
              </p>
            </div>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold"
              :class="backup.lastStatus === 'SUCCESS' ? 'bg-green-500/10 text-green-600' : backup.lastStatus === 'FAILED' ? 'bg-error/10 text-error' : 'bg-outline/10 text-outline'">
              {{ backup.lastStatus || '待执行' }}
            </span>
            <button @click="deleteScheduledBackup(backup.id)"
              class="p-1.5 rounded-lg hover:bg-error/10 text-outline hover:text-error transition-colors" title="删除">
              <span class="material-symbols-outlined text-[14px]">delete</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Retention Policies -->
    <div v-if="activeTab === '保留策略'" class="space-y-4">
      <div class="glass-panel p-6 rounded-xl">
        <h3 class="text-[18px] font-semibold mb-4">保留策略管理</h3>
        <p class="text-[12px] text-on-surface-variant mb-4">配置快照保留策略，自动清理过期快照</p>
        <div class="text-center py-8">
          <span class="material-symbols-outlined text-outline text-[48px] mb-2">inventory_2</span>
          <p class="text-[14px] text-on-surface-variant">保留策略管理功能可通过 API 配置</p>
          <p class="text-[11px] text-outline mt-1">使用 GET /api/retention-policies 查看策略列表</p>
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

    <!-- Webhooks -->
    <div v-if="activeTab === 'Webhooks'" class="space-y-4">
      <!-- Create/Edit Form -->
      <div v-if="showWebhookForm" class="glass-panel p-6 rounded-xl space-y-4">
        <h3 class="text-[18px] font-bold">{{ editingWebhook ? '编辑 Webhook' : '创建 Webhook' }}</h3>
        <div class="space-y-3">
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">URL</label>
            <input v-model="webhookForm.url" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] font-[Geist] focus:outline-none focus:ring-2 focus:ring-primary/20" placeholder="https://example.com/webhook" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">Secret（用于 HMAC 签名验证）</label>
            <input v-model="webhookForm.secret" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] font-[Geist] focus:outline-none focus:ring-2 focus:ring-primary/20" placeholder="可选" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">订阅事件（逗号分隔）</label>
            <input v-model="webhookForm.events" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] font-[Geist] focus:outline-none focus:ring-2 focus:ring-primary/20" placeholder="SNAPSHOT_CREATED,SNAPSHOT_DELETED" />
            <div class="flex flex-wrap gap-1.5 mt-1">
              <span v-for="evt in ['SNAPSHOT_CREATED','SNAPSHOT_DELETED','SNAPSHOT_RESTORED','DRIFT_DETECTED','ALERT_FIRED','BACKUP_FAILED']" :key="evt"
                class="px-2 py-0.5 rounded-full text-[9px] font-bold bg-surface-container-highest text-outline cursor-pointer hover:bg-primary/10 hover:text-primary transition-colors"
                @click="webhookForm.events = webhookForm.events.includes(evt) ? webhookForm.events : (webhookForm.events ? webhookForm.events + ',' + evt : evt)">
                {{ evt }}
              </span>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <label class="text-[12px] font-bold text-on-surface-variant">启用</label>
            <button @click="webhookForm.enabled = !webhookForm.enabled"
              class="w-10 h-5 rounded-full relative transition-colors"
              :class="webhookForm.enabled ? 'bg-primary' : 'bg-outline-variant'">
              <div class="absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all"
                :class="webhookForm.enabled ? 'right-0.5' : 'left-0.5'"></div>
            </button>
          </div>
        </div>
        <div class="flex justify-end gap-2">
          <button @click="showWebhookForm = false; editingWebhook = null" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
          <button @click="saveWebhook" :disabled="!webhookForm.url"
            class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50">
            {{ editingWebhook ? '更新' : '创建' }}
          </button>
        </div>
      </div>

      <!-- Webhook List -->
      <div class="glass-panel p-6 rounded-xl">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-[18px] font-semibold">Webhook 端点</h3>
          <button @click="showWebhookForm = true; editingWebhook = null; webhookForm = { url: '', secret: '', events: 'SNAPSHOT_CREATED,SNAPSHOT_DELETED,SNAPSHOT_RESTORED', enabled: true }"
            class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">add</span>
            添加 Webhook
          </button>
        </div>

        <div v-if="webhooks.length === 0" class="text-center py-8">
          <span class="material-symbols-outlined text-outline text-[48px] mb-2">webhook</span>
          <p class="text-[14px] text-on-surface-variant">暂无 Webhook 端点</p>
        </div>

        <div v-else class="space-y-3">
          <div v-for="wh in webhooks" :key="wh.id"
            class="flex items-center gap-3 p-3 rounded-lg border border-outline-variant/20 hover:bg-surface-container/30 transition-colors">
            <span class="w-3 h-3 rounded-full shrink-0" :class="wh.enabled ? 'bg-green-500' : 'bg-outline'"></span>
            <div class="flex-1 min-w-0">
              <p class="text-[13px] font-bold truncate">{{ wh.url }}</p>
              <p class="text-[11px] text-outline truncate">{{ wh.events || '所有事件' }}</p>
            </div>
            <button @click="testWebhook(wh.id)" class="p-1.5 rounded-lg hover:bg-primary/10 text-primary transition-colors" title="测试">
              <span class="material-symbols-outlined text-[16px]">send</span>
            </button>
            <button @click="editWebhook(wh)" class="p-1.5 rounded-lg hover:bg-surface-container-high text-outline transition-colors" title="编辑">
              <span class="material-symbols-outlined text-[14px]">edit</span>
            </button>
            <button @click="deleteWebhook(wh.id)" class="p-1.5 rounded-lg hover:bg-error/10 text-outline hover:text-error transition-colors" title="删除">
              <span class="material-symbols-outlined text-[14px]">delete</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Verification Jobs -->
    <div v-if="activeTab === '验证任务'" class="space-y-4">
      <div class="glass-panel p-6 rounded-xl">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="text-[18px] font-semibold">备份验证任务</h3>
            <p class="text-[12px] text-on-surface-variant">定期验证备份数据的完整性</p>
          </div>
          <button @click="createVerificationJob"
            class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">add</span>
            添加任务
          </button>
        </div>

        <div v-if="verificationJobs.length === 0" class="text-center py-8">
          <span class="material-symbols-outlined text-outline text-[48px] mb-2">verified</span>
          <p class="text-[14px] text-on-surface-variant">暂无验证任务</p>
        </div>

        <div v-else class="space-y-3">
          <div v-for="job in verificationJobs" :key="job.id"
            class="flex items-center gap-4 p-4 rounded-lg border border-outline-variant/20 hover:bg-surface-container/30 transition-colors">
            <span class="material-symbols-outlined text-[20px]"
              :class="job.lastStatus === 'SUCCESS' ? 'text-green-500' : job.lastStatus === 'FAILED' ? 'text-error' : 'text-outline'">verified</span>
            <div class="flex-1 min-w-0">
              <p class="text-[14px] font-bold">服务器 #{{ job.serverId }}</p>
              <p class="text-[11px] text-outline">
                调度: {{ job.scheduleCron }}
                <span v-if="job.lastRunAt"> · 上次执行: {{ new Date(job.lastRunAt).toLocaleString('zh-CN') }}</span>
              </p>
              <p v-if="job.lastError" class="text-[11px] text-error mt-0.5">{{ job.lastError }}</p>
            </div>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold" :class="statusColors[job.lastStatus] || statusColors.PENDING">
              {{ job.lastStatus }}
            </span>
            <button @click="runVerificationJob(job.id)"
              class="p-1.5 rounded-lg hover:bg-primary/10 text-primary transition-colors" title="立即执行">
              <span class="material-symbols-outlined text-[16px]">play_arrow</span>
            </button>
            <button @click="deleteVerificationJob(job.id)"
              class="p-1.5 rounded-lg hover:bg-error/10 text-outline hover:text-error transition-colors" title="删除">
              <span class="material-symbols-outlined text-[14px]">delete</span>
            </button>
          </div>
        </div>
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
import { webhooksApi } from '@/api/webhooks'
import { verificationApi } from '@/api/verification'
import { scheduledBackupsApi } from '@/api/scheduledBackups'
import GenerateKeyModal from '@/components/modals/GenerateKeyModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import type { AiConfig, WebhookEndpoint, WebhookDeliveryLog, VerificationJob, ScheduledBackup } from '@/types'

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

// Scheduled backup state
const scheduledBackups = ref<ScheduledBackup[]>([])
const showBackupForm = ref(false)
const editingBackup = ref<ScheduledBackup | null>(null)
const backupForm = ref({ name: '', serverId: 0, cronExpression: '0 2 * * *', enabled: true })
const servers = ref<any[]>([])

async function loadScheduledBackups() {
  try {
    const res = await scheduledBackupsApi.getAll()
    scheduledBackups.value = res || []
  } catch (e) {
    console.error('Failed to load scheduled backups', e)
  }
}

async function loadServersForBackups() {
  try {
    const { serversApi } = await import('@/api/servers')
    const res = await serversApi.getAll()
    servers.value = res || []
  } catch (e) {
    console.error('Failed to load servers', e)
  }
}

async function saveScheduledBackup() {
  try {
    if (editingBackup.value) {
      // Toggle is separate; for now just toggle
      await scheduledBackupsApi.toggle(editingBackup.value.id)
      toast.success('定时备份已更新')
    } else {
      await scheduledBackupsApi.create(backupForm.value)
      toast.success('定时备份已创建')
    }
    editingBackup.value = null
    showBackupForm.value = false
    backupForm.value = { name: '', serverId: 0, cronExpression: '0 2 * * *', enabled: true }
    await loadScheduledBackups()
  } catch (e: any) {
    toast.error(e?.message || '保存失败')
  }
}

async function toggleScheduledBackup(id: number) {
  try {
    await scheduledBackupsApi.toggle(id)
    await loadScheduledBackups()
  } catch (e: any) {
    toast.error(e?.message || '操作失败')
  }
}

async function deleteScheduledBackup(id: number) {
  try {
    await scheduledBackupsApi.delete(id)
    scheduledBackups.value = scheduledBackups.value.filter(b => b.id !== id)
    toast.success('定时备份已删除')
  } catch (e: any) {
    toast.error(e?.message || '删除失败')
  }
}

const activeTab = ref('审计日志')
const tabs = ['审计日志', '定时备份', '保留策略', 'API 密钥', 'AI 配置', 'Webhooks', '验证任务']

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

// Webhook state
const webhooks = ref<WebhookEndpoint[]>([])
const webhookLogs = ref<WebhookDeliveryLog[]>([])
const showWebhookForm = ref(false)
const editingWebhook = ref<WebhookEndpoint | null>(null)
const webhookForm = ref({ url: '', secret: '', events: 'SNAPSHOT_CREATED,SNAPSHOT_DELETED,SNAPSHOT_RESTORED', enabled: true })

async function loadWebhooks() {
  try {
    const res = await webhooksApi.getAll()
    webhooks.value = res || []
  } catch (e) {
    console.error('Failed to load webhooks', e)
  }
}

async function saveWebhook() {
  try {
    if (editingWebhook.value) {
      await webhooksApi.update(editingWebhook.value.id, webhookForm.value)
      toast.success('Webhook 已更新')
    } else {
      await webhooksApi.create(webhookForm.value)
      toast.success('Webhook 已创建')
    }
    editingWebhook.value = null
    webhookForm.value = { url: '', secret: '', events: 'SNAPSHOT_CREATED,SNAPSHOT_DELETED,SNAPSHOT_RESTORED', enabled: true }
    showWebhookForm.value = false
    await loadWebhooks()
  } catch (e: any) {
    toast.error(e?.message || '保存失败')
  }
}

function editWebhook(wh: WebhookEndpoint) {
  editingWebhook.value = wh
  webhookForm.value = { url: wh.url, secret: wh.secret || '', events: wh.events || '', enabled: wh.enabled }
  showWebhookForm.value = true
}

async function deleteWebhook(id: number) {
  try {
    await webhooksApi.delete(id)
    webhooks.value = webhooks.value.filter(w => w.id !== id)
    toast.success('Webhook 已删除')
  } catch (e: any) {
    toast.error(e?.message || '删除失败')
  }
}

async function testWebhook(id: number) {
  try {
    await webhooksApi.test(id)
    toast.success('测试事件已发送')
  } catch (e: any) {
    toast.error(e?.message || '测试失败')
  }
}

async function loadWebhookLogs(id: number) {
  try {
    const res = await webhooksApi.getLogs(id)
    webhookLogs.value = res || []
  } catch (e) {
    webhookLogs.value = []
  }
}

// Verification job state
const verificationJobs = ref<VerificationJob[]>([])
const servers = ref<any[]>([])

async function loadVerificationJobs() {
  try {
    const res = await verificationApi.getAll()
    verificationJobs.value = res || []
  } catch (e) {
    console.error('Failed to load verification jobs', e)
  }
}

async function createVerificationJob() {
  try {
    if (servers.value.length === 0) {
      const { serversApi } = await import('@/api/servers')
      const res = await serversApi.getAll()
      servers.value = res || []
    }
    if (servers.value.length === 0) {
      toast.error('请先添加服务器')
      return
    }
    await verificationApi.create({
      serverId: servers.value[0].id,
      scheduleCron: '0 * * * *',
      enabled: true,
    })
    toast.success('验证任务已创建')
    await loadVerificationJobs()
  } catch (e: any) {
    toast.error(e?.message || '创建失败')
  }
}

async function runVerificationJob(id: number) {
  try {
    await verificationApi.run(id)
    toast.success('验证任务已执行')
    await loadVerificationJobs()
  } catch (e: any) {
    toast.error(e?.message || '执行失败')
  }
}

async function deleteVerificationJob(id: number) {
  try {
    await verificationApi.delete(id)
    verificationJobs.value = verificationJobs.value.filter(j => j.id !== id)
    toast.success('验证任务已删除')
  } catch (e: any) {
    toast.error(e?.message || '删除失败')
  }
}

const statusColors: Record<string, string> = {
  SUCCESS: 'bg-green-500/10 text-green-600',
  FAILED: 'bg-error/10 text-error',
  RUNNING: 'bg-primary/10 text-primary',
  PENDING: 'bg-outline/10 text-outline',
  SKIPPED: 'bg-tertiary/10 text-tertiary',
}

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
  loadWebhooks()
  loadVerificationJobs()
  loadScheduledBackups()
  loadServersForBackups()
})
</script>
