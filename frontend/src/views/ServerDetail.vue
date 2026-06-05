<template>
  <div class="p-[24px] space-y-[40px] pb-20">
    <!-- Server Header Section -->
    <section class="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <h2 class="text-[32px] font-semibold text-on-surface">{{ server.name || '加载中...' }}</h2>
          <span class="px-2 py-0.5 rounded-full text-[10px] font-bold border uppercase tracking-wider flex items-center gap-1"
            :class="serverStatusClass">
            <span class="w-1.5 h-1.5 rounded-full animate-pulse" :class="serverStatusDotClass"></span>
            {{ serverStatusText }}
          </span>
          <!-- Branch Selector -->
          <div v-if="branches.length > 0" class="relative">
            <button @click="showBranchDropdown = !showBranchDropdown"
              class="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-secondary-container/30 border border-secondary-container text-[11px] font-bold text-on-secondary-container hover:bg-secondary-container/50 transition-all">
              <span class="material-symbols-outlined text-[14px]">call_split</span>
              {{ activeBranch?.name || 'main' }}
              <span class="material-symbols-outlined text-[12px]">expand_more</span>
            </button>
            <!-- Dropdown -->
            <div v-if="showBranchDropdown" class="absolute top-full left-0 mt-2 w-72 bg-surface-container rounded-xl border border-outline-variant/30 shadow-xl z-50 overflow-hidden">
              <div class="p-3 border-b border-outline-variant/20">
                <p class="text-[11px] font-bold text-on-surface-variant mb-2">切换分支</p>
                <div v-for="branch in branches" :key="branch.id"
                  @click="switchBranch(branch)"
                  class="flex items-center justify-between px-3 py-2 rounded-lg cursor-pointer transition-colors"
                  :class="activeBranch?.id === branch.id ? 'bg-primary/10 text-primary' : 'hover:bg-surface-container-high text-on-surface'">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-[16px]"
                      :class="activeBranch?.id === branch.id ? 'text-primary' : 'text-outline'">call_split</span>
                    <span class="text-[12px] font-bold">{{ branch.name }}</span>
                    <span v-if="branch.isDefault" class="text-[9px] px-1.5 py-0.5 rounded-full bg-primary/10 text-primary font-bold">默认</span>
                  </div>
                  <button v-if="!branch.isDefault" @click.stop="renameBranchPrompt(branch)"
                    class="p-1 rounded hover:bg-primary/10 text-outline hover:text-primary transition-colors" title="重命名">
                    <span class="material-symbols-outlined text-[14px]">edit</span>
                  </button>
                  <button v-if="!branch.isDefault" @click.stop="deleteBranch(branch)"
                    class="p-1 rounded hover:bg-error/10 text-outline hover:text-error transition-colors" title="删除">
                    <span class="material-symbols-outlined text-[14px]">delete</span>
                  </button>
                </div>
              </div>
              <!-- Merge branch -->
              <div v-if="branches.length >= 2" class="p-3 border-t border-outline-variant/20 bg-surface-container-low/50">
                <p class="text-[11px] font-bold text-on-surface-variant mb-2">合并分支</p>
                <div class="flex gap-2">
                  <select v-model="mergeSourceBranchId" class="flex-1 px-3 py-1.5 bg-white/50 border border-outline-variant rounded-lg text-[12px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none appearance-none">
                    <option value="">选择源分支</option>
                    <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
                  </select>
                  <button @click="mergeBranches" :disabled="!mergeSourceBranchId || !activeBranch || mergeSourceBranchId === activeBranch.id || mergingBranch"
                    class="px-3 py-1.5 bg-primary text-white rounded-lg text-[11px] font-bold hover:bg-primary/90 transition-all disabled:opacity-50">
                    {{ mergingBranch ? '...' : '合并' }}
                  </button>
                </div>
              </div>
              <!-- Create new branch -->
              <div class="p-3 bg-surface-container-low/50">
                <p class="text-[11px] font-bold text-on-surface-variant mb-2">创建新分支</p>
                <div class="flex gap-2">
                  <input v-model="newBranchName" @keydown.enter="createBranch"
                    class="flex-1 px-3 py-1.5 bg-white/50 border border-outline-variant rounded-lg text-[12px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
                    placeholder="分支名称" />
                  <button @click="createBranch" :disabled="!newBranchName.trim() || creatingBranch"
                    class="px-3 py-1.5 bg-primary text-white rounded-lg text-[11px] font-bold hover:bg-primary/90 transition-all disabled:opacity-50">
                    {{ creatingBranch ? '...' : '创建' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="flex flex-wrap gap-x-6 gap-y-2 text-on-surface-variant text-[14px] opacity-80">
          <div class="flex items-center gap-1.5">
            <span class="material-symbols-outlined text-sm">lan</span>
            {{ server.ip || '-' }}
          </div>
          <div class="flex items-center gap-1.5">
            <span class="material-symbols-outlined text-sm">laptop_windows</span>
            {{ server.os || '-' }}
          </div>
          <div class="flex items-center gap-1.5">
            <span class="material-symbols-outlined text-sm">schedule</span>
            已运行: {{ formatUptime(server.uptimeSeconds) }}
          </div>
        </div>
      </div>
      <div class="flex gap-3">
        <button @click="showBlamePanel = !showBlamePanel" class="px-4 py-2 rounded-lg bg-surface-container-lowest text-on-surface border border-outline-variant/50 text-[12px] font-bold flex items-center gap-2 hover:bg-surface-container-high transition-all">
          <span class="material-symbols-outlined text-lg">person_search</span>
          变更历史
        </button>
        <button @click="showHooksPanel = !showHooksPanel" class="px-4 py-2 rounded-lg bg-surface-container-lowest text-on-surface border border-outline-variant/50 text-[12px] font-bold flex items-center gap-2 hover:bg-surface-container-high transition-all">
          <span class="material-symbols-outlined text-lg">webhook</span>
          Hooks
          <span v-if="hooks.length > 0" class="px-1.5 py-0.5 rounded-full bg-primary/10 text-primary text-[10px] font-bold">{{ hooks.length }}</span>
        </button>
        <button @click="scanDrift" :disabled="driftScanning" class="px-4 py-2 rounded-lg bg-surface-container-lowest text-on-surface border border-outline-variant/50 text-[12px] font-bold flex items-center gap-2 hover:bg-surface-container-high transition-all disabled:opacity-50">
          <span class="material-symbols-outlined text-lg" :class="driftScanning ? 'animate-spin' : ''">radar</span>
          {{ driftScanning ? '检测中...' : '状态检测' }}
        </button>
        <button @click="openRemoteConnect" class="px-4 py-2 rounded-lg bg-surface-container-lowest text-on-surface border border-outline-variant/50 text-[12px] font-bold flex items-center gap-2 hover:bg-surface-container-high transition-all">
          <span class="material-symbols-outlined text-lg">terminal</span>
          远程连接
        </button>
        <button @click="toggleAutoSnapshot" class="relative px-4 py-2 rounded-lg text-[12px] font-bold flex items-center gap-2 transition-all"
          :class="server.autoSnapshotEnabled ? 'bg-green-500/10 text-green-600 border border-green-600/20' : 'bg-surface-container-lowest text-on-surface border border-outline-variant/50'">
          <span class="material-symbols-outlined text-lg" :style="server.autoSnapshotEnabled ? 'font-variation-settings: FILL 1' : ''">auto_awesome</span>
          自动快照 {{ server.autoSnapshotEnabled ? '已开启' : '已关闭' }}
        </button>
        <button @click="showStashPanel = !showStashPanel" class="relative px-4 py-2 rounded-lg bg-tertiary-container text-on-tertiary-container text-[12px] font-bold flex items-center gap-2 hover:bg-tertiary-container/80 transition-all">
          <span class="material-symbols-outlined text-lg">archive</span>
          快速暂存
          <span v-if="stashes.length > 0" class="absolute -top-1.5 -right-1.5 w-4 h-4 rounded-full bg-error text-white text-[9px] font-bold flex items-center justify-center">{{ stashes.length }}</span>
        </button>
        <button @click="openNewSnapshot" class="px-4 py-2 rounded-lg bg-primary text-white text-[12px] font-bold flex items-center gap-2 hover:opacity-90 shadow-lg shadow-primary/20 transition-all">
          <span class="material-symbols-outlined text-lg">cached</span>
          立即快照
        </button>
      </div>
    </section>

    <!-- Stash Panel (toggled) -->
    <div v-if="showStashPanel" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-tertiary text-[24px]" style="font-variation-settings: 'FILL' 1;">archive</span>
          <div>
            <h3 class="text-[20px] font-semibold">快速暂存</h3>
            <p class="text-[12px] text-on-surface-variant">暂存当前状态，7天后自动过期</p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <button @click="createStash" :disabled="stashing" class="px-4 py-2 bg-tertiary text-white rounded-lg text-[12px] font-bold hover:bg-tertiary/90 transition-all disabled:opacity-50 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">{{ stashing ? 'hourglass_empty' : 'add_circle' }}</span>
            {{ stashing ? '暂存中...' : '创建暂存' }}
          </button>
          <button v-if="stashes.length > 0" @click="popStash" class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary/90 transition-all flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">unarchive</span>
            恢复最新暂存
          </button>
        </div>
      </div>
      <div class="p-6">
        <div v-if="stashes.length === 0" class="text-center py-8">
          <span class="material-symbols-outlined text-[48px] text-outline/40">archive</span>
          <p class="text-[14px] text-outline mt-2">暂无暂存快照</p>
        </div>
        <div v-else class="space-y-3">
          <div v-for="stash in stashes" :key="stash.id"
            class="flex items-center justify-between p-3 rounded-xl bg-surface-container/50 border border-outline-variant/20 hover:border-tertiary/30 transition-all">
            <div class="flex items-center gap-3">
              <span class="material-symbols-outlined text-tertiary text-[20px]">inventory_2</span>
              <div>
                <p class="text-[14px] font-bold">{{ stash.name || stash.title || 'Stash' }}</p>
                <p class="text-[11px] text-outline font-[Geist]">{{ stash.createdAt ? new Date(stash.createdAt).toLocaleString('zh-CN') : '' }}</p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <span v-if="stash.hash" class="text-[10px] text-outline font-[Geist]">Hash: {{ stash.hash.substring(0, 8) }}...</span>
              <button @click="discardStash(stash.id)" class="p-1.5 rounded-lg hover:bg-error/10 text-outline hover:text-error transition-colors" title="丢弃暂存">
                <span class="material-symbols-outlined text-[16px]">delete</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Blame Panel (toggled) -->
    <div v-if="showBlamePanel" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">person_search</span>
          <div>
            <h3 class="text-[20px] font-semibold">变更历史 (Blame)</h3>
            <p class="text-[12px] text-on-surface-variant">谁在什么时候做了什么操作</p>
          </div>
        </div>
        <button @click="showBlamePanel = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>
      <div class="p-6">
        <div v-if="blameLoading" class="space-y-3">
          <div v-for="i in 5" :key="i" class="h-12 animate-pulse bg-surface-container-highest rounded-xl"></div>
        </div>
        <div v-else-if="blameEntries.length === 0" class="text-center py-8">
          <span class="material-symbols-outlined text-[48px] text-outline/40">person_search</span>
          <p class="text-[14px] text-outline mt-2">暂无变更记录</p>
        </div>
        <div v-else class="space-y-1">
          <div v-for="entry in blameEntries" :key="entry.id"
            class="flex items-start gap-3 p-3 rounded-xl hover:bg-surface-container/50 transition-colors">
            <div class="w-8 h-8 rounded-full bg-surface-container-highest flex items-center justify-center shrink-0 mt-0.5">
              <span class="material-symbols-outlined text-[16px]" :class="getChangeTypeColor(entry.changeType)">
                {{ getChangeTypeIcon(entry.changeType) }}
              </span>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-[13px]">
                <span class="font-bold">{{ entry.userName }}</span>
                <span class="text-on-surface-variant"> {{ entry.action }}</span>
              </p>
              <p v-if="entry.details" class="text-[11px] text-outline mt-0.5 truncate">{{ entry.details }}</p>
            </div>
            <span class="text-[10px] text-outline whitespace-nowrap font-[Geist]">{{ formatBlameTime(entry.timestamp) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Hooks Panel (toggled) -->
    <div v-if="showHooksPanel" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">webhook</span>
          <div>
            <h3 class="text-[20px] font-semibold">预/后置钩子 (Hooks)</h3>
            <p class="text-[12px] text-on-surface-variant">在快照/恢复前后自动执行自定义命令</p>
          </div>
        </div>
        <button @click="showHooksPanel = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
          <span class="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>
      <div class="p-6 space-y-5">
        <!-- Hook Form -->
        <div class="bg-surface-container/50 rounded-xl p-4 border border-outline-variant/20 space-y-3">
          <p class="text-[12px] font-bold text-on-surface-variant">{{ editingHook ? '编辑 Hook' : '创建新 Hook' }}</p>
          <div class="grid grid-cols-2 gap-3">
            <input v-model="hookForm.name" class="px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="Hook 名称" />
            <select v-model="hookForm.hookType" class="px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none appearance-none">
              <option value="PRE_SNAPSHOT">快照前 (PRE_SNAPSHOT)</option>
              <option value="POST_SNAPSHOT">快照后 (POST_SNAPSHOT)</option>
              <option value="PRE_RESTORE">恢复前 (PRE_RESTORE)</option>
              <option value="POST_RESTORE">恢复后 (POST_RESTORE)</option>
            </select>
          </div>
          <textarea v-model="hookForm.command" rows="2"
            class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] font-[Geist] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
            placeholder="要执行的命令，例如: docker exec mysql mysql -e 'FLUSH TABLES WITH READ LOCK;'"></textarea>
          <div class="flex items-center gap-3">
            <label class="flex items-center gap-2 text-[12px] text-on-surface-variant cursor-pointer">
              <input type="checkbox" v-model="hookForm.enabled" class="rounded border-outline-variant" />
              启用
            </label>
            <input v-model.number="hookForm.orderIndex" type="number" min="0"
              class="w-20 px-3 py-1.5 bg-white/50 border border-outline-variant rounded-lg text-[12px] text-center focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
              placeholder="顺序" />
            <input v-model.number="hookForm.timeoutSeconds" type="number" min="1" max="300"
              class="w-24 px-3 py-1.5 bg-white/50 border border-outline-variant rounded-lg text-[12px] text-center focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
              placeholder="超时秒数" />
            <div class="flex-1"></div>
            <button v-if="editingHook" @click="cancelHookEdit" class="px-3 py-1.5 text-[11px] font-bold text-outline hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
            <button @click="saveHook" :disabled="!hookForm.name || !hookForm.command"
              class="px-4 py-1.5 bg-primary text-white rounded-lg text-[11px] font-bold hover:bg-primary/90 transition-all disabled:opacity-50">
              {{ editingHook ? '更新' : '创建' }}
            </button>
          </div>
        </div>

        <!-- Hooks List -->
        <div v-if="hooks.length === 0" class="text-center py-6">
          <span class="material-symbols-outlined text-[36px] text-outline/40">webhook</span>
          <p class="text-[13px] text-outline mt-2">暂无自定义钩子</p>
        </div>
        <div v-else class="space-y-2">
          <div v-for="hook in hooks" :key="hook.id"
            class="flex items-center gap-3 p-3 rounded-xl border border-outline-variant/20 transition-all"
            :class="hook.enabled ? 'bg-surface-container/30' : 'bg-surface-container-highest/30 opacity-60'">
            <span class="material-symbols-outlined text-[18px]"
              :class="hook.hookType.includes('PRE') ? 'text-primary' : 'text-tertiary'">webhook</span>
            <div class="flex-1 min-w-0">
              <p class="text-[13px] font-bold">{{ hook.name }}</p>
              <p class="text-[11px] text-outline font-[Geist] truncate">{{ hook.command }}</p>
            </div>
            <span class="px-2 py-0.5 rounded-full text-[9px] font-bold"
              :class="hook.hookType.includes('PRE') ? 'bg-primary/10 text-primary' : 'bg-tertiary/10 text-tertiary'">
              {{ hook.hookType.replace('_', ' ') }}
            </span>
            <button @click="editHook(hook)" class="p-1.5 rounded-lg hover:bg-surface-container-high text-outline transition-colors" title="编辑">
              <span class="material-symbols-outlined text-[14px]">edit</span>
            </button>
            <button @click="deleteHook(hook.id)" class="p-1.5 rounded-lg hover:bg-error/10 text-outline hover:text-error transition-colors" title="删除">
              <span class="material-symbols-outlined text-[14px]">delete</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Drift Detection Report -->
    <div v-if="showDriftSection" class="glass-panel rounded-2xl overflow-hidden border border-outline-variant/30">
      <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[24px]" style="font-variation-settings: 'FILL' 1;">radar</span>
          <div>
            <h3 class="text-[20px] font-semibold">状态检测报告</h3>
            <p class="text-[12px] text-on-surface-variant" v-if="driftReport">
              {{ driftReport.totalChanges === 0 ? '无变更检测到' : '检测到 ' + driftReport.totalChanges + ' 项变更' }}
              <span class="text-outline"> · {{ driftReport.scannedAt }}</span>
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <span v-if="driftReport" class="px-2 py-0.5 rounded-full text-[10px] font-bold"
            :class="driftReport.status === 'CLEAN' ? 'bg-green-500/10 text-green-600' : driftReport.status === 'MINOR' ? 'bg-amber-500/10 text-amber-600' : 'bg-error/10 text-error'">
            {{ driftReport.status === 'CLEAN' ? '正常' : driftReport.status === 'MINOR' ? '轻微变更' : '显著变更' }}
          </span>
          <button @click="showDriftSection = false" class="p-2 hover:bg-surface-container-high rounded-lg transition-colors">
            <span class="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>
      </div>
      <div class="p-6">
        <div v-if="driftScanning" class="space-y-3">
          <div v-for="i in 3" :key="i" class="h-12 animate-pulse bg-surface-container-highest rounded-xl"></div>
        </div>
        <div v-else-if="!driftReport" class="text-center py-8">
          <span class="material-symbols-outlined text-[48px] text-outline/40">radar</span>
          <p class="text-[14px] text-outline mt-2">点击"状态检测"开始扫描</p>
        </div>
        <div v-else class="space-y-4">
          <!-- Container Drifts -->
          <div v-if="driftReport.containerDrifts.length > 0" class="space-y-2">
            <p class="text-[12px] font-bold text-on-surface-variant flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[16px]">deployed_code</span>
              容器变更 ({{ driftReport.containerDrifts.length }})
            </p>
            <div v-for="cd in driftReport.containerDrifts" :key="cd.containerName"
              class="p-3 rounded-lg bg-amber-50 border border-amber-200 flex items-center gap-3">
              <span class="material-symbols-outlined text-[16px] text-amber-600">warning</span>
              <div class="flex-1">
                <p class="text-[13px] font-bold">{{ cd.containerName }}</p>
                <p class="text-[11px] text-amber-700">{{ cd.details }}</p>
              </div>
              <span class="text-[10px] text-amber-600 font-bold">{{ cd.driftType }}</span>
            </div>
          </div>

          <!-- File Drifts -->
          <div v-if="driftReport.fileDrifts.length > 0" class="space-y-2">
            <p class="text-[12px] font-bold text-on-surface-variant flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[16px]">description</span>
              配置文件检查 ({{ driftReport.fileDrifts.length }})
            </p>
            <div v-for="fd in driftReport.fileDrifts" :key="fd.filePath"
              class="p-3 rounded-lg bg-surface-container/50 border border-outline-variant/20 flex items-center gap-3">
              <span class="material-symbols-outlined text-[16px] text-primary">check_circle</span>
              <div class="flex-1">
                <p class="text-[13px] font-bold font-[Geist]">{{ fd.filePath }}</p>
                <p class="text-[11px] text-outline font-[Geist]">MD5: {{ fd.currentHash }}</p>
              </div>
            </div>
          </div>

          <!-- Port Drifts -->
          <div v-if="driftReport.portDrifts.length > 0" class="space-y-2">
            <p class="text-[12px] font-bold text-on-surface-variant flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[16px]">lan</span>
              端口监听 ({{ driftReport.portDrifts.length }})
            </p>
            <div class="grid grid-cols-3 gap-2">
              <div v-for="pd in driftReport.portDrifts.slice(0, 12)" :key="pd.port"
                class="p-2 rounded-lg bg-surface-container/50 border border-outline-variant/20 text-center">
                <p class="text-[14px] font-bold font-[Geist]">{{ pd.port }}</p>
                <p class="text-[10px] text-outline">{{ pd.protocol }}</p>
              </div>
            </div>
          </div>

          <div v-if="driftReport.totalChanges === 0" class="text-center py-6">
            <span class="material-symbols-outlined text-[48px] text-green-500" style="font-variation-settings: 'FILL' 1;">verified</span>
            <p class="text-[14px] text-green-600 mt-2 font-bold">服务器状态正常，未检测到漂移</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Bento Grid for Core Metrics & Topology -->
    <div class="grid grid-cols-12 gap-[16px]">
      <!-- Docker Containers Status -->
      <div class="col-span-12 lg:col-span-8 glass-panel rounded-xl p-[20px]">
        <div class="flex justify-between items-center mb-6">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">token</span>
            容器实例 (Docker)
          </h3>
          <span class="text-[14px] text-outline">{{ containers.length }} 个活动中</span>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 max-h-[400px] overflow-y-auto pr-1">
          <div v-for="container in containers" :key="container.name" class="p-4 rounded-lg bg-surface-container-low border border-outline-variant/30 hover:border-primary/30 transition-all group">
            <div class="flex justify-between items-start mb-4">
              <div>
                <p class="text-[12px] font-bold text-outline">{{ container.type }}</p>
                <h4 class="text-lg font-bold">{{ container.name }}</h4>
              </div>
              <span class="w-3 h-3 rounded-full bg-green-500 shadow-[0_0_10px_rgba(16,185,129,0.4)]"></span>
            </div>
            <div class="space-y-3">
              <div class="space-y-1">
                <div class="flex justify-between text-[11px] font-bold uppercase tracking-tighter">
                  <span>{{ container.metric1.label }}</span>
                  <span class="text-primary">{{ container.metric1.value }}</span>
                </div>
                <div class="h-1.5 w-full bg-surface-container-highest rounded-full overflow-hidden">
                  <div class="h-full bg-primary rounded-full" :style="{ width: container.metric1.percent }"></div>
                </div>
              </div>
              <div class="space-y-1">
                <div class="flex justify-between text-[11px] font-bold uppercase tracking-tighter">
                  <span>{{ container.metric2.label }}</span>
                  <span class="text-secondary">{{ container.metric2.value }}</span>
                </div>
                <div class="h-1.5 w-full bg-surface-container-highest rounded-full overflow-hidden">
                  <div class="h-full bg-secondary rounded-full" :style="{ width: container.metric2.percent }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: AI Insights + Volumes (row-span-2 to fill both rows) -->
      <div class="col-span-12 lg:col-span-4 lg:row-span-2 flex flex-col gap-[16px]">
      <!-- AI Insights Mini Card -->
      <div class="rounded-xl p-[20px] bg-gradient-to-br from-primary to-primary-container text-white shadow-xl shadow-primary/20 relative overflow-hidden group flex-1">
        <div class="absolute -right-10 -bottom-10 w-40 h-40 bg-white/10 rounded-full blur-3xl group-hover:scale-150 transition-transform duration-700"></div>
        <div class="relative z-10 flex flex-col h-full">
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-2xl" style="font-variation-settings: 'FILL' 1;">psychology</span>
              <h3 class="text-[20px] font-semibold">AI 智能观察</h3>
            </div>
            <div v-if="aiAnalysis" class="flex items-center gap-1.5">
              <div class="w-10 h-10 rounded-full border-2 border-white/40 flex items-center justify-center" :class="aiAnalysis.healthScore >= 80 ? 'border-green-300' : aiAnalysis.healthScore >= 60 ? 'border-amber-300' : 'border-red-300'">
                <span class="text-[14px] font-bold">{{ aiAnalysis.healthScore }}</span>
              </div>
            </div>
          </div>

          <div v-if="aiLoading" class="flex-1 flex items-center justify-center">
            <div class="flex items-center gap-2 text-white/70">
              <span class="material-symbols-outlined animate-spin text-lg">sync</span>
              <span class="text-[13px]">AI 分析中...</span>
            </div>
          </div>

          <div v-else-if="aiAnalysis" class="flex-1 space-y-3">
            <p class="text-[13px] text-white/90 leading-relaxed">{{ aiAnalysis.summary }}</p>

            <div v-if="aiAnalysis.findings?.length" class="space-y-1.5">
              <p class="text-[10px] font-bold uppercase tracking-widest text-white/50">发现</p>
              <div v-for="f in aiAnalysis.findings.slice(0, 3)" :key="f" class="flex items-start gap-1.5">
                <span class="material-symbols-outlined text-[14px] mt-0.5 text-amber-300">warning</span>
                <span class="text-[12px] text-white/80">{{ f }}</span>
              </div>
            </div>

            <div v-if="aiAnalysis.recommendations?.length" class="space-y-1.5">
              <p class="text-[10px] font-bold uppercase tracking-widest text-white/50">建议</p>
              <div v-for="r in aiAnalysis.recommendations.slice(0, 2)" :key="r" class="flex items-start gap-1.5">
                <span class="material-symbols-outlined text-[14px] mt-0.5 text-green-300">lightbulb</span>
                <span class="text-[12px] text-white/80">{{ r }}</span>
              </div>
            </div>
          </div>

          <div v-else class="flex-1 flex items-center justify-center">
            <div class="text-center">
              <p class="text-[13px] text-white/60 mb-2">{{ aiError || '暂无分析数据' }}</p>
              <button v-if="aiError" @click="retryAiAnalysis" class="px-3 py-1 bg-white/20 hover:bg-white/30 rounded text-[11px] text-white/80">重试</button>
            </div>
          </div>

          <button @click="$router.push('/ai-insights')" class="mt-4 px-4 py-2 bg-white/20 hover:bg-white/30 backdrop-blur-md rounded-lg text-[12px] font-bold transition-all w-fit">
            查看完整分析
          </button>
        </div>
      </div>

        <!-- Volume & Config Area -->
        <div class="glass-panel rounded-xl p-[20px] overflow-hidden flex flex-col flex-1">
          <h3 class="text-[24px] font-semibold flex items-center gap-2 mb-6">
            <span class="material-symbols-outlined text-primary">folder_managed</span>
            挂载卷与配置
          </h3>
          <div class="space-y-4 flex-1 overflow-y-auto">
            <div v-for="vol in volumes" :key="vol.name" class="flex items-center gap-3 p-3 rounded-lg hover:bg-surface-container-high/40 transition-colors">
              <div class="w-10 h-10 rounded-lg bg-surface-container-highest flex items-center justify-center" :class="vol.iconColor">
                <span class="material-symbols-outlined">{{ vol.icon }}</span>
              </div>
              <div class="flex-1">
                <h5 class="text-[14px] font-bold">{{ vol.name }}</h5>
                <p class="text-[11px] text-outline">{{ vol.path }}</p>
              </div>
              <div class="text-right">
                <p class="text-[14px] font-bold">{{ vol.size }}</p>
                <p class="text-[10px] font-bold" :class="vol.statusColor">{{ vol.status }}</p>
              </div>
            </div>
          </div>
          <button @click="openAddMount" class="w-full mt-4 py-2.5 rounded-lg border border-dashed border-outline-variant hover:border-primary/50 text-[14px] text-outline hover:text-primary transition-all flex items-center justify-center gap-2">
            <span class="material-symbols-outlined text-sm">add</span>
            添加挂载路径
          </button>
        </div>
      </div><!-- end right-col wrapper -->

      <!-- Topology Graph (Draggable Canvas) -->
      <div class="col-span-12 lg:col-span-8 glass-panel rounded-xl p-[20px] min-h-[400px] flex flex-col">
        <div class="flex justify-between items-center mb-4">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">hub</span>
            服务拓扑图
          </h3>
          <div class="flex gap-2">
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-secondary"></span> 运行中</span>
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-error"></span> 已停止</span>
            <span class="flex items-center gap-1 text-[10px] text-outline"><span class="w-2 h-2 rounded-full bg-primary"></span> 网络连接</span>
          </div>
        </div>
        <div ref="topologyCanvas" class="flex-1 bg-surface-container-lowest/50 rounded-lg border border-outline-variant/20 relative overflow-hidden cursor-grab"
          @mousedown="onCanvasMouseDown" @mousemove="onCanvasMouseMove" @mouseup="onCanvasMouseUp" @mouseleave="onCanvasMouseUp">
          <svg v-if="containers.length" class="absolute inset-0 w-full h-full" style="z-index: 1;">
            <defs>
              <marker id="arrowhead" markerWidth="6" markerHeight="4" refX="6" refY="2" orient="auto">
                <polygon points="0 0, 6 2, 0 4" fill="var(--color-primary)" opacity="0.5" />
              </marker>
              <marker id="arrowhead-weak" markerWidth="5" markerHeight="3" refX="5" refY="1.5" orient="auto">
                <polygon points="0 0, 5 1.5, 0 3" fill="var(--color-outline)" opacity="0.25" />
              </marker>
            </defs>
            <!-- Server-to-container edges (lighter, no label) -->
            <line v-for="(edge, ei) in topologyEdges.filter(e => !e.label)" :key="'se-'+ei"
              :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
              stroke="var(--color-outline)" stroke-width="1" stroke-dasharray="4 4" opacity="0.2"
              marker-end="url(#arrowhead-weak)" />
            <!-- Container-to-container network edges (prominent, with label) -->
            <line v-for="(edge, ei) in topologyEdges.filter(e => e.label)" :key="'ne-'+ei"
              :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
              stroke="var(--color-primary)" stroke-width="1.5" stroke-dasharray="6 3" opacity="0.45"
              marker-end="url(#arrowhead)" class="topology-line" />
            <text v-for="(edge, ei) in topologyEdges.filter(e => e.label)" :key="'nl-'+ei"
              :x="(edge.x1 + edge.x2) / 2" :y="(edge.y1 + edge.y2) / 2 - 6"
              text-anchor="middle" fill="var(--color-primary)" font-size="9" font-weight="bold" opacity="0.7">
              {{ edge.label }}
            </text>
          </svg>
          <div v-if="topologyNodes.length" class="absolute" style="z-index: 2;">
            <div v-for="(node, i) in topologyNodes" :key="node.name"
              class="absolute glass-panel rounded-xl flex flex-col items-center justify-center border-2 cursor-move select-none transition-all hover:shadow-lg hover:scale-105"
              :class="[
                node.isServer ? 'w-28 h-28 p-2 shadow-md' : 'w-24 h-24 p-1.5',
                node.status === 'RUNNING' || node.status === 'running' ? (node.isServer ? 'border-primary bg-primary/5' : 'border-secondary') : 'border-error'
              ]"
              :style="{ left: node.x + 'px', top: node.y + 'px', transform: 'translate(-50%, -50%)' }"
              @mousedown.stop="startDragNode(i, $event)">
              <span class="material-symbols-outlined mb-0.5"
                :class="[
                  node.isServer ? 'text-2xl' : 'text-xl',
                  node.status === 'RUNNING' || node.status === 'running' ? (node.isServer ? 'text-primary' : 'text-secondary') : 'text-error'
                ]">
                {{ node.icon }}
              </span>
              <span class="text-[9px] font-bold text-center truncate w-full leading-tight">{{ node.name }}</span>
              <span v-if="node.isServer" class="text-[8px] text-primary font-medium truncate w-full text-center">{{ node.networks }}</span>
              <span v-else class="text-[8px] text-outline truncate w-full text-center">{{ node.networks || '' }}</span>
            </div>
          </div>
          <div v-else class="flex items-center justify-center h-full">
            <div class="text-center">
              <span class="material-symbols-outlined text-outline text-[48px] mb-2">hub</span>
              <p class="text-on-surface-variant text-[14px]">暂无容器数据</p>
            </div>
          </div>
        </div>
      </div>

      <!-- SSH Configuration -->
      <div class="col-span-12 glass-panel rounded-xl p-[20px]">
        <div class="flex justify-between items-center mb-4">
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-primary">vpn_key</span>
            SSH 连接配置
          </h3>
          <div class="flex gap-2">
            <button @click="testSshConnection" :disabled="sshTesting" class="px-4 py-2 rounded-lg border border-outline-variant/50 text-[12px] font-bold flex items-center gap-2 hover:bg-surface-container-high transition-all disabled:opacity-50">
              <span class="material-symbols-outlined text-lg" :class="sshTesting ? 'animate-spin' : ''">{{ sshTesting ? 'sync' : 'cell_tower' }}</span>
              {{ sshTesting ? '测试中...' : '测试连接' }}
            </button>
            <button @click="saveSshConfig" :disabled="sshSaving" class="px-4 py-2 rounded-lg bg-primary text-white text-[12px] font-bold flex items-center gap-2 hover:opacity-90 shadow-lg shadow-primary/20 transition-all disabled:opacity-50">
              {{ sshSaving ? '保存中...' : '保存配置' }}
            </button>
          </div>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">SSH 端口</label>
            <input v-model.number="sshConfig.port" type="number" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="22" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">用户名</label>
            <input v-model="sshConfig.username" type="text" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="root" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">认证方式</label>
            <select v-model="sshConfig.authMethod" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]">
              <option value="PASSWORD">密码</option>
              <option value="KEY">密钥</option>
            </select>
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant tracking-wide">{{ sshConfig.authMethod === 'KEY' ? 'SSH 私钥' : '密码' }}</label>
            <textarea v-if="sshConfig.authMethod === 'KEY'" v-model="sshConfig.credential" rows="6" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface font-mono text-[12px] focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all resize-y" placeholder="粘贴完整的 SSH 私钥内容（含 BEGIN/END 行）"></textarea>
            <input v-else v-model="sshConfig.credential" type="password" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-[14px]" placeholder="输入密码" />
          </div>
        </div>
        <div v-if="sshTestResult" class="mt-3 p-3 rounded-lg text-[13px]" :class="sshTestResult.success ? 'bg-green-500/10 text-green-600 border border-green-600/20' : 'bg-error/10 text-error border border-error/20'">
          {{ sshTestResult.message }}
        </div>
      </div>

      <!-- Terminal Area (Resizable) -->
      <div class="col-span-12 glass-panel rounded-xl overflow-hidden flex flex-col" :style="{ height: terminalHeight + 'px' }">
        <div class="bg-surface-dim px-4 py-2 flex justify-between items-center border-b border-outline-variant/30 cursor-default">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-on-surface-variant text-lg">terminal</span>
            <span class="text-[12px] font-bold text-on-surface-variant">日志终端 (Recent Logs)</span>
          </div>
          <div class="flex gap-4">
            <button @click="exportLogs" class="text-on-surface-variant hover:text-primary transition-colors">
              <span class="material-symbols-outlined text-lg">download</span>
            </button>
            <button @click="openClearLogs" class="text-on-surface-variant hover:text-primary transition-colors">
              <span class="material-symbols-outlined text-lg">delete</span>
            </button>
            <button @click="toggleFullscreen" class="text-on-surface-variant hover:text-primary transition-colors">
              <span class="material-symbols-outlined text-lg">open_in_full</span>
            </button>
          </div>
        </div>
        <div class="flex-1 bg-[#191b23] p-4 font-[Geist] text-[14px] overflow-y-auto">
          <div class="space-y-1">
            <p v-for="log in logs" :key="log.text" :class="log.color" class="opacity-80">
              {{ log.text }}
            </p>
          </div>
        </div>
        <div class="h-1.5 bg-surface-dim hover:bg-primary/30 cursor-row-resize flex items-center justify-center transition-colors"
          @mousedown="startResizeTerminal">
          <div class="w-8 h-0.5 bg-outline-variant/50 rounded-full"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { serversApi } from '@/api/servers'
