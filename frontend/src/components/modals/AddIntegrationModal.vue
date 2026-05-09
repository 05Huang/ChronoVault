<template>
  <div class="p-6 space-y-5">
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">集成类型</label>
      <div class="grid grid-cols-2 gap-3">
        <button v-for="t in types" :key="t.value" @click="form.type = t.value"
          class="p-4 rounded-xl border-2 text-left transition-all flex items-center gap-3"
          :class="form.type === t.value ? 'border-primary bg-primary/5' : 'border-outline-variant/30 hover:border-primary/30'">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center text-white" :class="t.bg">
            <span class="material-symbols-outlined text-[20px]">{{ t.icon }}</span>
          </div>
          <div>
            <p class="text-[12px] font-bold">{{ t.label }}</p>
            <p class="text-[10px] text-outline">{{ t.desc }}</p>
          </div>
        </button>
      </div>
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">{{ form.type === 'webhook' ? 'Webhook URL' : form.type === 'slack' ? 'Slack Webhook URL' : '邮箱地址' }}</label>
      <input v-model="form.url" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]"
        :placeholder="form.type === 'email' ? 'admin@company.com' : 'https://...'" />
    </div>
    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleAdd" :disabled="!form.url || loading" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-40">{{ loading ? '添加中...' : '添加' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'
import { integrationsApi } from '@/api/integrations'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()
const loading = ref(false)

const types = [
  { value: 'slack', label: 'Slack', desc: '频道通知', icon: 'tag', bg: 'bg-[#4A154B]' },
  { value: 'email', label: 'Email', desc: '邮件通知', icon: 'alternate_email', bg: 'bg-secondary' },
  { value: 'webhook', label: 'Webhook', desc: '自定义 HTTP', icon: 'webhook', bg: 'bg-on-background' },
  { value: 'dingtalk', label: '钉钉', desc: '群机器人', icon: 'chat', bg: 'bg-blue-500' },
]

const form = ref({ type: 'slack', url: '' })

const typeLabels: Record<string, string> = {
  slack: 'Slack', email: 'Email', webhook: 'Webhook', dingtalk: '钉钉'
}

async function handleAdd() {
  loading.value = true
  try {
    await integrationsApi.create({ type: form.value.type, name: typeLabels[form.value.type] || form.value.type, url: form.value.url })
    toast.success(`${typeLabels[form.value.type]} 集成已添加`)
    emit('close')
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '添加集成失败')
  } finally {
    loading.value = false
  }
}
</script>
