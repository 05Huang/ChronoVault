<template>
  <main class="relative min-h-screen w-full flex items-center justify-center overflow-hidden" style="background: radial-gradient(circle at top right, #acedff 0%, transparent 40%), radial-gradient(circle at bottom left, #adc6ff 0%, transparent 40%), #f9f9ff;">
    <!-- Animated Floating Particles -->
    <div class="absolute inset-0 z-0 pointer-events-none overflow-hidden">
      <div v-for="p in particles" :key="p.id"
        class="absolute rounded-full"
        :class="p.color"
        :style="{
          width: p.size + 'px',
          height: p.size + 'px',
          left: p.x + '%',
          top: p.y + '%',
          animation: `float-${p.id % 3} ${p.duration}s ease-in-out infinite`,
          animationDelay: p.delay + 's',
          opacity: p.opacity,
        }"
      ></div>
    </div>

    <!-- Background Blobs -->
    <div class="absolute inset-0 z-0 pointer-events-none opacity-40">
      <div class="absolute top-1/3 right-1/4 w-96 h-96 bg-secondary/20 rounded-full blur-[120px]"></div>
      <div class="absolute bottom-1/4 left-1/3 w-[500px] h-[500px] bg-primary/15 rounded-full blur-[150px]"></div>
    </div>

    <div class="relative z-10 w-full max-w-[440px] px-[24px] py-[40px]">
      <!-- Brand -->
      <div class="text-center mb-8">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary shadow-lg shadow-primary/20 mb-6 transition-transform duration-500 hover:rotate-12">
          <span class="material-symbols-outlined text-white text-[32px]" style="font-variation-settings: 'FILL' 1;">restore</span>
        </div>
        <h1 class="font-[Geist] text-[32px] font-semibold tracking-tighter text-on-surface mb-2">创建账号</h1>
        <p class="text-[16px] text-on-surface-variant opacity-80">开始使用 ChronoVault 保护你的基础设施</p>
      </div>

      <!-- Register Card -->
      <div class="glass-panel rounded-3xl p-[20px] md:p-10 border border-white/50 shadow-2xl">
        <!-- Step Indicator -->
        <div class="flex items-center justify-center gap-3 mb-8">
          <div v-for="s in 3" :key="s" class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-full flex items-center justify-center text-[12px] font-bold transition-all duration-500"
              :class="s <= step ? 'bg-primary text-white scale-110 shadow-lg shadow-primary/30' : 'bg-surface-container-highest text-outline'">
              <span v-if="s < step" class="material-symbols-outlined text-[16px]">check</span>
              <span v-else>{{ s }}</span>
            </div>
            <div v-if="s < 3" class="w-8 h-[2px] rounded-full transition-all duration-500"
              :class="s < step ? 'bg-primary' : 'bg-outline-variant/30'"></div>
          </div>
        </div>

        <form @submit.prevent="handleSubmit">
          <!-- Step 1: Email -->
          <div v-show="step === 1" class="space-y-6 animate-step">
            <div class="space-y-2">
              <label class="block text-[12px] font-medium text-on-surface-variant ml-1">邮箱地址</label>
              <div class="relative group">
                <span class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline group-focus-within:text-primary transition-colors">mail</span>
                <input
                  v-model="form.email"
                  @blur="emailTouched = true"
                  class="w-full pl-12 pr-4 py-3.5 bg-white/50 border rounded-xl focus:ring-2 focus:ring-primary/20 outline-none transition-all text-[14px] placeholder:text-outline-variant"
                  :class="emailError && emailTouched ? 'border-error focus:border-error' : 'border-outline-variant focus:border-primary'"
                  placeholder="name@company.com"
                  type="email"
                  required
                />
              </div>
              <p v-if="emailError && emailTouched" class="text-[11px] ml-1 text-error">{{ emailError }}</p>
            </div>
            <div class="space-y-2">
              <label class="block text-[12px] font-medium text-on-surface-variant ml-1">你的名字</label>
              <div class="relative group">
                <span class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline group-focus-within:text-primary transition-colors">person</span>
                <input
                  v-model="form.name"
                  class="w-full pl-12 pr-4 py-3.5 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none transition-all text-[14px] placeholder:text-outline-variant"
                  placeholder="Xuan Huang"
                  type="text"
                  required
                />
              </div>
            </div>
            <button
              type="button"
              @click="nextStep"
              :disabled="!form.email || !form.name || !!emailError"
              class="w-full py-4 px-6 bg-primary text-white font-semibold text-[16px] rounded-xl hover:bg-primary-container transition-all active:scale-95 shadow-lg shadow-primary/20 flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100"
            >
              <span>继续</span>
              <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
            </button>
          </div>

          <!-- Step 2: Password -->
          <div v-show="step === 2" class="space-y-6 animate-step">
            <div class="space-y-2">
              <label class="block text-[12px] font-medium text-on-surface-variant ml-1">设置密码</label>
              <div class="relative group">
                <span class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline group-focus-within:text-primary transition-colors">lock</span>
                <input
                  v-model="form.password"
                  class="w-full pl-12 pr-12 py-3.5 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none transition-all text-[14px] placeholder:text-outline-variant"
                  placeholder="至少 8 位字符"
                  :type="showPassword ? 'text' : 'password'"
                  required
                />
                <button type="button" @click="showPassword = !showPassword"
                  class="absolute right-4 top-1/2 -translate-y-1/2 text-outline hover:text-primary transition-colors">
                  <span class="material-symbols-outlined text-[20px]">{{ showPassword ? 'visibility_off' : 'visibility' }}</span>
                </button>
              </div>
              <!-- Password Strength -->
              <div class="flex gap-1.5 mt-2">
                <div v-for="i in 4" :key="i" class="h-1 flex-1 rounded-full transition-all duration-300"
                  :class="i <= passwordStrength ? strengthColors[passwordStrength] : 'bg-surface-container-highest'"></div>
              </div>
              <p v-if="form.password && form.password.length < 8" class="text-[11px] ml-1 text-error">
                密码长度至少 8 位（当前 {{ form.password.length }} 位）
              </p>
              <p v-else class="text-[11px] ml-1 transition-colors" :class="strengthLabels[passwordStrength]?.color">
                {{ strengthLabels[passwordStrength]?.text }}
              </p>
            </div>
            <div class="space-y-2">
              <label class="block text-[12px] font-medium text-on-surface-variant ml-1">确认密码</label>
              <div class="relative group">
                <span class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline group-focus-within:text-primary transition-colors">lock_reset</span>
                <input
                  v-model="form.confirmPassword"
                  class="w-full pl-12 pr-4 py-3.5 bg-white/50 border rounded-xl focus:ring-2 focus:ring-primary/20 outline-none transition-all text-[14px] placeholder:text-outline-variant"
                  :class="form.confirmPassword && form.confirmPassword !== form.password ? 'border-error focus:border-error' : 'border-outline-variant focus:border-primary'"
                  placeholder="再次输入密码"
                  type="password"
                  required
                />
                <span v-if="form.confirmPassword && form.confirmPassword === form.password"
                  class="material-symbols-outlined absolute right-4 top-1/2 -translate-y-1/2 text-green-500 text-[20px] animate-check">check_circle</span>
              </div>
            </div>
            <div class="flex gap-3">
              <button type="button" @click="step = 1"
                class="flex-1 py-3 px-6 bg-surface-container-high text-on-surface font-semibold text-[14px] rounded-xl hover:bg-surface-container-highest transition-all active:scale-95 flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">arrow_back</span>
                <span>返回</span>
              </button>
              <button
                type="button"
                @click="nextStep"
                :disabled="!form.password || form.password !== form.confirmPassword || form.password.length < 8 || passwordStrength < 2"
                class="flex-1 py-3 px-6 bg-primary text-white font-semibold text-[14px] rounded-xl hover:bg-primary-container transition-all active:scale-95 shadow-lg shadow-primary/20 flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100"
              >
                <span>继续</span>
                <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
              </button>
            </div>
          </div>

          <!-- Step 3: Confirm -->
          <div v-show="step === 3" class="space-y-6 animate-step">
            <div class="text-center py-4">
              <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-primary/10 mb-4 animate-bounce-slow">
                <span class="material-symbols-outlined text-primary text-[32px]" style="font-variation-settings: 'FILL' 1;">verified_user</span>
              </div>
              <h3 class="text-[18px] font-semibold mb-2">确认注册信息</h3>
              <p class="text-[14px] text-on-surface-variant">请检查以下信息是否正确</p>
            </div>
            <div class="bg-surface-container/50 rounded-xl p-4 space-y-3 border border-outline-variant/20">
              <div class="flex items-center justify-between">
                <span class="text-[12px] text-outline">邮箱</span>
                <span class="text-[14px] font-medium">{{ form.email }}</span>
              </div>
              <div class="h-px bg-outline-variant/20"></div>
              <div class="flex items-center justify-between">
                <span class="text-[12px] text-outline">姓名</span>
                <span class="text-[14px] font-medium">{{ form.name }}</span>
              </div>
              <div class="h-px bg-outline-variant/20"></div>
              <div class="flex items-center justify-between">
                <span class="text-[12px] text-outline">密码</span>
                <span class="text-[14px] font-medium tracking-widest">••••••••</span>
              </div>
            </div>
            <div class="flex gap-3">
              <button type="button" @click="step = 2"
                class="flex-1 py-3 px-6 bg-surface-container-high text-on-surface font-semibold text-[14px] rounded-xl hover:bg-surface-container-highest transition-all active:scale-95 flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">arrow_back</span>
                <span>返回</span>
              </button>
              <button
                type="submit"
                :disabled="loading"
                class="flex-1 py-3 px-6 bg-primary text-white font-semibold text-[14px] rounded-xl hover:bg-primary-container transition-all active:scale-95 shadow-lg shadow-primary/20 flex items-center justify-center gap-2 disabled:opacity-60"
              >
                <svg v-if="loading" class="animate-spin w-5 h-5" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                </svg>
                <span v-else>创建账号</span>
              </button>
            </div>
          </div>
        </form>

        <p class="text-center mt-8 text-[14px] text-on-surface-variant">
          已有账号? <router-link to="/login" class="text-primary font-bold hover:underline">立即登录</router-link>
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
        </div>
      </div>
    </div>

    <!-- Decorative Cards -->
    <div class="hidden xl:block absolute left-[2%] top-[22%] w-80 glass-panel p-6 rounded-3xl transform rotate-3 border-l-4 border-l-secondary/30 shadow-xl pointer-events-none">
      <div class="flex items-center gap-3 mb-4">
        <div class="p-2 rounded-lg bg-secondary-container/20 text-secondary">
          <span class="material-symbols-outlined text-[20px]">shield_lock</span>
        </div>
        <span class="text-[12px] font-medium text-secondary font-bold">安全注册</span>
      </div>
      <p class="text-[14px] text-on-surface-variant mb-4">"您的数据将通过 AES-256 端到端加密保护，零知识架构确保只有您能访问。"</p>
      <div class="flex gap-2">
        <span class="px-2 py-1 text-[10px] font-bold rounded-full bg-green-500/10 text-green-600">AES-256</span>
        <span class="px-2 py-1 text-[10px] font-bold rounded-full bg-primary/10 text-primary">E2E</span>
        <span class="px-2 py-1 text-[10px] font-bold rounded-full bg-secondary/10 text-secondary">零知识</span>
      </div>
    </div>
    <div class="hidden xl:block absolute right-[4%] bottom-[18%] w-72 glass-panel p-6 rounded-3xl transform -rotate-6 border-r-4 border-r-tertiary/30 shadow-xl pointer-events-none">
      <div class="flex items-center gap-3 mb-3">
        <span class="material-symbols-outlined text-tertiary text-[24px]" style="font-variation-settings: 'FILL' 1;">rocket_launch</span>
        <span class="text-[12px] font-medium text-tertiary font-bold">快速上手</span>
      </div>
      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <div class="w-5 h-5 rounded-full bg-primary flex items-center justify-center">
            <span class="material-symbols-outlined text-white text-[12px]">check</span>
          </div>
          <div class="h-2 w-28 bg-primary/20 rounded-full"></div>
        </div>
        <div class="flex items-center gap-3 opacity-50">
          <div class="w-5 h-5 rounded-full border-2 border-outline-variant"></div>
          <div class="h-2 w-24 bg-outline-variant/30 rounded-full"></div>
        </div>
        <div class="flex items-center gap-3 opacity-30">
          <div class="w-5 h-5 rounded-full border-2 border-outline-variant"></div>
          <div class="h-2 w-20 bg-outline-variant/30 rounded-full"></div>
        </div>
      </div>
    </div>

    <!-- Global Decorations -->
    <div class="fixed top-0 left-0 w-[800px] h-[800px] bg-secondary-container/10 rounded-full blur-[180px] -ml-96 -mt-96 pointer-events-none z-0"></div>
    <div class="fixed bottom-0 right-0 w-[600px] h-[600px] bg-primary-container/10 rounded-full blur-[150px] -mr-64 -mb-64 pointer-events-none z-0"></div>
  </main>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const toast = useToastStore()
