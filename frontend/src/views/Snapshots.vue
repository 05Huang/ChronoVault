<template>
  <div class="p-[24px] space-y-[40px] relative">
    <!-- Background Decoration Particles -->
    <div class="absolute w-1 h-1 top-[10%] left-[20%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>
    <div class="absolute w-2 h-2 top-[30%] left-[80%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>
    <div class="absolute w-1.5 h-1.5 top-[60%] left-[15%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>
    <div class="absolute w-1 h-1 top-[80%] left-[70%] bg-primary opacity-10 blur-[1px] rounded-full pointer-events-none"></div>

    <!-- Verify Result -->
    <div v-if="verifyResult" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30 p-6">
      <div class="flex items-center gap-3 mb-3">
        <span class="material-symbols-outlined text-[24px]"
          :class="verifyResult.verified ? 'text-green-500' : 'text-error'"
          style="font-variation-settings: 'FILL' 1;">
          {{ verifyResult.verified ? 'verified' : 'error' }}
        </span>
        <div>
          <h3 class="text-[20px] font-semibold" :class="verifyResult.verified ? 'text-green-500' : 'text-error'">
            {{ verifyResult.verified ? '验证通过' : '验证失败' }}
          </h3>
          <p class="text-[12px] text-on-surface-variant">
            耗时 {{ (verifyResult.durationMs / 1000).toFixed(1) }} 秒
            <span v-if="verifyResult.errors"> — {{ verifyResult.errors }}</span>
          </p>
        </div>
      </div>
    </div>

    <!-- Container States -->
    <div v-if="containerStates.length > 0" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50 cursor-pointer"
        @click="showContainers = !showContainers">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-secondary text-[24px]" style="font-variation-settings: 'FILL' 1;">deployed_code</span>
          <div>
            <h3 class="text-[20px] font-semibold">容器状态</h3>
            <p class="text-[12px] text-on-surface-variant">快照时捕获了 {{ containerStates.length }} 个容器</p>
          </div>
        </div>
        <span class="material-symbols-outlined text-[20px] text-outline transition-transform" :class="showContainers ? 'rotate-180' : ''">expand_more</span>
      </div>
      <div v-show="showContainers" class="p-4">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div v-for="cs in containerStates" :key="cs.id"
            class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
            <div class="flex items-center gap-3 mb-2">
              <span class="material-symbols-outlined text-[18px]"
                :class="cs.status.includes('Up') ? 'text-green-500' : 'text-error'">circle</span>
              <div>
                <p class="text-[14px] font-bold">{{ cs.containerName }}</p>
                <p class="text-[11px] text-outline truncate">{{ cs.image }}</p>
              </div>
            </div>
            <p class="text-[11px] text-on-surface-variant">{{ cs.status }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- System State (state.json) -->
    <div v-if="stateSnapshot" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50 cursor-pointer"
        @click="showState = !showState">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-[24px]" style="font-variation-settings: 'FILL' 1; color: var(--color-primary);">database</span>
          <div>
            <h3 class="text-[20px] font-semibold">系统状态 (state.json)</h3>
            <p class="text-[12px] text-on-surface-variant">
              采集于 {{ stateSnapshot.collected_at ? new Date(stateSnapshot.collected_at).toLocaleString('zh-CN') : 'N/A' }}
              — Agent {{ stateSnapshot.agent_version || '?' }}
            </p>
          </div>
        </div>
        <span class="material-symbols-outlined text-[20px] text-outline transition-transform" :class="showState ? 'rotate-180' : ''">expand_more</span>
      </div>
      <div v-show="showState" class="p-4 space-y-4">
        <!-- OS Info -->
        <div v-if="stateSnapshot.os" class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
          <h4 class="text-[14px] font-bold mb-2 flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">computer</span>
            操作系统
          </h4>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-2 text-[12px]">
            <div><span class="text-outline">名称:</span> {{ stateSnapshot.os.name }}</div>
            <div><span class="text-outline">版本:</span> {{ stateSnapshot.os.version }}</div>
            <div><span class="text-outline">内核:</span> {{ stateSnapshot.os.kernel }}</div>
            <div><span class="text-outline">架构:</span> {{ stateSnapshot.os.arch }}</div>
          </div>
        </div>

        <!-- Packages -->
        <div v-if="stateSnapshot.packages && stateSnapshot.packages.length" class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
          <h4 class="text-[14px] font-bold mb-2 flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">inventory_2</span>
            已安装包 ({{ stateSnapshot.packages.length }})
          </h4>
          <div class="max-h-[200px] overflow-y-auto space-y-1">
            <div v-for="pkg in stateSnapshot.packages.slice(0, 50)" :key="pkg.name"
              class="flex items-center justify-between px-3 py-1.5 bg-surface/50 rounded text-[12px]">
              <span class="font-mono">{{ pkg.name }}</span>
              <span class="text-outline">{{ pkg.version }}</span>
            </div>
            <p v-if="stateSnapshot.packages.length > 50" class="text-[11px] text-outline text-center pt-2">
              ... 还有 {{ stateSnapshot.packages.length - 50 }} 个包
            </p>
          </div>
        </div>

        <!-- Services -->
        <div v-if="stateSnapshot.services && stateSnapshot.services.length" class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
          <h4 class="text-[14px] font-bold mb-2 flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">settings</span>
            服务状态 ({{ stateSnapshot.services.length }})
          </h4>
          <div class="max-h-[200px] overflow-y-auto space-y-1">
            <div v-for="svc in stateSnapshot.services" :key="svc.name"
              class="flex items-center justify-between px-3 py-1.5 bg-surface/50 rounded text-[12px]">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full" :class="svc.status === 'active' ? 'bg-green-500' : svc.status === 'failed' ? 'bg-red-500' : 'bg-gray-400'"></span>
                <span class="font-mono">{{ svc.name }}</span>
              </div>
              <div class="flex items-center gap-3 text-outline">
                <span>{{ svc.status }}</span>
                <span class="text-[10px] px-1.5 py-0.5 rounded" :class="svc.enabled ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'">
                  {{ svc.enabled ? 'enabled' : 'disabled' }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Ports -->
        <div v-if="stateSnapshot.ports && stateSnapshot.ports.length" class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
          <h4 class="text-[14px] font-bold mb-2 flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">cable</span>
            开放端口 ({{ stateSnapshot.ports.length }})
          </h4>
          <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
            <div v-for="(port, i) in stateSnapshot.ports" :key="i"
              class="px-3 py-1.5 bg-surface/50 rounded text-[12px] flex items-center gap-2">
              <span class="font-mono font-bold">{{ port.port }}</span>
              <span class="text-outline text-[10px]">{{ port.protocol }}</span>
              <span class="text-outline text-[10px] truncate">{{ port.process }}</span>
            </div>
          </div>
        </div>

        <!-- Docker -->
        <div v-if="stateSnapshot.docker && stateSnapshot.docker.available" class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
          <h4 class="text-[14px] font-bold mb-2 flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">deployed_code</span>
            Docker 容器 ({{ stateSnapshot.docker.containers.length }})
          </h4>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div v-for="c in stateSnapshot.docker.containers" :key="c.id"
              class="px-3 py-2 bg-surface/50 rounded text-[12px]">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full" :class="c.status.includes('running') ? 'bg-green-500' : 'bg-gray-400'"></span>
                <span class="font-bold">{{ c.name }}</span>
              </div>
              <p class="text-outline text-[10px] mt-0.5">{{ c.image }} — {{ c.status }}</p>
            </div>
          </div>
        </div>

        <!-- Configs -->
        <div v-if="stateSnapshot.configs && stateSnapshot.configs.length" class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
          <h4 class="text-[14px] font-bold mb-2 flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">description</span>
            配置文件 ({{ stateSnapshot.configs.length }})
          </h4>
          <div class="max-h-[150px] overflow-y-auto space-y-1">
            <div v-for="cfg in stateSnapshot.configs" :key="cfg.path"
              class="flex items-center justify-between px-3 py-1.5 bg-surface/50 rounded text-[11px]">
              <span class="font-mono truncate">{{ cfg.path }}</span>
              <span class="text-outline text-[10px] shrink-0 ml-2">{{ cfg.sha256?.substring(0, 8) }}...</span>
            </div>
          </div>
        </div>

        <!-- Crontab -->
        <div v-if="stateSnapshot.crontab && stateSnapshot.crontab.length" class="p-4 rounded-xl bg-surface-container/50 border border-outline-variant/20">
          <h4 class="text-[14px] font-bold mb-2 flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">schedule</span>
            定时任务 ({{ stateSnapshot.crontab.length }})
          </h4>
          <div class="space-y-1">
            <div v-for="(entry, i) in stateSnapshot.crontab" :key="i"
              class="px-3 py-1.5 bg-surface/50 rounded text-[12px] flex items-center gap-3">
              <span class="text-outline text-[10px] shrink-0">{{ entry.user }}</span>
              <span class="font-mono text-[11px]">{{ entry.schedule }}</span>
              <span class="truncate text-on-surface-variant">{{ entry.command }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- State loading indicator -->
    <div v-if="loadingState" class="glass-panel rounded-2xl border border-outline-variant/30 p-4 flex items-center gap-3">
      <span class="material-symbols-outlined text-primary animate-spin">progress_activity</span>
      <span class="text-[13px] text-on-surface-variant">加载系统状态数据...</span>
    </div>

    <!-- Bisect Wizard (toggled) -->
    <div v-if="showBisect" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">binary</span>
          <div>
            <h3 class="text-[24px] font-semibold">二分查找 (Bisect)</h3>
            <p class="text-[12px] text-on-surface-variant">快速定位哪个快照引入了问题</p>
          </div>
        </div>
        <button @click="showBisect = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>

      <!-- Step 1: Select good/bad snapshots -->
      <div v-if="!bisectSession" class="p-6 space-y-6">
        <p class="text-[14px] text-on-surface-variant">选择一个已知正常的快照（好）和一个已知有问题的快照（坏），系统将通过二分法快速定位引入问题的快照。</p>

        <div class="grid grid-cols-2 gap-6">
          <!-- Good snapshot selector -->
          <div class="space-y-3">
            <label class="flex items-center gap-2 text-[12px] font-bold text-on-surface-variant">
              <span class="w-3 h-3 rounded-full bg-green-500"></span>
              已知正常快照 (Good)
            </label>
            <select v-model="bisectGoodId" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-green-500/20 focus:border-green-500 outline-none text-[14px] appearance-none">
              <option :value="0" disabled>选择好快照</option>
              <option v-for="s in snapshots" :key="s.id" :value="s.id">{{ s.name || s.title || '快照' }} ({{ s.createdAt ? new Date(s.createdAt).toLocaleDateString('zh-CN') : '' }})</option>
            </select>
          </div>

          <!-- Bad snapshot selector -->
          <div class="space-y-3">
            <label class="flex items-center gap-2 text-[12px] font-bold text-on-surface-variant">
              <span class="w-3 h-3 rounded-full bg-error"></span>
              已知异常快照 (Bad)
            </label>
            <select v-model="bisectBadId" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-error/20 focus:border-error outline-none text-[14px] appearance-none">
              <option :value="0" disabled>选择坏快照</option>
              <option v-for="s in snapshots" :key="s.id" :value="s.id">{{ s.name || s.title || '快照' }} ({{ s.createdAt ? new Date(s.createdAt).toLocaleDateString('zh-CN') : '' }})</option>
            </select>
          </div>
        </div>

        <button @click="startBisect" :disabled="!bisectGoodId || !bisectBadId || bisectStarting" class="bg-primary text-white font-bold px-6 py-3 rounded-xl shadow-xl shadow-primary/30 hover:scale-105 active:scale-95 transition-all disabled:opacity-50 flex items-center gap-2">
          <span class="material-symbols-outlined text-[18px]">play_arrow</span>
          {{ bisectStarting ? '启动中...' : '开始二分查找' }}
        </button>
      </div>

      <!-- Step 2: Testing snapshots -->
      <div v-else-if="bisectSession.status === 'IN_PROGRESS'" class="p-6 space-y-6">
        <!-- Progress bar -->
        <div class="space-y-2">
          <div class="flex justify-between items-center">
            <span class="text-[12px] font-bold text-on-surface-variant">进度</span>
            <span class="text-[12px] text-outline font-[Geist]">{{ bisectSession.totalSteps - bisectSession.stepsRemaining }} / {{ bisectSession.totalSteps }}</span>
          </div>
          <div class="w-full bg-surface-container-highest h-2 rounded-full overflow-hidden">
            <div class="bg-primary h-full rounded-full transition-all duration-500" :style="{ width: ((bisectSession.totalSteps - bisectSession.stepsRemaining) / bisectSession.totalSteps * 100) + '%' }"></div>
          </div>
          <p class="text-[11px] text-outline">还剩 {{ bisectSession.stepsRemaining }} 步</p>
        </div>

        <!-- Current snapshot to test -->
        <div class="bg-primary/5 border border-primary/20 rounded-xl p-6">
          <p class="text-[12px] font-bold text-primary mb-2">请测试以下快照：</p>
          <div class="flex items-center gap-4">
            <div class="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
              <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">inventory_2</span>
            </div>
            <div>
              <h4 class="text-[20px] font-semibold">{{ bisectSession.currentSnapshotName }}</h4>
              <p class="text-[12px] text-on-surface-variant">ID: {{ bisectSession.currentSnapshotId }}</p>
            </div>
          </div>
        </div>

        <!-- Verdict buttons -->
        <div class="flex gap-4">
          <button @click="markBisect('good')" class="flex-1 py-4 bg-green-500 text-white font-bold rounded-xl text-[14px] shadow-lg hover:shadow-green-500/30 hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-2">
            <span class="material-symbols-outlined text-[20px]">check_circle</span>
            此快照正常 (Good)
          </button>
          <button @click="markBisect('bad')" class="flex-1 py-4 bg-error text-white font-bold rounded-xl text-[14px] shadow-lg hover:shadow-error/30 hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-2">
            <span class="material-symbols-outlined text-[20px]">cancel</span>
            此快照异常 (Bad)
          </button>
        </div>

        <button @click="showBisect = false; bisectSession = null" class="text-[12px] text-outline hover:text-on-surface transition-colors">放弃查找</button>
      </div>

      <!-- Step 3: Culprit found -->
      <div v-else-if="bisectSession.status === 'FOUND'" class="p-6 space-y-6">
        <div class="text-center py-4">
          <div class="w-16 h-16 rounded-full bg-error/10 flex items-center justify-center mx-auto mb-4">
            <span class="material-symbols-outlined text-error text-[32px]" style="font-variation-settings: 'FILL' 1;">gpp_maybe</span>
          </div>
          <h3 class="text-[24px] font-semibold mb-2">找到问题快照！</h3>
          <p class="text-[14px] text-on-surface-variant">以下快照引入了问题：</p>
        </div>

        <div class="bg-error/5 border border-error/20 rounded-xl p-6 text-center">
          <h4 class="text-[20px] font-semibold text-error mb-1">{{ bisectSession.culpritSnapshotName }}</h4>
          <p class="text-[12px] text-on-surface-variant">建议回滚此快照或排查此时间点的变更</p>
        </div>

        <div class="flex gap-3">
          <button @click="showBisect = false; bisectSession = null" class="flex-1 py-3 bg-surface-container-high text-on-surface font-bold rounded-xl text-[12px] hover:bg-surface-container-highest transition-all">
            关闭
          </button>
        </div>
      </div>
    </div>

    <!-- Comparison Panel (toggled) -->
    <div v-if="compareMode" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">compare</span>
          <div>
            <h3 class="text-[20px] font-semibold">快照对比</h3>
            <p class="text-[12px] text-on-surface-variant">对比两个快照之间的差异</p>
          </div>
        </div>
        <button @click="compareMode = false; compareResult = null" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>
      <div class="p-6 space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">基准快照 (From)</label>
            <select v-model="compareId" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none appearance-none">
              <option :value="0" disabled>选择基准快照</option>
              <option v-for="s in snapshots" :key="s.id" :value="s.id">{{ s.name || s.title || '快照' }} ({{ s.createdAt ? new Date(s.createdAt).toLocaleDateString('zh-CN') : '' }})</option>
            </select>
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">目标快照 (To)</label>
            <div class="px-3 py-2 bg-surface-container/50 border border-outline-variant/20 rounded-lg text-[13px] text-on-surface-variant">
              {{ selectedSnapshot?.name || selectedSnapshot?.title || '未选择' }}
            </div>
          </div>
        </div>
        <button @click="runComparison" :disabled="!compareId || comparing"
          class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50 flex items-center gap-1.5">
          <span class="material-symbols-outlined text-[16px]">{{ comparing ? 'hourglass_empty' : 'compare' }}</span>
          {{ comparing ? '对比中...' : '开始对比' }}
        </button>

        <!-- Comparison Result -->
        <div v-if="compareResult" class="space-y-4">
          <div class="flex gap-4">
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-500/10 text-green-600">+{{ compareResult.addedCount }} 新增</span>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-primary/10 text-primary">~{{ compareResult.modifiedCount }} 修改</span>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-error/10 text-error">-{{ compareResult.deletedCount }} 删除</span>
          </div>
          <div v-if="compareResult.diffs.length === 0" class="text-center py-6">
            <span class="material-symbols-outlined text-[36px] text-green-500" style="font-variation-settings: 'FILL' 1;">check_circle</span>
            <p class="text-[13px] text-on-surface-variant mt-2">两个快照完全相同</p>
          </div>
          <div v-else class="space-y-1 max-h-[300px] overflow-y-auto">
            <div v-for="(diff, i) in compareResult.diffs.slice(0, 50)" :key="i"
              class="flex items-center gap-3 px-3 py-1.5 rounded-lg text-[12px] font-[Geist]"
              :class="diff.changeType === 'added' ? 'bg-green-50 text-green-700' : diff.changeType === 'deleted' ? 'bg-red-50 text-red-700' : 'bg-primary/5 text-primary'">
              <span class="w-5 text-center shrink-0">
                {{ diff.changeType === 'added' ? '+' : diff.changeType === 'deleted' ? '-' : '~' }}
              </span>
              <span class="truncate">{{ diff.path }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Cherry-Pick Dialog (toggled) -->
    <div v-if="showCherryPickDialog" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">content_paste</span>
          <div>
            <h3 class="text-[20px] font-semibold">应用文件到其他服务器 (Cherry-pick)</h3>
            <p class="text-[12px] text-on-surface-variant">从快照中提取指定文件并应用到目标服务器</p>
          </div>
        </div>
        <button @click="showCherryPickDialog = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>
      <div class="p-6 space-y-5">
        <div class="bg-primary/5 border border-primary/20 rounded-xl p-4">
          <p class="text-[12px] font-bold text-primary mb-1">源快照</p>
          <p class="text-[14px]">{{ selectedSnapshot?.name || selectedSnapshot?.title || '快照' }} ({{ selectedSnapshot?.hash ? selectedSnapshot.hash.substring(0, 8) + '...' : '' }})</p>
        </div>

        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">文件路径（每行一个）</label>
          <textarea v-model="cherryPickFiles" rows="5"
            class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px] font-[Geist]"
            placeholder="/etc/nginx/nginx.conf&#10;/var/www/app/config.yml&#10;/etc/crontab"></textarea>
        </div>

        <div class="space-y-2">
          <label class="block text-[12px] font-bold text-on-surface-variant">目标服务器</label>
          <select v-model="cherryPickTargetId" class="w-full px-4 py-3 bg-white/50 border border-outline-variant rounded-xl focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none text-[14px] appearance-none">
            <option :value="0" disabled>选择目标服务器</option>
            <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }} ({{ s.ip }})</option>
          </select>
        </div>

        <div class="flex justify-end gap-3">
          <button @click="showCherryPickDialog = false" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
          <button @click="executeCherryPick" :disabled="!cherryPickFiles.trim() || !cherryPickTargetId || cherryPicking"
            class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">{{ cherryPicking ? 'hourglass_empty' : 'content_paste' }}</span>
            {{ cherryPicking ? '应用中...' : '应用文件' }}
          </button>
        </div>
      </div>
    </div>

    <!-- File Browser Panel (toggled) -->
    <div v-if="showFileBrowser" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">folder_open</span>
          <div>
            <h3 class="text-[20px] font-semibold">浏览快照文件</h3>
            <p class="text-[12px] text-on-surface-variant font-[Geist]">{{ fileBrowserPath }}</p>
          </div>
        </div>
        <button @click="showFileBrowser = false; showFilePreview = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>
      <div class="flex">
        <!-- File tree -->
        <div class="flex-1 p-4 max-h-[400px] overflow-y-auto">
          <div v-if="fileBrowserPath !== '/'" @click="navigateUp"
            class="flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer hover:bg-surface-container-high transition-colors mb-1">
            <span class="material-symbols-outlined text-[16px] text-outline">arrow_upward</span>
            <span class="text-[12px] text-outline">返回上级</span>
          </div>
          <div v-if="fileBrowserLoading" class="space-y-2">
            <div v-for="i in 5" :key="i" class="h-10 animate-pulse bg-surface-container-highest rounded-lg"></div>
          </div>
          <div v-else-if="fileEntries.length === 0" class="text-center py-8">
            <span class="material-symbols-outlined text-[36px] text-outline/40">folder_off</span>
            <p class="text-[13px] text-outline mt-2">此目录为空</p>
          </div>
          <div v-else>
            <div v-for="entry in fileEntries" :key="entry.path" @click="navigateToEntry(entry)"
              class="flex items-center gap-3 px-3 py-2 rounded-lg cursor-pointer hover:bg-surface-container-high transition-colors">
              <span class="material-symbols-outlined text-[18px]"
                :class="entry.type === 'DIRECTORY' ? 'text-primary' : 'text-outline'">
                {{ entry.type === 'DIRECTORY' ? 'folder' : 'description' }}
              </span>
              <div class="flex-1 min-w-0">
                <p class="text-[13px] truncate">{{ entry.name }}</p>
              </div>
              <span v-if="entry.type === 'FILE'" class="text-[10px] text-outline font-[Geist]">{{ formatFileSize(entry.size) }}</span>
            </div>
          </div>
        </div>
        <!-- File preview -->
        <div v-if="showFilePreview" class="w-[350px] border-l border-outline-variant/30 bg-surface-container-low/50">
          <div class="px-4 py-3 border-b border-outline-variant/20 flex justify-between items-center">
            <p class="text-[11px] font-bold text-on-surface-variant truncate">{{ filePreviewPath }}</p>
            <button @click="showFilePreview = false" class="p-1 hover:bg-surface-container-high rounded transition-colors">
              <span class="material-symbols-outlined text-[14px]">close</span>
            </button>
          </div>
          <pre class="p-4 text-[11px] font-[Geist] text-on-surface-variant overflow-auto max-h-[340px] whitespace-pre-wrap break-words">{{ filePreviewContent }}</pre>
        </div>
      </div>
    </div>

    <!-- Hero Section -->
    <section class="grid grid-cols-1 lg:grid-cols-12 gap-[16px] items-start">
      <!-- Vertical Timeline Column -->
      <div class="lg:col-span-5 relative">
        <div class="absolute left-6 top-0 bottom-0 w-[2px] opacity-30" style="background: linear-gradient(to bottom, transparent, #0058be, #924700, transparent);"></div>
        <div class="space-y-12">
          <div v-for="snap in snapshots" :key="snap.id" class="relative pl-16 group" :class="snap.status === 'ARCHIVED' ? 'opacity-70 hover:opacity-100' : ''">
            <div class="absolute left-[20px] top-1 w-3 h-3 rounded-full ring-4 z-10" :class="dotColors[snap.status] || dotColors.STABLE"></div>
            <div @click="selectSnapshot(snap)" class="glass-panel p-[20px] rounded-xl border-l-4 hover:translate-x-1 transition-transform cursor-pointer" :class="[(borderColors[snap.status] || borderColors.STABLE), selectedSnapshot?.id === snap.id ? 'shimmer-edge' : '']">
              <div class="flex justify-between items-start mb-2">
                <span class="text-[12px] font-bold tracking-wide" :class="snap.status === 'STABLE' ? 'text-primary' : snap.status === 'WARNING' ? 'text-tertiary' : 'text-outline'">{{ snap.createdAt ? new Date(snap.createdAt).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : snap.date || 'N/A' }}</span>
                <span class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase" :class="statusColors[snap.status] || statusColors.STABLE">{{ snap.status || 'Stable' }}</span>
              </div>
              <h3 class="text-[24px] font-semibold mb-1">{{ snap.name || snap.title || '快照' }}</h3>
              <p class="text-on-surface-variant text-[14px] mb-3">{{ snap.description || snap.note || '' }}</p>
              <!-- Tags -->
              <div v-if="snap.tags && snap.tags.length" class="flex flex-wrap gap-1.5 mb-3">
                <span
                  v-for="tag in snap.tags"
                  :key="tag.id"
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium"
                  :style="{ backgroundColor: tag.color + '20', color: tag.color, border: '1px solid ' + tag.color + '40' }"
                >
                  {{ tag.name }}
                  <button
                    @click.stop="handleRemoveTag(snap.id, tag.name)"
                    class="ml-0.5 hover:opacity-70 transition-opacity"
                    title="移除标签"
                  >
                    <span class="material-symbols-outlined text-[12px]">close</span>
                  </button>
                </span>
              </div>
              <div class="flex gap-2 items-center">
                <span v-if="snap.hash" class="text-[10px] text-outline">Hash: {{ snap.hash }}</span>
              </div>
            </div>
          </div>
          <div v-if="!snapshots.length" class="relative pl-16 group">
            <div class="absolute left-[20px] top-1 w-3 h-3 rounded-full bg-outline ring-4 ring-outline/10 z-10"></div>
            <div class="glass-panel p-[20px] rounded-xl border-l-4 border-l-outline-variant">
              <p class="text-on-surface-variant text-[14px]">暂无快照数据</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Details & Diff View Column -->
      <div class="lg:col-span-7 space-y-[16px]">
        <!-- Time Travel Control Card -->
        <div class="glass-panel p-6 rounded-2xl bg-gradient-to-br from-primary/5 to-transparent relative overflow-hidden">
          <div class="absolute -right-10 -top-10 w-40 h-40 bg-primary/10 rounded-full blur-3xl"></div>
          <div class="relative z-10 flex flex-col md:flex-row justify-between items-center gap-6">
            <div>
              <div class="flex items-center gap-2 mb-2">
                <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">auto_awesome</span>
                <h2 class="text-[24px] font-semibold">时间穿梭控制台</h2>
              </div>
              <p class="text-on-surface-variant text-[14px] max-w-md">当前选定：{{ selectedSnapshot?.name || '未选择' }} {{ selectedSnapshot?.createdAt ? '(' + new Date(selectedSnapshot.createdAt).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) + ')' : '' }}。您可以快速回滚到任何历史状态，ChronoVault 将自动处理网络路由与持久化卷映射。</p>
            </div>
            <button @click="openRollbackConfirm" class="w-full md:w-auto bg-primary text-white font-bold px-8 py-4 rounded-2xl shadow-xl shadow-primary/30 hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3">
              <span class="material-symbols-outlined">settings_backup_restore</span>
              立即执行回滚 (Rollback)
            </button>
            <button @click="openRevertConfirm" class="w-full md:w-auto bg-tertiary-container text-on-tertiary-container font-bold px-6 py-4 rounded-2xl shadow-lg hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3">
              <span class="material-symbols-outlined">undo</span>
              撤销此快照
            </button>
            <button @click="showBisect = !showBisect" class="w-full md:w-auto bg-secondary-container text-on-secondary-container font-bold px-6 py-4 rounded-2xl shadow-lg hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3">
              <span class="material-symbols-outlined">binary</span>
              二分查找
            </button>
            <button @click="openFileBrowser" class="w-full md:w-auto bg-surface-container-high text-on-surface font-bold px-6 py-4 rounded-2xl shadow-lg hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3">
              <span class="material-symbols-outlined">folder_open</span>
              浏览文件
            </button>
            <button @click="verifySnapshot" :disabled="verifying" class="w-full md:w-auto bg-surface-container-high text-on-surface font-bold px-6 py-4 rounded-2xl shadow-lg hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3 disabled:opacity-50">
              <span class="material-symbols-outlined">{{ verifying ? 'hourglass_empty' : 'verified' }}</span>
              {{ verifying ? '验证中...' : '验证快照' }}
            </button>
            <button @click="openCherryPickDialog" class="w-full md:w-auto bg-surface-container-high text-on-surface font-bold px-6 py-4 rounded-2xl shadow-lg hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3">
              <span class="material-symbols-outlined">content_paste</span>
              应用到...
            </button>
            <button @click="compareMode = !compareMode" class="w-full md:w-auto bg-surface-container-high text-on-surface font-bold px-6 py-4 rounded-2xl shadow-lg hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-3"
              :class="compareMode ? 'ring-2 ring-primary' : ''">
              <span class="material-symbols-outlined">compare</span>
              对比
            </button>
          </div>
        </div>

        <!-- Tags Panel -->
        <div v-if="selectedSnapshot" class="glass-panel p-5 rounded-2xl border border-outline-variant/20">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-primary text-[20px]">label</span>
              <span class="text-[12px] font-bold uppercase tracking-wider text-on-surface-variant">标签</span>
            </div>
            <button @click="openAddTagModal" class="text-[12px] font-bold text-primary flex items-center gap-1 hover:gap-2 transition-all">
              <span class="material-symbols-outlined text-[16px]">add_circle</span>
              添加标签
            </button>
          </div>
          <div v-if="selectedSnapshot.tags && selectedSnapshot.tags.length" class="flex flex-wrap gap-2">
            <span
              v-for="tag in selectedSnapshot.tags"
              :key="tag.id"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[12px] font-medium"
              :style="{ backgroundColor: tag.color + '20', color: tag.color, border: '1px solid ' + tag.color + '40' }"
            >
              {{ tag.name }}
              <button
                @click="handleRemoveTag(selectedSnapshot!.id, tag.name)"
                class="hover:opacity-70 transition-opacity"
                title="移除标签"
              >
                <span class="material-symbols-outlined text-[14px]">close</span>
              </button>
            </span>
          </div>
          <p v-else class="text-[13px] text-outline">暂无标签，点击上方按钮添加</p>
        </div>

        <!-- Diff Compare View -->
        <div class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
          <div class="bg-surface-container-high/50 px-6 py-4 flex justify-between items-center border-b border-outline-variant/20">
            <div class="flex items-center gap-4">
              <span class="text-[12px] font-bold uppercase tracking-wider text-on-surface-variant">差异对比</span>
              <div class="flex items-center gap-2 bg-surface px-3 py-1 rounded-full border border-outline-variant/30 text-[12px]">
                <span class="text-outline">当前</span>
                <span class="material-symbols-outlined text-[14px]">arrow_right_alt</span>
                <span class="font-bold text-primary">{{ selectedSnapshot?.name || '快照' }}</span>
              </div>
            </div>
            <div class="flex gap-1">
              <div class="w-2.5 h-2.5 rounded-full bg-error/40"></div>
              <div class="w-2.5 h-2.5 rounded-full bg-tertiary/40"></div>
              <div class="w-2.5 h-2.5 rounded-full bg-primary/40"></div>
            </div>
          </div>
          <div class="p-6 font-[Geist] text-[14px] space-y-4">
            <div class="grid grid-cols-12 gap-4 border-b border-outline-variant/10 pb-2">
              <div class="col-span-4 text-outline">配置路径</div>
              <div class="col-span-4 text-outline">变更前</div>
              <div class="col-span-4 text-outline">变更后</div>
            </div>
            <div v-for="diff in diffs" :key="diff.path" class="grid grid-cols-12 gap-4 items-center">
              <div class="col-span-4 text-on-surface-variant">{{ diff.path }}</div>
              <div class="col-span-4 bg-error/5 text-error px-2 py-1 rounded border border-error/10 overflow-hidden text-ellipsis">{{ diff.prev }}</div>
              <div class="col-span-4 bg-primary/5 text-primary px-2 py-1 rounded border border-primary/10 overflow-hidden text-ellipsis">{{ diff.next }}</div>
            </div>
            <div class="pt-4 mt-4 border-t border-outline-variant/10">
              <div class="flex items-center justify-between">
                <div class="text-[12px] text-outline">
                  <span v-if="diffs.length" class="text-primary font-bold">{{ diffs.length }} 项变更</span>
                  <span v-else class="text-outline">暂无差异数据</span>
                </div>
                <button @click="showFullDiff" class="text-primary text-[12px] font-bold flex items-center gap-1 hover:underline">
                  查看完整 JSON 差异
                  <span class="material-symbols-outlined text-[16px]">open_in_new</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- AI Insight Card -->
        <div class="glass-panel p-6 rounded-2xl border-l-4 border-l-primary/40 bg-gradient-to-r from-surface-container-lowest to-transparent">
          <div class="flex gap-4">
            <div class="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center shrink-0 text-primary">
              <span class="material-symbols-outlined text-[28px]" style="font-variation-settings: 'FILL' 1;">psychology</span>
            </div>
            <div>
              <h4 class="text-[24px] font-semibold text-primary mb-1">AI 架构师洞察</h4>
              <p v-if="selectedSnapshot && diffs.length" class="text-on-surface-variant text-[14px] leading-relaxed mb-3">
                当前快照包含 {{ diffs.length }} 项文件变更。建议在回滚前确认变更内容，以避免潜在的服务中断。
              </p>
              <p v-else class="text-on-surface-variant text-[14px] leading-relaxed mb-3">
                选择一个快照查看 AI 分析洞察。
              </p>
              <button @click="showOperationSteps" class="text-[14px] font-bold text-primary flex items-center gap-1 hover:gap-2 transition-all">
                查看建议的操作流程
                <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Footer Stats -->
    <footer class="grid grid-cols-2 md:grid-cols-4 gap-[16px] pt-8 border-t border-outline-variant/20">
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">总大小</p>
        <p class="text-[32px] font-semibold">{{ stats.totalSize ? formatBytes(stats.totalSize) : '-' }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">快照数</p>
        <p class="text-[32px] font-semibold">{{ snapshots.length }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">平均大小</p>
        <p class="text-[32px] font-semibold">{{ stats.avgSize || '-' }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-outline text-[12px] font-bold uppercase tracking-widest">最新快照</p>
        <p class="text-[32px] font-semibold text-primary">{{ stats.latestDate || '-' }}</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { snapshotsApi } from '@/api/snapshots'
import { formatBytes } from '@/utils/format'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import AddTagModal from '@/components/modals/AddTagModal.vue'

const toast = useToastStore()
const modal = useModalStore()

import type { Snapshot, SnapshotDiff, BisectSession, SnapshotFileEntry, SnapshotVerifyResult, ContainerState, FileDiffSummary, StateSnapshot } from '@/types'
import { serversApi } from '@/api/servers'

const snapshots = ref<Snapshot[]>([])
const selectedSnapshot = ref<Snapshot | null>(null)
const diffs = ref<SnapshotDiff[]>([])
const stats = ref({ totalSize: 0, avgSize: '-', latestDate: '' })

// Bisect state
const showBisect = ref(false)
const bisectSession = ref<BisectSession | null>(null)
const bisectGoodId = ref(0)
const bisectBadId = ref(0)
const bisectStarting = ref(false)

// Cherry-pick state
const showCherryPickDialog = ref(false)
const cherryPickFiles = ref('')
const cherryPickTargetId = ref(0)
const cherryPicking = ref(false)
const servers = ref<any[]>([])

// File browser state
const showFileBrowser = ref(false)
const fileEntries = ref<SnapshotFileEntry[]>([])
const fileBrowserPath = ref('/')
const fileBrowserLoading = ref(false)
const filePreviewContent = ref('')
const filePreviewPath = ref('')
const showFilePreview = ref(false)

// Comparison state
const compareMode = ref(false)
const compareId = ref(0)
const compareResult = ref<FileDiffSummary | null>(null)
const comparing = ref(false)

// Verify state
const verifying = ref(false)
const verifyResult = ref<SnapshotVerifyResult | null>(null)

// Container state
const containerStates = ref<ContainerState[]>([])
const showContainers = ref(false)

// System state (state.json)
const stateSnapshot = ref<StateSnapshot | null>(null)
const showState = ref(false)
const loadingState = ref(false)

async function openRollbackConfirm() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }

  // Fetch rollback preview first
  try {
    const preview = await snapshotsApi.rollbackPreview(selectedSnapshot.value.id)
    const previewInfo = [
      `服务器: ${preview.serverName} (${preview.serverIp})`,
      `目标快照: ${preview.snapshotTitle}`,
      `备份大小: ${preview.sizeBytes ? (preview.sizeBytes / 1024 / 1024).toFixed(1) + ' MB' : '未知'}`,
      `存储类型: ${preview.storageType || '未知'}`,
      `预估恢复时间: ${preview.estimatedRestoreTimeSeconds} 秒`,
      `备份数据: ${preview.hasValidBackup ? '✓ 有效' : '✗ 无效'}`,
    ].join('\n')

    modal.open({
      component: ConfirmModal,
      title: '确认回滚',
      props: {
        message: `即将回滚至 ${selectedSnapshot.value?.name || '选定快照'}。\n\n${previewInfo}\n\n此操作将覆盖当前系统状态，期间 API 服务将短暂离线。是否继续？`,
        confirmText: '执行回滚',
        confirmClass: 'bg-error hover:bg-error/90',
        successMessage: '回滚任务已提交，预计 ' + preview.estimatedRestoreTimeSeconds + ' 秒完成',
        onConfirm: async () => {
          if (selectedSnapshot.value) {
            await snapshotsApi.rollback(selectedSnapshot.value.id)
          }
        },
      },
    })
  } catch (e: any) {
    // Fallback to simple dialog if preview fails
    modal.open({
      component: ConfirmModal,
      title: '确认回滚',
      props: {
        message: `即将回滚至 ${selectedSnapshot.value?.name || '选定快照'}。此操作将覆盖当前系统状态，期间 API 服务将短暂离线约 45-60 秒。是否继续？`,
        confirmText: '执行回滚',
        confirmClass: 'bg-error hover:bg-error/90',
        successMessage: '回滚任务已提交，预计 60 秒完成',
        onConfirm: async () => {
          if (selectedSnapshot.value) {
            await snapshotsApi.rollback(selectedSnapshot.value.id)
          }
        },
      },
    })
  }
}

function openRevertConfirm() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  modal.open({
    component: ConfirmModal,
    title: '确认撤销快照',
    props: {
      message: `即将撤销快照 "${selectedSnapshot.value?.name || '选定快照'}" 的变更。系统将自动创建安全快照，然后恢复此快照之前的状态。是否继续？`,
      confirmText: '执行撤销',
      confirmClass: 'bg-tertiary hover:bg-tertiary/90',
      successMessage: '撤销任务已提交，正在后台执行',
      onConfirm: async () => {
        if (selectedSnapshot.value) {
          const result = await snapshotsApi.revert(selectedSnapshot.value.id)
          toast.success(result || '撤销完成')
        }
      },
    },
  })
}