import { aiApi } from '@/api/ai'
import { branchesApi } from '@/api/branches'
import { stashApi } from '@/api/stash'
import { blameApi } from '@/api/blame'
import { hooksApi } from '@/api/hooks'
import { driftApi } from '@/api/drift'
import { formatBytes } from '@/utils/format'
import TerminalModal from '@/components/modals/TerminalModal.vue'
import AddMountPathModal from '@/components/modals/AddMountPathModal.vue'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import NewBackupModal from '@/components/modals/NewBackupModal.vue'

const route = useRoute()
const toast = useToastStore()
const modal = useModalStore()
const serverId = Number(route.params.id)

import type { Server, Container, Volume, LogEntry, AiServerAnalysis, ServerBranch, Snapshot, ChangeAttribution, SnapshotHook, DriftReport } from '@/types'

/** Extract a safe error message from an unknown catch value */
function getErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object' && 'message' in e) return (e as { message: string }).message
  if (typeof e === 'string') return e
  return fallback
}

function getApiErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object') {
    const err = e as Record<string, unknown>
    const resp = err.response as Record<string, unknown> | undefined
    const data = resp?.data as Record<string, unknown> | undefined
    if (typeof data?.message === 'string') return data.message
    if (typeof err.message === 'string') return err.message
  }
  return fallback
}