const auth = useAuthStore()
const step = ref(1)
const showPassword = ref(false)
const loading = ref(false)
const emailTouched = ref(false)

const form = ref({
  email: '',
  name: '',
  password: '',
  confirmPassword: '',
})

// Email validation
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const emailError = computed(() => {
  const e = form.value.email
  if (!e) return ''
  if (!emailRegex.test(e)) return '请输入有效的邮箱地址'
  return ''
})

// Password strength
const passwordStrength = computed(() => {
  const p = form.value.password
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  return score
})

const strengthColors: Record<number, string> = {
  1: 'bg-error',
  2: 'bg-tertiary',
  3: 'bg-primary',
  4: 'bg-green-500',
}

const strengthLabels: Record<number, { text: string; color: string }> = {
  0: { text: '请输入密码', color: 'text-outline' },
  1: { text: '密码强度：弱', color: 'text-error' },
  2: { text: '密码强度：中', color: 'text-tertiary' },
  3: { text: '密码强度：强', color: 'text-primary' },
  4: { text: '密码强度：极强', color: 'text-green-500' },
}

function nextStep() {
  if (step.value < 3) step.value++
}

async function handleSubmit() {
  loading.value = true
  try {
    await auth.register(form.value.name, form.value.email, form.value.password)
    toast.success('注册成功！')
    router.push('/onboarding')
  } catch (e: any) {
    toast.error(e?.message || '注册失败')
  } finally {
    loading.value = false
  }
}