function showFullDiff() {
  if (!diffs.value.length) {
    toast.error('暂无差异数据')
    return
  }
  const json = JSON.stringify(diffs.value, null, 2)
  modal.open({
    component: ConfirmModal,
    title: '完整 JSON 差异',
    props: {
      message: json,
      confirmText: '关闭',
      confirmClass: 'bg-surface-container-highest text-on-surface',
    },
  })
}

function showOperationSteps() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  const steps = [
    `1. 确认当前服务状态：检查 ${selectedSnapshot.value.serverName || '目标服务器'} 上的服务是否正常运行`,
    `2. 创建当前状态备份：在回滚前先创建一个应急快照`,
    `3. 执行回滚：将系统恢复至 ${selectedSnapshot.value.name || '选定快照'}`,
    '4. 验证服务：确认所有服务已正常启动并通过健康检查',
    '5. 通知团队：告知相关成员回滚已完成',
  ]
  modal.open({
    component: ConfirmModal,
    title: '建议的操作流程',
    props: {
      message: steps.join('\n\n'),
      confirmText: '已了解',
      confirmClass: 'bg-primary text-white',
    },
  })
}

function openAddTagModal() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  const snapId = selectedSnapshot.value.id
  modal.open({
    component: AddTagModal,
    title: '添加标签',
    width: 'max-w-md',
    props: {
      snapshotId: snapId,
      existingTags: selectedSnapshot.value.tags || [],
      onAdded: () => {
        refreshSnapshotTags(snapId)
      },
    },
  })
}

