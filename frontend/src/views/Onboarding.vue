<template>
  <div class="min-h-screen bg-background text-on-surface selection:bg-primary/20">
    <!-- Top Navigation Bar -->
    <header class="fixed top-0 z-50 w-full h-16 bg-surface-bright/80 backdrop-blur-xl border-b border-outline-variant/30 flex justify-between items-center px-6 shadow-sm">
      <div class="flex items-center gap-3">
        <span class="material-symbols-outlined text-primary text-3xl" style="font-variation-settings: 'FILL' 1;">restore</span>
        <span class="text-[24px] font-bold tracking-tighter text-on-surface font-[Geist]">ChronoVault</span>
      </div>
      <button @click="router.push('/dashboard')" class="text-[12px] font-bold px-4 py-2 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">
        跳过引导
      </button>
    </header>

    <!-- Main Content -->
    <main class="pt-24 pb-12 min-h-screen flex flex-col items-center">
      <div class="max-w-3xl w-full px-6">
        <!-- Step Indicator -->
        <div class="flex items-center justify-center gap-2 mb-10">
          <div v-for="i in 3" :key="i" class="flex items-center gap-2">
            <div
              class="h-8 w-8 rounded-full flex items-center justify-center text-[12px] font-bold transition-all duration-300"
              :class="step >= i
                ? 'bg-primary text-on-primary shadow-lg shadow-primary/20'
                : 'bg-surface-container-high text-on-surface-variant border border-outline-variant'"
            >
              <span v-if="step > i" class="material-symbols-outlined text-[16px]">check</span>
              <span v-else>{{ i }}</span>
            </div>
            <div v-if="i < 3" class="w-12 h-0.5 rounded-full transition-all duration-300" :class="step > i ? 'bg-primary' : 'bg-outline-variant/30'"></div>
          </div>
        </div>

        <!-- Step 1: Welcome -->
        <div v-if="step === 1" class="glass-panel rounded-xl p-8 border-outline-variant/20 animate-fade-in">
          <div class="text-center mb-8">
            <div class="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
              <span class="material-symbols-outlined text-primary text-[32px]" style="font-variation-settings: 'FILL' 1;">rocket_launch</span>
            </div>
            <h1 class="text-[28px] font-semibold text-on-surface mb-2 font-[Geist]">欢迎使用 ChronoVault</h1>
            <p class="text-[15px] text-on-surface-variant max-w-md mx-auto">
              您的智能服务器时间机器。以下是核心功能：
            </p>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            <div class="p-4 rounded-xl bg-surface-container border border-outline-variant/20 text-center">
              <span class="material-symbols-outlined text-primary text-[28px] mb-2 block">camera</span>
              <h3 class="text-[14px] font-bold text-on-surface mb-1">快照备份</h3>
              <p class="text-[12px] text-on-surface-variant">一键创建服务器快照，支持增量备份与定时策略</p>
            </div>
            <div class="p-4 rounded-xl bg-surface-container border border-outline-variant/20 text-center">
              <span class="material-symbols-outlined text-primary text-[28px] mb-2 block">history</span>
              <h3 class="text-[14px] font-bold text-on-surface mb-1">时间回溯</h3>
              <p class="text-[12px] text-on-surface-variant">随时回滚到任意历史状态，秒级恢复业务</p>
            </div>
            <div class="p-4 rounded-xl bg-surface-container border border-outline-variant/20 text-center">
              <span class="material-symbols-outlined text-primary text-[28px] mb-2 block">psychology</span>
              <h3 class="text-[14px] font-bold text-on-surface mb-1">AI 洞察</h3>
              <p class="text-[12px] text-on-surface-variant">智能分析服务器健康，预测风险与存储趋势</p>
            </div>
          </div>

          <div class="text-center">
            <button @click="step = 2" class="text-[13px] font-bold px-8 py-3 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all">
              开始设置
            </button>
          </div>
        </div>

        <!-- Step 2: Add Server -->
        <div v-if="step === 2" class="glass-panel rounded-xl p-8 border-outline-variant/20 animate-fade-in">
          <div class="flex items-center gap-3 mb-6">
            <div class="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
              <span class="material-symbols-outlined text-primary text-[22px]" style="font-variation-settings: 'FILL' 1;">dns</span>
            </div>
            <div>
              <h2 class="text-[20px] font-semibold font-[Geist]">添加您的第一台服务器</h2>
              <p class="text-[13px] text-on-surface-variant">填写服务器基本信息，后续可随时修改</p>
            </div>
          </div>

          <div class="space-y-4 mb-6">
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-1.5">
                <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">服务器名称 *</label>
                <input v-model="serverName" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2.5 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="如：Prod-Web-01" type="text" />
              </div>
              <div class="space-y-1.5">
                <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">IP 地址 *</label>
                <input v-model="serverIp" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2.5 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="如：192.168.1.100" type="text" />
              </div>
            </div>
            <div class="space-y-1.5">
              <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">操作系统</label>
              <select v-model="serverOs" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2.5 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]">
                <option>Ubuntu 22.04 LTS</option>
                <option>Ubuntu 24.04 LTS</option>
                <option>Debian 12</option>
                <option>CentOS Stream 9</option>
                <option>Rocky Linux 9</option>
              </select>
            </div>
          </div>

          <div class="flex items-center justify-between">
            <button @click="step = 1" class="text-[12px] font-bold px-6 py-2.5 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">
              上一步
            </button>
            <button @click="addServer" :disabled="loading" class="text-[13px] font-bold px-8 py-3 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all disabled:opacity-60">
              {{ loading ? '添加中...' : '添加服务器' }}
            </button>
          </div>
        </div>

        <!-- Step 3: What's Next -->
        <div v-if="step === 3" class="glass-panel rounded-xl p-8 border-outline-variant/20 animate-fade-in">
          <div class="text-center mb-8">
            <div class="w-16 h-16 rounded-2xl bg-green-500/10 flex items-center justify-center mx-auto mb-4">
              <span class="material-symbols-outlined text-green-500 text-[32px]">check_circle</span>
            </div>
            <h2 class="text-[24px] font-semibold text-on-surface mb-2 font-[Geist]">服务器已添加</h2>
            <p class="text-[14px] text-on-surface-variant">接下来您可以这样使用 ChronoVault：</p>
          </div>

          <div class="space-y-3 mb-8">
            <div class="flex items-start gap-4 p-4 rounded-xl bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors">
              <div class="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <span class="material-symbols-outlined text-primary text-[18px]">dashboard</span>
              </div>
              <div>
                <h3 class="text-[14px] font-bold text-on-surface mb-0.5">仪表盘</h3>
                <p class="text-[12px] text-on-surface-variant">查看服务器概览、存储用量、近期活动和风险评分</p>
              </div>
            </div>
            <div class="flex items-start gap-4 p-4 rounded-xl bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors">
              <div class="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <span class="material-symbols-outlined text-primary text-[18px]">photo_camera</span>
              </div>
              <div>
                <h3 class="text-[14px] font-bold text-on-surface mb-0.5">创建快照</h3>
                <p class="text-[12px] text-on-surface-variant">进入「快照」页面，点击「立刻快照」备份服务器当前状态</p>
              </div>
            </div>
            <div class="flex items-start gap-4 p-4 rounded-xl bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors">
              <div class="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <span class="material-symbols-outlined text-primary text-[18px]">storage</span>
              </div>
              <div>
                <h3 class="text-[14px] font-bold text-on-surface mb-0.5">配置存储</h3>
                <p class="text-[12px] text-on-surface-variant">在「存储」页面添加 S3/OSS/本地存储作为快照存放目标</p>
              </div>
            </div>
            <div class="flex items-start gap-4 p-4 rounded-xl bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors">
              <div class="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <span class="material-symbols-outlined text-primary text-[18px]">smart_toy</span>
              </div>
              <div>
                <h3 class="text-[14px] font-bold text-on-surface mb-0.5">AI 分析</h3>
                <p class="text-[12px] text-on-surface-variant">访问「AI 洞察」获取风险雷达、存储预测和优化建议</p>
              </div>
            </div>
          </div>

          <div class="text-center">
            <button @click="router.push('/dashboard')" class="text-[13px] font-bold px-8 py-3 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all">
              进入仪表盘
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- Background Decorations -->
    <div class="fixed bottom-0 left-0 w-full h-1/3 pointer-events-none opacity-40 z-[-1]">
      <div class="absolute bottom-[-10%] left-[-5%] w-[400px] h-[400px] bg-primary/10 rounded-full" style="filter: blur(120px);"></div>
      <div class="absolute bottom-[-15%] right-[-5%] w-[500px] h-[500px] bg-secondary/10 rounded-full" style="filter: blur(150px);"></div>
    </div>
    <div class="fixed inset-0 pointer-events-none z-[-2] opacity-[0.03]" style="background-image: radial-gradient(#005ac2 0.5px, transparent 0.5px); background-size: 24px 24px;"></div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { serversApi } from '@/api/servers'

const router = useRouter()
const toast = useToastStore()
const step = ref(1)
const serverName = ref('')
const serverIp = ref('')
const serverOs = ref('Ubuntu 22.04 LTS')
const loading = ref(false)

async function addServer() {
  if (!serverName.value.trim()) {
    toast.error('请输入服务器名称')
    return
  }
  if (!serverIp.value.trim()) {
    toast.error('请输入服务器 IP 地址')
    return
  }
  loading.value = true
  try {
    await serversApi.create({ name: serverName.value.trim(), ip: serverIp.value.trim(), os: serverOs.value })
    toast.success('服务器添加成功')
    step.value = 3
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '添加服务器失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
