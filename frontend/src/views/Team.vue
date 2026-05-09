<template>
  <div class="p-[24px] space-y-[40px]">
    <!-- Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-[32px] font-semibold text-on-surface">团队</h2>
        <p class="text-on-surface-variant text-[16px] mt-1">管理团队成员和权限。</p>
      </div>
      <button @click="openInvite" class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-2 shadow-sm">
        <span class="material-symbols-outlined text-[18px]">person_add</span> 邀请成员
      </button>
    </div>

    <!-- Team Members -->
    <div class="space-y-4">
      <div class="glass-panel rounded-xl overflow-hidden">
        <table class="w-full">
          <thead>
            <tr class="bg-surface-container-low/50 border-b border-outline-variant/20">
              <th class="text-left px-6 py-3 text-[12px] font-bold text-outline uppercase">成员</th>
              <th class="text-left px-6 py-3 text-[12px] font-bold text-outline uppercase">角色</th>
              <th class="text-left px-6 py-3 text-[12px] font-bold text-outline uppercase">状态</th>
              <th class="text-left px-6 py-3 text-[12px] font-bold text-outline uppercase">最后活跃</th>
              <th class="text-right px-6 py-3 text-[12px] font-bold text-outline uppercase">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in teamMembers" :key="member.name" class="border-b border-outline-variant/10 hover:bg-surface-container/30 transition-colors">
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold" :class="member.avatarBg">{{ member.initials }}</div>
                  <div>
                    <p class="text-[14px] font-bold">{{ member.name }}</p>
                    <p class="text-[12px] text-outline">{{ member.email }}</p>
                  </div>
                </div>
              </td>
              <td class="px-6 py-4">
                <span class="px-2 py-0.5 rounded text-[10px] font-bold" :class="member.roleBadge">{{ member.role }}</span>
              </td>
              <td class="px-6 py-4">
                <span class="flex items-center gap-1 text-[12px]">
                  <span class="w-2 h-2 rounded-full" :class="member.statusColor"></span>
                  {{ member.status }}
                </span>
              </td>
              <td class="px-6 py-4 text-[12px] text-outline">{{ member.lastActive }}</td>
              <td class="px-6 py-4 text-right">
                <button @click="openEditMember(member)" class="text-on-surface-variant hover:text-primary transition-colors p-1">
                  <span class="material-symbols-outlined text-[18px]">edit</span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useModalStore } from '@/stores/modal'
import { teamApi } from '@/api/team'
import InviteMemberModal from '@/components/modals/InviteMemberModal.vue'
import EditMemberModal from '@/components/modals/EditMemberModal.vue'

const modal = useModalStore()

function openInvite() {
  modal.open({ component: InviteMemberModal, title: '邀请新成员' })
}

function openEditMember(member: any) {
  modal.open({
    component: EditMemberModal,
    title: `编辑成员 — ${member.name}`,
    props: { name: member.name, email: member.email, currentRole: member.role },
  })
}

const roleBadgeMap: Record<string, string> = {
  OWNER: 'bg-primary/10 text-primary',
  ADMIN: 'bg-secondary/10 text-secondary',
  MEMBER: 'bg-tertiary/10 text-tertiary',
}

const avatarColors = ['bg-primary', 'bg-secondary', 'bg-tertiary', 'bg-error', 'bg-green-600']

const teamMembers = ref<any[]>([])

onMounted(async () => {
  try {
    const res: any = await teamApi.getMembers()
    teamMembers.value = (res.data || res || []).map((m: any, i: number) => ({
      ...m,
      initials: m.name?.split(' ').map((w: string) => w[0]).join('').toUpperCase().slice(0, 2) || '??',
      avatarBg: avatarColors[i % avatarColors.length],
      roleBadge: roleBadgeMap[m.role] || 'bg-outline/10 text-outline',
      status: m.status === 'ONLINE' ? '在线' : m.status === 'online' ? '在线' : '离线',
      statusColor: (m.status === 'ONLINE' || m.status === 'online') ? 'bg-green-500' : 'bg-outline-variant',
      lastActive: m.lastActiveAt ? formatRelativeTime(m.lastActiveAt) : '未知',
    }))
  } catch (e) {
    console.error('Failed to load team members', e)
  }
})

function formatRelativeTime(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  return `${days} 天前`
}
</script>
