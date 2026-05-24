<template>
  <div class="p-6 space-y-5">
    <div v-if="!generated" class="space-y-5">
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">密钥名称</label>
        <input v-model="keyName" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="例如 CI/CD Pipeline" />
      </div>
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">权限范围</label>
        <div class="flex gap-2">
          <button v-for="s in scopes" :key="s.value" @click="selectedScope = s.value"
            class="px-3 py-1.5 rounded-lg text-[11px] font-bold transition-all"
            :class="selectedScope === s.value ? 'bg-primary text-white' : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-highest'">
            {{ s.label }}
          </button>
        </div>
      </div>
      <div class="flex justify-end gap-3 pt-2">
        <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
        <button @click="handleGenerate" :disabled="!keyName || loading" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-40">{{ loading ? '生成中...' : '生成' }}</button>
      </div>
    </div>
    <div v-else class="space-y-4">
      <div class="bg-green-500/10 border border-green-500/30 rounded-xl p-4 flex items-center gap-3">
        <span class="material-symbols-outlined text-green-500">check_circle</span>
        <p class="text-[14px] font-bold text-green-700">密钥已生成</p>
      </div>
      <div class="space-y-2">
        <label class="block text-[12px] font-bold text-on-surface-variant">API 密钥（仅显示一次）</label>
        <div class="flex gap-2">
          <input :value="generatedKey" readonly class="flex-1 px-4 py-3 bg-surface-container border border-outline-variant rounded-xl text-[13px] font-[Geist]" />
          <button @click="copyKey" class="px-4 py-3 bg-primary text-white rounded-xl hover:bg-primary-container transition-all">
            <span class="material-symbols-outlined text-[18px]">content_copy</span>
          </button>
        </div>
      </div>
      <div class="flex justify-end pt-2">
        <button @click="$emit('close')" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all">完成</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'
import { settingsApi } from '@/api/settings'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const keyName = ref('')
const selectedScope = ref('readwrite')
const generated = ref(false)
const generatedKey = ref('')
const loading = ref(false)
const scopes = [
  { value: 'readonly', label: '只读' },
  { value: 'readwrite', label: '读写' },
  { value: 'admin', label: '管理员' },
]

async function handleGenerate() {
  if (!keyName.value) return
  loading.value = true
  try {
    const res = await settingsApi.generateKey({ name: keyName.value, scope: selectedScope.value })
    generatedKey.value = res?.key || ''
    generated.value = true
    toast.success(`密钥 ${keyName.value} 已生成`)
  } catch (e: any) {
    toast.error(e.message || '生成失败')
  } finally {
    loading.value = false
  }
}

function copyKey() {
  navigator.clipboard.writeText(generatedKey.value)
  toast.success('密钥已复制到剪贴板')
}
</script>