const server = ref<Server>({} as Server)
const containers = ref<(Container & { metric1: { label: string; value: string; percent: string }; metric2: { label: string; value: string; percent: string } })[]>([])
const volumes = ref<(Volume & { size: string; icon: string; iconColor: string; status: string; statusColor: string })[]>([])
const logs = ref<{ text: string; color: string }[]>([])
const aiAnalysis = ref<AiServerAnalysis | null>(null)
const aiLoading = ref(false)
const aiError = ref('')
const sshConfig = ref({ port: 22, username: 'root', authMethod: 'PASSWORD', credential: '' })
const sshTesting = ref(false)
const sshSaving = ref(false)
const sshTestResult = ref<{ success: boolean; message: string } | null>(null)

// Branch state
const branches = ref<ServerBranch[]>([])
const activeBranch = ref<ServerBranch | null>(null)
const showBranchDropdown = ref(false)
const newBranchName = ref('')
const creatingBranch = ref(false)

// Stash state
const stashes = ref<Snapshot[]>([])
const showStashPanel = ref(false)
const stashing = ref(false)

// Blame state
const blameEntries = ref<ChangeAttribution[]>([])
const showBlamePanel = ref(false)
const blameLoading = ref(false)

// Hooks state
const hooks = ref<SnapshotHook[]>([])
const showHooksPanel = ref(false)
const editingHook = ref<Partial<SnapshotHook> | null>(null)
const hookForm = ref({ name: '', hookType: 'PRE_SNAPSHOT' as SnapshotHook['hookType'], command: '', timeoutSeconds: 60, enabled: true, orderIndex: 0 })

