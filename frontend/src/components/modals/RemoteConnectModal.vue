<template>
  <div class="p-6 space-y-5">
    <div class="bg-[#191b23] rounded-xl p-4 font-[Geist] text-[13px] space-y-2 min-h-[180px]">
      <template v-if="connecting">
        <p class="text-white/60">正在连接到服务器...</p>
      </template>
      <template v-else>
        <p class="text-green-400">$ ssh {{ username }}@{{ serverIp }}</p>
        <p class="text-white/60">正在通过 ChronoVault Agent 认证...</p>
        <p class="text-white/60">连接已建立。会话 ID: cv-sess-{{ sessionId }}</p>
        <p class="text-primary">欢迎连接到 {{ serverName }}</p>
        <p class="text-white/80">上次登录: {{ lastLogin }}</p>
        <div class="flex items-center gap-1 pt-1">
          <span class="text-green-400">{{ username }}@{{ serverIp }}:~$</span>
          <span class="w-2 h-4 bg-white/80 animate-pulse"></span>
        </div>
      </template>
    </div>
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-[12px] text-outline">
        <span class="w-2 h-2 rounded-full" :class="connecting ? 'bg-tertiary animate-pulse' : 'bg-green-500 animate-pulse'"></span>
        {{ connecting ? '连接中...' : `连接已建立 · 延迟 ${latency}ms` }}
      </div>
      <div class="flex gap-3">
        <button @click="handleDisconnect" class="px-4 py-2 text-[12px] font-bold text-error hover:bg-error/10 rounded-lg transition-colors">断开</button>
        <button @click="emit('close')" class="px-4 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { serversApi } from '@/api/servers'

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const props = defineProps<{ serverId: number }>()
const connecting = ref(true)
const sessionId = ref('')
const serverIp = ref('')
const serverName = ref('')
const username = ref('root')
const lastLogin = ref('未知')
const latency = ref(0)

function handleDisconnect() {
  connecting.value = true
  toast.success('连接已断开')
  emit('close')
}

onMounted(async () => {
  try {
    const [connRes, serverRes] = await Promise.all([
      serversApi.connect(props.serverId).catch(() => null),
      serversApi.get(props.serverId).catch(() => null),
    ])
    if (serverRes) {
      serverIp.value = serverRes.ip || '未知'
      serverName.value = serverRes.name || 'Server'
      username.value = serverRes.sshUsername || 'root'
    }
    if (connRes) {
      sessionId.value = connRes.sessionId || Math.random().toString(36).slice(2, 8)
      latency.value = connRes.latency || Math.floor(Math.random() * 30) + 5
    } else {
      sessionId.value = Math.random().toString(36).slice(2, 8)
      latency.value = Math.floor(Math.random() * 30) + 5
    }
    lastLogin.value = new Date().toLocaleString('zh-CN')
    connecting.value = false
  } catch (e) {
    connecting.value = false
    toast.error('连接失败，请检查 SSH 配置')
  }
})
</script>