async function handleRemoveTag(snapshotId: number, tagName: string) {
  try {
    await snapshotsApi.removeTag(snapshotId, tagName)
    toast.success(`标签 "${tagName}" 已移除`)
    await refreshSnapshotTags(snapshotId)
  } catch (e: any) {
    toast.error(e?.message || '移除标签失败')
  }
}

async function refreshSnapshotTags(snapshotId: number) {
  try {
    const updated = await snapshotsApi.get(snapshotId)
    // Update the tags in the snapshots list
    const idx = snapshots.value.findIndex(s => s.id === snapshotId)
    if (idx >= 0) {
      snapshots.value[idx] = { ...snapshots.value[idx], tags: updated.tags || [] }
    }
    // Update selected snapshot
    if (selectedSnapshot.value?.id === snapshotId) {
      selectedSnapshot.value = { ...selectedSnapshot.value, tags: updated.tags || [] }
    }
  } catch (e) {
    console.error('Failed to refresh tags', e)
  }
}

async function startBisect() {
  if (!bisectGoodId.value || !bisectBadId.value) {
    toast.error('请选择好快照和坏快照')
    return
  }
  if (bisectGoodId.value === bisectBadId.value) {
    toast.error('好快照和坏快照不能相同')
    return
  }
  const serverId = snapshots.value.find(s => s.id === bisectGoodId.value)?.serverId
  if (!serverId) {
    toast.error('无法确定服务器信息')
    return
  }
  bisectStarting.value = true
  try {
    const session = await snapshotsApi.bisectStart({
      serverId,
      goodSnapshotId: bisectGoodId.value,
      badSnapshotId: bisectBadId.value,
    })
    bisectSession.value = session
    toast.success('二分查找已启动，共需 ' + session.totalSteps + ' 步')
  } catch (e: any) {
    toast.error(e?.message || '启动二分查找失败')
  } finally {
    bisectStarting.value = false
  }
}