// Drift detection state
const driftReport = ref<DriftReport | null>(null)
const driftScanning = ref(false)
const showDriftSection = ref(false)

async function scanDrift() {
  if (!server.value?.id) return
  driftScanning.value = true
  driftReport.value = null
  showDriftSection.value = true
  try {
    const res = await driftApi.detect(server.value.id)
    driftReport.value = res
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '漂移检测失败'))
  } finally {
    driftScanning.value = false
  }
}

// Topology
const topologyCanvas = ref<HTMLElement | null>(null)
const topologyNodes = ref<Array<{ name: string; x: number; y: number; status: string; icon: string; networks: string; type: string; isServer?: boolean }>>([])
const topologyEdges = ref<Array<{ x1: number; y1: number; x2: number; y2: number; label: string }>>([])
const topologyRawEdges = ref<{ source: string; target: string }[]>([])
let dragNodeIndex = -1
let dragOffsetX = 0
let dragOffsetY = 0

const topologyTypeConfig: Record<string, { icon: string; color: string; label: string }> = {
  'HTTP Server': { icon: 'language', color: 'text-blue-500', label: 'Web 服务' },
  'Web Server': { icon: 'language', color: 'text-blue-500', label: 'Web 服务' },
  'Database': { icon: 'database', color: 'text-amber-500', label: '数据库' },
  'Cache': { icon: 'memory', color: 'text-green-500', label: '缓存' },
  'Queue': { icon: 'swap_horiz', color: 'text-purple-500', label: '消息队列' },
  'Other': { icon: 'token', color: 'text-outline', label: '其他' },
}

