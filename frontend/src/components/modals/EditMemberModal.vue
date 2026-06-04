<template>
  <form @submit.prevent="handleSave" class="space-y-4">
    <div>
      <label class="text-[12px] font-bold text-on-surface-variant block mb-1">成员</label>
      <p class="text-[14px] font-semibold text-on-surface">{{ name }}</p>
      <p class="text-[12px] text-outline">{{ email }}</p>
    </div>
    <div>
      <label class="text-[12px] font-bold text-on-surface-variant block mb-1">角色</label>
      <select v-model="role"
        class="w-full bg-surface-container border border-outline-variant/30 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary outline-none appearance-none">
        <option>管理员</option>
        <option>成员</option>
        <option>观察者</option>
      </select>
    </div>
    <div>
      <label class="text-[12px] font-bold text-on-surface-variant block mb-2">权限</label>
      <div class="space-y-2">
        <label v-for="perm in permissions" :key="perm.key" class="flex items-center gap-3 p-2 rounded-lg hover:bg-surface-container-high transition-colors cursor-pointer">
          <input type="checkbox" v-model="perm.enabled"
            class="w-4 h-4 rounded border-outline-variant text-primary focus:ring-primary" />
          <div>
            <p class="text-[14px] text-on-surface">{{ perm.label }}</p>
            <p class="text-[11px] text-outline">{{ perm.desc }}</p>
          </div>
        </label>
      </div>
    </div>
    <div class="flex gap-3 pt-2">
      <button type="button" @click="modal.close()" class="flex-1 py-2.5 rounded-lg text-[12px] font-bold border border-outline-variant/30 text-on-surface-variant hover:bg-surface-container-high transition-all">
        取消
      </button>
      <button type="submit" :disabled="loading" class="flex-1 py-2.5 rounded-lg text-[12px] font-bold bg-primary text-white hover:opacity-90 transition-all disabled:opacity-50">
        {{ loading ? '保存中...' : '保存更改' }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useModalStore } from '@/stores/modal'
import { useToastStore } from '@/stores/toast'
import { teamApi } from '@/api/team'

const props = defineProps<{
  id: number
  name: string
  email: string
  currentRole: string
}>()

const modal = useModalStore()
const toast = useToastStore()

const role = ref(props.currentRole)
const loading = ref(false)
const permissions = ref([
  { key: 'snapshot', label: '快照管理', desc: '创建、删除、回滚快照', enabled: true },
  { key: 'recovery', label: '恢复操作', desc: '执行服务器状态恢复', enabled: true },
  { key: 'settings', label: '系统设置', desc: '修改全局配置和集成', enabled: false },
  { key: 'team', label: '团队管理', desc: '邀请和管理团队成员', enabled: false },
])

async function handleSave() {
  loading.value = true
  try {
    const enabledPerms = permissions.value.filter(p => p.enabled).map(p => p.key).join(',')
    await teamApi.updateMember(props.id, { role: role.value, permissions: enabledPerms })
    toast.success(`${props.name} 的权限已更新`)
    modal.close()
  } catch (e: unknown) {
    toast.error((e instanceof Error ? e.message : null) || '更新失败')
  } finally {
    loading.value = false
  }
}
</script>
