<template>
  <main class="relative min-h-screen w-full flex items-center justify-center overflow-hidden" style="background: radial-gradient(circle at top left, #adc6ff 0%, transparent 40%), radial-gradient(circle at bottom right, #acedff 0%, transparent 40%), #f9f9ff;">
    <!-- Background Decoration -->
    <div class="absolute inset-0 z-0 pointer-events-none opacity-40">
      <div class="absolute top-1/4 left-1/4 w-96 h-96 bg-primary/20 rounded-full blur-[120px]"></div>
      <div class="absolute bottom-1/3 right-1/4 w-[500px] h-[500px] bg-secondary/15 rounded-full blur-[150px]"></div>
      <svg class="absolute inset-0 w-full h-full text-primary/10" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10%" cy="20%" fill="currentColor" r="2"></circle>
        <circle cx="15%" cy="25%" fill="currentColor" r="2"></circle>
        <circle cx="20%" cy="15%" fill="currentColor" r="2"></circle>
        <line stroke="currentColor" stroke-width="0.5" x1="10%" x2="15%" y1="20%" y2="25%"></line>
        <line stroke="currentColor" stroke-width="0.5" x1="15%" x2="20%" y1="25%" y2="15%"></line>
        <circle cx="85%" cy="70%" fill="currentColor" r="3"></circle>
        <circle cx="90%" cy="80%" fill="currentColor" r="2"></circle>
        <circle cx="80%" cy="85%" fill="currentColor" r="2"></circle>
        <line stroke="currentColor" stroke-width="0.5" x1="85%" x2="90%" y1="70%" y2="80%"></line>
        <line stroke="currentColor" stroke-width="0.5" x1="85%" x2="80%" y1="70%" y2="85%"></line>
      </svg>
    </div>

    <div class="relative z-10 w-full max-w-[440px] px-[24px] py-[40px]">
      <!-- Brand -->
      <div class="text-center mb-10">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary shadow-lg shadow-primary/20 mb-6 transition-transform duration-500 hover:rotate-12">
          <span class="material-symbols-outlined text-white text-[32px]" style="font-variation-settings: 'FILL' 1;">restore</span>
        </div>
        <h1 class="font-[Geist] text-[32px] font-semibold tracking-tighter text-on-surface mb-2">ChronoVault</h1>
        <p class="text-[16px] text-on-surface-variant opacity-80">你的服务器时间机器 (Your Server Time Machine)</p>
      </div>

      <!-- Login Card -->
      <div class="glass-panel rounded-3xl p-[20px] md:p-10 border border-white/50 shadow-2xl">
        <form class="space-y-6" @submit.prevent="handleLogin">
          <div class="space-y-2">
            <label class="block text-[12px] font-medium text-on-surface-variant ml-1">邮箱地址</label>
            <div class="relative group">
              <span class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline group-focus-within:text-primary transition-colors">mail</span>
              <input
                v-model="email"
                class="w-full pl-12 pr-4 py-3.5 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none transition-all text-[14px] placeholder:text-outline-variant"
                placeholder="name@company.com"
                type="email"
              />
            </div>
          </div>
          <div class="space-y-2">
            <label class="block text-[12px] font-medium text-on-surface-variant ml-1">访问密码</label>
            <div class="relative group">
              <span class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline group-focus-within:text-primary transition-colors">lock</span>
              <input
                v-model="password"
                class="w-full pl-12 pr-4 py-3.5 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none transition-all text-[14px] placeholder:text-outline-variant"
                placeholder="••••••••"
                type="password"
              />
            </div>
          </div>
          <button
            type="submit"
            class="w-full py-4 px-6 bg-primary text-white font-semibold text-[16px] rounded-xl hover:bg-primary-container transition-all active:scale-95 shadow-lg shadow-primary/20 flex items-center justify-center gap-2"
          >
            <span>立即登录</span>
            <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
          </button>
        </form>

        <p class="text-center mt-8 text-[14px] text-on-surface-variant">
          还没有账号? <router-link to="/register" class="text-primary font-bold hover:underline">立即注册</router-link>
        </p>
      </div>

      <!-- Trust Badges -->
      <div class="mt-12 flex flex-col items-center space-y-6 opacity-60">
        <div class="flex items-center gap-8 grayscale hover:grayscale-0 transition-all duration-500">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-on-surface-variant text-[20px]">verified_user</span>
            <span class="text-[12px] font-medium">端到端加密</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-on-surface-variant text-[20px]">bolt</span>
            <span class="text-[12px] font-medium">毫秒级同步</span>
          </div>
        </div>
        <div class="flex gap-4 text-[12px] font-medium">
          <a class="hover:text-primary cursor-pointer" @click.prevent="toast.info('服务条款页面开发中')">服务条款</a>
          <span class="text-outline-variant">•</span>
          <a class="hover:text-primary cursor-pointer" @click.prevent="toast.info('隐私政策页面开发中')">隐私政策</a>
          <span class="text-outline-variant">•</span>
          <a class="hover:text-primary cursor-pointer" @click.prevent="toast.info('帮助中心页面开发中')">帮助中心</a>
        </div>
      </div>
    </div>

    <!-- Decorative Cards -->
    <div class="hidden xl:block absolute right-[2%] top-[22%] w-80 glass-panel p-6 rounded-3xl transform -rotate-3 border-l-4 border-l-primary/30 shadow-xl pointer-events-none">
      <div class="flex items-center gap-3 mb-4">
        <div class="p-2 rounded-lg bg-primary-container/20 text-primary">
          <span class="material-symbols-outlined text-[20px]">psychology</span>
        </div>
        <span class="text-[12px] font-medium text-primary font-bold">AI 智能观察</span>
      </div>
      <p class="text-[14px] text-on-surface-variant mb-4">"检测到您的主服务器正在进行常规快照备份，当前健康度 99.8%。"</p>
      <div class="h-1.5 w-full bg-surface-container rounded-full overflow-hidden">
        <div class="h-full bg-primary w-4/5 rounded-full"></div>
      </div>
    </div>
    <div class="hidden xl:block absolute left-[4%] bottom-[18%] w-72 glass-panel p-6 rounded-3xl transform rotate-6 border-r-4 border-r-secondary/30 shadow-xl pointer-events-none">
      <div class="flex items-center gap-3 mb-3">
        <span class="material-symbols-outlined text-secondary text-[24px]" style="font-variation-settings: 'FILL' 1;">history</span>
        <span class="text-[12px] font-medium text-secondary font-bold">回溯时间线</span>
      </div>
      <div class="space-y-3">
        <div class="flex items-center gap-3 opacity-40">
          <div class="w-2 h-2 rounded-full bg-outline-variant"></div>
          <div class="h-2 w-24 bg-outline-variant/30 rounded-full"></div>
        </div>
        <div class="flex items-center gap-3">
          <div class="w-2 h-2 rounded-full bg-secondary"></div>
          <div class="h-2 w-32 bg-secondary/20 rounded-full"></div>
        </div>
        <div class="flex items-center gap-3 opacity-40">
          <div class="w-2 h-2 rounded-full bg-outline-variant"></div>
          <div class="h-2 w-20 bg-outline-variant/30 rounded-full"></div>
        </div>
      </div>
    </div>

    <!-- Global Decorations -->
    <div class="fixed top-0 right-0 w-[800px] h-[800px] bg-secondary-container/10 rounded-full blur-[180px] -mr-96 -mt-96 pointer-events-none z-0"></div>
    <div class="fixed bottom-0 left-0 w-[600px] h-[600px] bg-primary-container/10 rounded-full blur-[150px] -ml-64 -mb-64 pointer-events-none z-0"></div>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const toast = useToastStore()
const auth = useAuthStore()
const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    await auth.login(email.value, password.value)
    router.push('/dashboard')
  } catch (e: unknown) {
    error.value = (e instanceof Error ? e.message : null) || '邮箱或密码错误'
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}
</script>