async function markBisect(verdict: 'good' | 'bad') {
  if (!bisectSession.value) return
  try {
    const result = await snapshotsApi.bisectMark(bisectSession.value.sessionId, {
      snapshotId: bisectSession.value.currentSnapshotId,
      verdict,
    })
    bisectSession.value = result
    if (result.status === 'FOUND') {
      toast.success('已定位到问题快照：' + result.culpritSnapshotName)
    }
  } catch (e: any) {
    toast.error(e?.message || '标记失败')
  }
}

async function openFileBrowser() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  showFileBrowser.value = true
  fileBrowserPath.value = '/'
  await loadFiles('/')
}

async function loadFiles(path: string) {
  if (!selectedSnapshot.value) return
  fileBrowserLoading.value = true
  fileBrowserPath.value = path
  try {
    const res = await snapshotsApi.listFiles(selectedSnapshot.value.id, path)
    fileEntries.value = res || []
  } catch (e: any) {
    toast.error(e?.message || '加载文件列表失败')
    fileEntries.value = []
  } finally {
    fileBrowserLoading.value = false
  }
}

function navigateToEntry(entry: SnapshotFileEntry) {
  if (entry.type === 'DIRECTORY') {
    loadFiles(entry.path)
  } else {
    previewFile(entry.path)
  }
}