// Terminal resize
const terminalHeight = ref(300)
let resizingTerminal = false
let resizeStartY = 0
let resizeStartH = 0

// Server status computed
const serverStatusClass = computed(() => {
  const s = server.value.status
  if (s === 'RUNNING') return 'bg-green-500/10 text-green-600 border-green-600/20'
  if (s === 'STOPPED') return 'bg-red-500/10 text-red-600 border-red-600/20'
  return 'bg-amber-500/10 text-amber-600 border-amber-600/20'
})
const serverStatusDotClass = computed(() => {
  const s = server.value.status
  if (s === 'RUNNING') return 'bg-green-500'
  if (s === 'STOPPED') return 'bg-red-500'
  return 'bg-amber-500'
})
const serverStatusText = computed(() => {
  const s = server.value.status
  if (s === 'RUNNING') return '运行中'
  if (s === 'STOPPED') return '已停止'
  return s || '检测中'
})

function openRemoteConnect() {
  modal.open({
    component: TerminalModal,
    title: `SSH 终端 — ${server.value.name || 'Server'}`,
    width: 'max-w-4xl',
    props: { serverId, serverName: server.value.name }
  })
}

function openAddMount() {
  modal.open({ component: AddMountPathModal, title: '添加挂载路径' })
}

function openClearLogs() {
  modal.open({
    component: ConfirmModal,
    title: '清空日志',
    props: {
      message: '确定要清空当前服务器的所有日志记录吗？清空后日志将无法恢复。建议先导出备份。',
      confirmText: '确认清空',
      confirmClass: 'bg-error hover:bg-error/90',
      successMessage: '日志已清空',
      onConfirm: async () => {
        await serversApi.clearLogs(serverId)
        logs.value = []
      },
    },
  })
}