// Floating particles data
const particles = Array.from({ length: 20 }, (_, i) => ({
  id: i,
  x: Math.random() * 100,
  y: Math.random() * 100,
  size: Math.random() * 6 + 2,
  duration: Math.random() * 8 + 6,
  delay: Math.random() * 4,
  opacity: Math.random() * 0.3 + 0.1,
  color: i % 3 === 0 ? 'bg-primary/20' : i % 3 === 1 ? 'bg-secondary/20' : 'bg-tertiary/10',
}))
</script>

<style scoped>
.animate-step {
  animation: stepIn 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes stepIn {
  from {
    opacity: 0;
    transform: translateX(24px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.animate-check {
  animation: checkPop 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes checkPop {
  0% { transform: translateY(-50%) scale(0); }
  60% { transform: translateY(-50%) scale(1.3); }
  100% { transform: translateY(-50%) scale(1); }
}

.animate-bounce-slow {
  animation: bounceSlow 3s ease-in-out infinite;
}

@keyframes bounceSlow {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

@keyframes float-0 {
  0%, 100% { transform: translate(0, 0); }
  25% { transform: translate(12px, -18px); }
  50% { transform: translate(-8px, -30px); }
  75% { transform: translate(15px, -12px); }
}

@keyframes float-1 {
  0%, 100% { transform: translate(0, 0); }
  25% { transform: translate(-15px, 12px); }
  50% { transform: translate(10px, 25px); }
  75% { transform: translate(-20px, 8px); }
}

@keyframes float-2 {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(18px, 15px); }
  66% { transform: translate(-12px, -20px); }
}
</style>