function navigateUp() {
  const parts = fileBrowserPath.value.split('/').filter(Boolean)
  parts.pop()
  loadFiles('/' + parts.join('/') || '/')
}

async function previewFile(path: string) {
  if (!selectedSnapshot.value) return
  filePreviewPath.value = path
  showFilePreview.value = true
  filePreviewContent.value = '加载中...'
  try {
    const res = await snapshotsApi.downloadFile(selectedSnapshot.value.id, path)
    filePreviewContent.value = res || '(空文件)'
  } catch (e: any) {
    filePreviewContent.value = '加载失败: ' + (e?.message || '未知错误')
  }
}

async function verifySnapshot() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  verifying.value = true
  verifyResult.value = null
  try {
    const result = await snapshotsApi.verify(selectedSnapshot.value.id)
    verifyResult.value = result
    if (result.verified) {
      toast.success('快照完整性验证通过')
    } else {
      toast.error('快照验证发现问题: ' + (result.errors || '未知错误'))
    }
  } catch (e: any) {
    toast.error(e?.message || '验证失败')
  } finally {
    verifying.value = false
  }
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i]
}

async function openCherryPickDialog() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  // Load servers if not loaded
  if (servers.value.length === 0) {
    try {
      const res = await serversApi.getAll()
      servers.value = res || []
    } catch (e) {
      console.error('Failed to load servers', e)
    }
  }
  showCherryPickDialog.value = true
}