function openNewSnapshot() {
  modal.open({ component: NewBackupModal, title: '发起新快照任务' })
}

async function testSshConnection() {
  sshTesting.value = true
  sshTestResult.value = null
  try {
    const res = await serversApi.testConnection(serverId)
    sshTestResult.value = res
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    sshTestResult.value = { success: false, message: err?.response?.data?.message || '测试失败' }
  } finally {
    sshTesting.value = false
  }
}

async function saveSshConfig() {
  sshSaving.value = true
  try {
    await serversApi.updateSshConfig(serverId, {
      port: sshConfig.value.port,
      username: sshConfig.value.username,
      authMethod: sshConfig.value.authMethod,
      credential: sshConfig.value.credential,
    })
    toast.success('SSH 配置已保存')
    sshConfig.value.credential = '' // Clear credential after save
  } catch (e: unknown) {
    toast.error(getApiErrorMessage(e, '保存失败'))
  } finally {
    sshSaving.value = false
  }
}

function exportLogs() {
  if (!logs.value.length) {
    toast.error('暂无日志数据')
    return
  }
  const text = logs.value.map((l) => l.text).join('\n')
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `logs-${server.value.name || 'server'}-${new Date().toISOString().slice(0, 10)}.txt`
  a.click()
  URL.revokeObjectURL(url)
  toast.success('日志已导出')
}

function toggleFullscreen() {
  const el = document.querySelector('.bg-\\[\\#191b23\\]')
  if (el) {
    if (!document.fullscreenElement) {
      el.requestFullscreen?.()
    } else {
      document.exitFullscreen?.()
    }
  }
}

function startResizeTerminal(e: MouseEvent) {
  resizingTerminal = true
  resizeStartY = e.clientY
  resizeStartH = terminalHeight.value
  document.addEventListener('mousemove', onResizeTerminal)
  document.addEventListener('mouseup', stopResizeTerminal)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
}

function onResizeTerminal(e: MouseEvent) {
  if (!resizingTerminal) return
  const delta = resizeStartY - e.clientY
  terminalHeight.value = Math.max(150, Math.min(800, resizeStartH + delta))
}

