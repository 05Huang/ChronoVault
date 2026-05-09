<template>
  <div class="p-6 space-y-5">
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">邮箱地址</label>
      <input v-model="form.email" type="email" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px]" placeholder="colleague@company.com" />
    </div>
    <div class="space-y-2">
      <label class="block text-[12px] font-bold text-on-surface-variant">角色</label>
      <div class="grid grid-cols-3 gap-3">
        <button v-for="r in roles" :key="r.value" @click="form.role = r.value"
          class="p-3 rounded-xl border-2 text-center transition-all"
          :class="form.role === r.value ? 'border-primary bg-primary/5' : 'border-outline-variant/30 hover:border-primary/30'">
          <p class="text-[12px] font-bold" :class="form.role === r.value ? 'text-primary' : 'text-on-surface'">{{ r.label }}</p>
          <p class="text-[10px] text-outline mt-1">{{ r.desc }}</p>
        </button>
      </div>
    </div>
    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button @click="handleInvite" :disabled="!form.email || loading" class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-40">{{ loading ? '发送中...' : '发送邀请' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'
import { teamApi } from '@/api/team'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const roles = [
  { value: 'Admin', label: '管理员', desc: '完全控制权' },
  { value: 'Member', label: '成员', desc: '读写权限' },
  { value: 'Viewer', label: '观察者', desc: '只读权限' },
]

const form = ref({ email: '', role: 'Member' })
const loading = ref(false)

async function handleInvite() {
  if (!form.value.email) return
  loading.value = true
  try {
    await teamApi.invite({ name: form.value.email.split('@')[0], email: form.value.email, role: form.value.role })
    toast.success(`邀请已发送至 ${form.value.email}`)
    emit('close')
  } catch (e: any) {
    toast.error(e.message || '邀请失败')
  } finally {
    loading.value = false
  }
}
</script>