async function executeCherryPick() {
  if (!selectedSnapshot.value) return
  const files = cherryPickFiles.value.split('\n').map(f => f.trim()).filter(f => f.length > 0)
  if (files.length === 0) {
    toast.error('请输入至少一个文件路径')
    return
  }
  if (!cherryPickTargetId.value) {
    toast.error('请选择目标服务器')
    return
  }
  cherryPicking.value = true
  try {
    const result = await snapshotsApi.cherryPick(selectedSnapshot.value.id, {
      files,
      targetServerId: cherryPickTargetId.value,
    })
    toast.success(result || 'Cherry-pick 完成')
    showCherryPickDialog.value = false
    cherryPickFiles.value = ''
    cherryPickTargetId.value = 0
  } catch (e: any) {
    toast.error(e?.message || 'Cherry-pick 失败')
  } finally {
    cherryPicking.value = false
  }
}

function selectSnapshot(snap: Snapshot) {
  selectedSnapshot.value = snap
  loadDiff(snap.id)
  loadContainers(snap.id)
  loadStateSnapshot(snap.id)
}

async function loadStateSnapshot(snapshotId: number) {
  loadingState.value = true
  stateSnapshot.value = null
  try {
    const res = await snapshotsApi.getState(snapshotId)
    if (res) {
      // The API returns the state JSON as a string, parse it
      const parsed = typeof res === 'string' ? JSON.parse(res) : res
      stateSnapshot.value = parsed
      showState.value = true
    }
  } catch (e) {
    // State may not be available for this snapshot
    stateSnapshot.value = null
  } finally {
    loadingState.value = false
  }
}