function stopResizeTerminal() {
  resizingTerminal = false
  document.removeEventListener('mousemove', onResizeTerminal)
  document.removeEventListener('mouseup', stopResizeTerminal)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function retryAiAnalysis() {
  aiError.value = ''
  aiLoading.value = true
  aiApi.analyzeServer(serverId).then(data => {
    if (data && data.healthScore !== undefined) {
      aiAnalysis.value = data
      aiError.value = ''
    } else {
      aiError.value = '分析结果格式异常'
    }
  }).catch((e: Error) => {
    aiError.value = '分析失败: ' + (e?.message || '网络错误')
  }).finally(() => { aiLoading.value = false })
}

function formatUptime(seconds: number): string {
  if (!seconds || seconds <= 0) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${mins} 分钟`
  return `${mins} 分钟`
}

function initTopologyNodes() {
  if (!topologyCanvas.value || !containers.value.length) return
  const rect = topologyCanvas.value.getBoundingClientRect()
  const w = rect.width
  const h = rect.height
  const cx = w / 2
  const cy = h / 2

  // Group containers by type
  const groups: Record<string, (typeof containers.value)[number][]> = {}
  containers.value.forEach((c) => {
    const t = c.type || 'Other'
    if (!groups[t]) groups[t] = []
    groups[t].push(c)
  })

  // Layout zones: server in center, groups arranged around it
  // Web types at top, DB at bottom, Cache/Queue on sides
  const typeOrder = ['HTTP Server', 'Web Server', 'Queue', 'Cache', 'Database', 'Other']
  const groupKeys = Object.keys(groups).sort((a, b) => {
    const ai = typeOrder.indexOf(a) === -1 ? 99 : typeOrder.indexOf(a)
    const bi = typeOrder.indexOf(b) === -1 ? 99 : typeOrder.indexOf(b)
    return ai - bi
  })

  const nodes: typeof topologyNodes.value = []

  // Server node in the center
  nodes.push({
    name: server.value.name || 'Server',
    x: cx,
    y: cy,
    status: server.value.status || 'RUNNING',
    icon: 'dns',
    networks: server.value.ip || '',
    type: 'Server',
    isServer: true,
  })

  // Position groups in arcs around the center
  const groupCount = groupKeys.length
  const minRadius = Math.min(w, h) * 0.28
  const maxRadius = Math.min(w, h) * 0.42

  groupKeys.forEach((type, gi) => {
    const group = groups[type]
    const cfg = topologyTypeConfig[type] || topologyTypeConfig['Other']

    // Each group gets an angular sector
    const sectorStart = (2 * Math.PI * gi) / groupCount - Math.PI / 2
    const sectorEnd = (2 * Math.PI * (gi + 1)) / groupCount - Math.PI / 2

    group.forEach((c, ci: number) => {
      // Spread containers within the sector
      const spread = group.length === 1 ? 0 : (sectorEnd - sectorStart) * 0.7
      const angle = sectorStart + (sectorEnd - sectorStart) * 0.15 + (spread * ci) / Math.max(group.length - 1, 1)
      const radius = minRadius + (maxRadius - minRadius) * (0.6 + 0.4 * Math.sin(ci * 1.2))

      nodes.push({
        name: c.name,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
        status: c.status || 'RUNNING',
        icon: cfg.icon,
        networks: c.networks || '',
        type: type,
      })
    })
  })

  topologyNodes.value = nodes
  recalcTopologyEdges()
}

function recalcTopologyEdges() {
  if (!topologyCanvas.value) return
  const nameToNode: Record<string, typeof topologyNodes.value[0]> = {}
  topologyNodes.value.forEach(n => { nameToNode[n.name] = n })

  const serverNode = topologyNodes.value.find(n => n.isServer)
  const edges: typeof topologyEdges.value = []

  // Edges from raw topology data (container-to-container network connections)
  topologyRawEdges.value.forEach(edge => {
    const a = nameToNode[edge.source]
    const b = nameToNode[edge.target]
    if (a && b) {
      edges.push({ x1: a.x, y1: a.y, x2: b.x, y2: b.y, label: '' })
    }
  })

  // Edges from server to each container (hosting relationship)
  if (serverNode) {
    topologyNodes.value.forEach(node => {
      if (node.isServer) return
      // Only add if not already connected via raw edges
      const alreadyConnected = edges.some(e =>
        (e.x1 === serverNode.x && e.y1 === serverNode.y && e.x2 === node.x && e.y2 === node.y) ||
        (e.x2 === serverNode.x && e.y2 === serverNode.y && e.x1 === node.x && e.y1 === node.y)
      )
      if (!alreadyConnected) {
        edges.push({ x1: serverNode.x, y1: serverNode.y, x2: node.x, y2: node.y, label: '' })
      }
    })
  }

  topologyEdges.value = edges
}

function startDragNode(index: number, e: MouseEvent) {
  dragNodeIndex = index
  const node = topologyNodes.value[index]
  dragOffsetX = e.clientX - node.x
  dragOffsetY = e.clientY - node.y
}

function onCanvasMouseDown(_e: MouseEvent) {}

function onCanvasMouseMove(e: MouseEvent) {
  if (dragNodeIndex < 0) return
  const node = topologyNodes.value[dragNodeIndex]
  node.x = e.clientX - dragOffsetX
  node.y = e.clientY - dragOffsetY
  recalcTopologyEdges()
}

function onCanvasMouseUp() {
  dragNodeIndex = -1
}

const containerDefaults = {
  'HTTP Server': { metric1Label: 'CPU 占用', metric2Label: '内存 占用' },
  Database: { metric1Label: 'CPU 占用', metric2Label: '磁盘 I/O' },
  Cache: { metric1Label: 'CPU 占用', metric2Label: '内存 占用' },
}

const volumeIcons: Record<string, { icon: string; iconColor: string }> = {
  database: { icon: 'database', iconColor: 'text-primary' },
  config: { icon: 'settings_ethernet', iconColor: 'text-secondary' },
  log: { icon: 'description', iconColor: 'text-tertiary' },
}

function mapContainers(cData: Container[]) {
  return cData.map((c: Container) => {
    const defaults = containerDefaults[c.type as keyof typeof containerDefaults] || containerDefaults['HTTP Server']
    return {
      ...c,
      metric1: { label: defaults.metric1Label, value: c.cpuUsage || '0%', percent: c.cpuUsage || '0%' },
      metric2: { label: defaults.metric2Label, value: c.memoryUsage || '0MB', percent: c.memoryPercent || '0%' },
    }
  })
}

async function refreshContainers() {
  try {
    const res = await serversApi.getContainers(serverId)
    const cData = res || []
    containers.value = mapContainers(cData)
  } catch (e) {
    console.warn('Failed to refresh containers', e)
  }
}

let refreshTimer: ReturnType<typeof setInterval> | null = null

// Branch functions
async function loadBranches() {
  try {
    const res = await branchesApi.getAll(serverId)
    branches.value = res || []
    activeBranch.value = branches.value.find(b => b.isDefault) || branches.value[0] || null
  } catch (e) {
    console.warn('Failed to load branches', e)
  }
}

async function switchBranch(branch: ServerBranch) {
  if (activeBranch.value?.id === branch.id) {
    showBranchDropdown.value = false
    return
  }
  try {
    await branchesApi.switch(serverId, branch.id)
    activeBranch.value = branch
    showBranchDropdown.value = false
    toast.success(`已切换到分支: ${branch.name}`)
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '切换分支失败'))
  }
}

async function createBranch() {
  const name = newBranchName.value.trim()
  if (!name) return
  creatingBranch.value = true
  try {
    const newBranch = await branchesApi.create(serverId, { name })
    branches.value.push(newBranch)
    newBranchName.value = ''
    toast.success(`分支 "${name}" 已创建`)
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '创建分支失败'))
  } finally {
    creatingBranch.value = false
  }
}

async function deleteBranch(branch: ServerBranch) {
  if (branch.isDefault) {
    toast.error('不能删除默认分支')
    return
  }
  try {
    await branchesApi.delete(serverId, branch.id)
    branches.value = branches.value.filter(b => b.id !== branch.id)
    if (activeBranch.value?.id === branch.id) {
      activeBranch.value = branches.value.find(b => b.isDefault) || branches.value[0] || null
    }
    toast.success(`分支 "${branch.name}" 已删除`)
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '删除分支失败'))
  }
}

// Branch rename
const renamingBranchId = ref<number | null>(null)
const renamingBranchName = ref('')

function renameBranchPrompt(branch: ServerBranch) {
  renamingBranchId.value = branch.id
  renamingBranchName.value = branch.name
  const newName = prompt('输入新分支名称:', branch.name)
  if (newName && newName.trim() && newName.trim() !== branch.name) {
    doRenameBranch(branch.id, newName.trim())
  }
  renamingBranchId.value = null
}

async function doRenameBranch(branchId: number, newName: string) {
  try {
    await branchesApi.rename(serverId, branchId, newName)
    const branch = branches.value.find(b => b.id === branchId)
    if (branch) branch.name = newName
    toast.success(`分支已重命名为 "${newName}"`)
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '重命名分支失败'))
  }
}

// Branch merge
const mergeSourceBranchId = ref<number | null>(null)
const mergingBranch = ref(false)

async function mergeBranches() {
  if (!mergeSourceBranchId.value || !activeBranch.value) return
  mergingBranch.value = true
  try {
    await branchesApi.merge(serverId, {
      sourceBranchId: mergeSourceBranchId.value,
      targetBranchId: activeBranch.value.id,
    })
    toast.success(`分支已合并到 "${activeBranch.value.name}"`)
    mergeSourceBranchId.value = null
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '合并分支失败'))
  } finally {
    mergingBranch.value = false
  }
}

// Cherry-pick
async function cherryPickSnapshot(snapshotId: number) {
  const targetServerId = prompt('输入目标服务器 ID (留空则应用到当前服务器):')
  const targetId = targetServerId ? Number(targetServerId) : serverId
  if (!targetId || isNaN(targetId)) return
  const filesInput = prompt('输入要提取的文件路径 (逗号分隔):')
  if (!filesInput) return
  const files = filesInput.split(',').map(f => f.trim()).filter(Boolean)
  if (files.length === 0) return

  try {
    const { snapshotsApi } = await import('@/api/snapshots')
    const result = await snapshotsApi.cherryPick(snapshotId, { files, targetServerId: targetId })
    toast.success(result || 'Cherry-pick 完成')
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, 'Cherry-pick 失败'))
  }
}

// Auto-snapshot toggle
async function toggleAutoSnapshot() {
  const newState = !server.value.autoSnapshotEnabled
  try {
    await serversApi.toggleAutoSnapshot(serverId, newState)
    server.value.autoSnapshotEnabled = newState
    toast.success(`自动快照已${newState ? '开启' : '关闭'}`)
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '操作失败'))
  }
}

// Hooks functions
async function loadHooks() {
  try {
    const res = await hooksApi.getAll(serverId)
    hooks.value = res || []
  } catch (e) {
    console.warn('Failed to load hooks', e)
  }
}

async function saveHook() {
  try {
    if (editingHook.value?.id) {
      await hooksApi.update(serverId, editingHook.value.id, hookForm.value)
      toast.success('Hook 已更新')
    } else {
      await hooksApi.create(serverId, hookForm.value)
      toast.success('Hook 已创建')
    }
    editingHook.value = null
    hookForm.value = { name: '', hookType: 'PRE_SNAPSHOT', command: '', timeoutSeconds: 60, enabled: true, orderIndex: 0 }
    await loadHooks()
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '保存失败'))
  }
}

function editHook(hook: SnapshotHook) {
  editingHook.value = hook
  hookForm.value = { ...hook }
}

async function deleteHook(hookId: number) {
  try {
    await hooksApi.delete(serverId, hookId)
    hooks.value = hooks.value.filter(h => h.id !== hookId)
    toast.success('Hook 已删除')
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '删除失败'))
  }
}

function cancelHookEdit() {
  editingHook.value = null
  hookForm.value = { name: '', hookType: 'PRE_SNAPSHOT', command: '', timeoutSeconds: 60, enabled: true, orderIndex: 0 }
}

// Blame functions
async function loadBlame() {
  blameLoading.value = true
  try {
    const res = await blameApi.getServerBlame(serverId)
    blameEntries.value = res || []
  } catch (e) {
    console.warn('Failed to load blame', e)
  } finally {
    blameLoading.value = false
  }
}

function getChangeTypeIcon(type?: string): string {
  if (!type) return 'edit'
  if (type.includes('CREATED')) return 'add_circle'
  if (type.includes('DELETED')) return 'delete'
  if (type.includes('RESTORED') || type.includes('REVERTED')) return 'settings_backup_restore'
  if (type.includes('BRANCH')) return 'call_split'
  if (type.includes('STASH')) return 'archive'
  return 'edit'
}

function getChangeTypeColor(type?: string): string {
  if (!type) return 'text-outline'
  if (type.includes('CREATED')) return 'text-green-500'
  if (type.includes('DELETED')) return 'text-error'
  if (type.includes('RESTORED') || type.includes('REVERTED')) return 'text-primary'
  if (type.includes('BRANCH')) return 'text-secondary'
  if (type.includes('STASH')) return 'text-tertiary'
  return 'text-outline'
}

function formatBlameTime(ts: string): string {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return `${diffMin} 分钟前`
    const diffHr = Math.floor(diffMin / 60)
    if (diffHr < 24) return `${diffHr} 小时前`
    const diffDay = Math.floor(diffHr / 24)
    return `${diffDay} 天前`
  } catch { return ts }
}

// Stash functions
async function loadStashes() {
  try {
    const res = await stashApi.list(serverId)
    stashes.value = res || []
  } catch (e) {
    console.warn('Failed to load stashes', e)
  }
}

async function createStash() {
  stashing.value = true
  try {
    const result = await stashApi.create(serverId, '手动暂存')
    stashes.value.unshift(result)
    toast.success('已创建暂存快照')
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '暂存失败'))
  } finally {
    stashing.value = false
  }
}

async function popStash() {
  try {
    const result = await stashApi.pop(serverId)
    toast.success(result || '已恢复暂存快照')
    await loadStashes()
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '恢复暂存失败'))
  }
}

async function discardStash(stashId: number) {
  try {
    await stashApi.discard(serverId, stashId)
    stashes.value = stashes.value.filter(s => s.id !== stashId)
    toast.success('暂存快照已丢弃')
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '丢弃暂存失败'))
  }
}

onMounted(async () => {
  try {
    const [serverRes, containersRes, volumesRes, logsRes] = await Promise.all([
      serversApi.get(serverId),
      serversApi.getContainers(serverId),
      serversApi.getVolumes(serverId),
      serversApi.getLogs(serverId),
    ])

    // Load branches, stashes, blame, and hooks
    loadBranches()
    loadStashes()
    loadBlame()
    loadHooks()

    // AI analysis runs separately (may be slow, up to 30s)
    aiLoading.value = true
    aiApi.analyzeServer(serverId).then(res => {
      const data = res || null
      if (data && data.healthScore !== undefined) {
        aiAnalysis.value = data
        aiError.value = ''
      } else {
        aiError.value = '分析结果格式异常'
        console.warn('AI analysis unexpected format:', res)
      }
    }).catch(e => {
      aiError.value = '分析失败: ' + (e?.message || '网络错误')
      console.warn('AI analysis failed:', e)
    }).finally(() => { aiLoading.value = false })
    server.value = serverRes || {}
    // Load SSH config from server data
    if (server.value.sshPort) sshConfig.value.port = server.value.sshPort
    if (server.value.sshUsername) sshConfig.value.username = server.value.sshUsername
    if (server.value.sshAuthMethod) sshConfig.value.authMethod = server.value.sshAuthMethod

    // Trigger health check to get real-time status
    serversApi.refreshHealth(serverId).then(healthRes => {
      const health = healthRes || {}
      if (health.status === 'ONLINE') {
        server.value.status = 'RUNNING'
        if (health.uptimeSeconds) server.value.uptimeSeconds = health.uptimeSeconds
        if (health.os) server.value.os = health.os
      }
    }).catch(() => {})
    const cData = containersRes || []
    containers.value = mapContainers(cData)
    const vData = volumesRes || []
    volumes.value = vData.map((v: Volume) => {
      const iconCfg = volumeIcons[v.type] || volumeIcons.database
      return {
        ...v,
        ...iconCfg,
        size: v.sizeBytes ? formatBytes(v.sizeBytes) : v.size || '',
        status: v.status || 'RW',
        statusColor: v.status === 'RW' ? 'text-green-600' : 'text-outline',
      }
    })
    const lData = logsRes || []
    logs.value = lData.map((l: LogEntry) => ({
      text: l.message || l.text || '',
      color: l.level === 'ERROR' ? 'text-red-400' : l.level === 'WARN' ? 'text-amber-400' : l.level === 'DEBUG' ? 'text-blue-300' : 'text-green-400',
    }))

    // Fetch topology edges
    try {
      const topoRes = await serversApi.getTopology(serverId)
      const edges = topoRes || []
      topologyRawEdges.value = edges
      await nextTick()
      initTopologyNodes()
      window.addEventListener('resize', () => { initTopologyNodes() })
    } catch (e) {
      console.warn('Failed to load topology', e)
    }

    // Poll container stats every 5 seconds
    refreshTimer = setInterval(refreshContainers, 5000)
  } catch (e) {
    console.error('Failed to load server data', e)
  }
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  window.removeEventListener('resize', initTopologyNodes)
})
</script>

<style scoped>
@keyframes pulse {
  0%, 100% { opacity: 0.8; }
  50% { opacity: 0.4; }
}
.topology-line {
  stroke-dasharray: 4;
  animation: dash 20s linear infinite;
}
@keyframes dash {
  to { stroke-dashoffset: -1000; }
}
</style>