async function loadContainers(snapshotId: number) {
  try {
    const res = await snapshotsApi.getContainers(snapshotId)
    containerStates.value = res || []
  } catch (e) {
    containerStates.value = []
  }
}

async function runComparison() {
  if (!selectedSnapshot.value || !compareId.value) {
    toast.error('请选择要对比的快照')
    return
  }
  if (selectedSnapshot.value.id === compareId.value) {
    toast.error('不能与自身对比')
    return
  }
  comparing.value = true
  compareResult.value = null
  try {
    const result = await snapshotsApi.compare(selectedSnapshot.value.id, compareId.value)
    compareResult.value = result
  } catch (e: any) {
    toast.error(e?.message || '对比失败')
  } finally {
    comparing.value = false
  }
}

async function loadDiff(id: number) {
  try {
    const res = await snapshotsApi.getDiff(id)
    diffs.value = res || []
  } catch (e) {
    console.error('Failed to load diff', e)
  }
}

const statusColors: Record<string, string> = {
  STABLE: 'bg-primary/10 text-primary',
  WARNING: 'bg-tertiary/10 text-tertiary',
  ARCHIVED: 'bg-outline/10 text-outline',
}

const dotColors: Record<string, string> = {
  STABLE: 'bg-primary ring-primary/20 shadow-[0_0_15px_rgba(0,88,190,0.5)]',
  WARNING: 'bg-tertiary ring-tertiary/20 shadow-[0_0_15px_rgba(146,71,0,0.5)]',
  ARCHIVED: 'bg-outline ring-outline/10',
}

const borderColors: Record<string, string> = {
  STABLE: 'border-l-primary',
  WARNING: 'border-l-tertiary',
  ARCHIVED: 'border-l-outline-variant',
}

onMounted(async () => {
  try {
    const [snapshotsRes] = await Promise.all([
      snapshotsApi.getAll(),
    ])
    const data = snapshotsRes || []
    snapshots.value = data
    if (data.length > 0) {
      selectSnapshot(data[0])
    }
    const totalSize = data.reduce((acc: number, s: Snapshot) => acc + (s.sizeBytes || 0), 0)
    const avgSize = data.length > 0 ? Math.round(totalSize / data.length) : 0
    const latestDate = data.length > 0 && data[0].createdAt
      ? new Date(data[0].createdAt).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
      : ''
    stats.value = {
      totalSize,
      avgSize: avgSize ? formatBytes(avgSize) : '-',
      latestDate,
    }
  } catch (e) {
    console.error('Failed to load snapshots', e)
  }
})
</script>
