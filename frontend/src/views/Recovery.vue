<template>
  <div class="p-[24px] space-y-[40px]">
    <!-- Page Header & Recovery Wizard Stepper -->
    <section class="space-y-6">
      <div class="flex justify-between items-end">
        <div>
          <h2 class="text-[32px] font-semibold text-on-surface">恢复中心</h2>
          <p class="text-on-surface-variant text-[16px] mt-1">控制并管理服务器的状态回溯与精密恢复。</p>
        </div>
        <div class="flex space-x-3">
          <button @click="scrollToMigration" class="flex items-center space-x-2 px-4 py-2 bg-surface-container-high text-on-surface rounded-xl text-[12px] font-bold hover:bg-surface-container-highest transition-all">
            <span class="material-symbols-outlined text-[18px]">swap_horiz</span>
            <span>跨服务器迁移</span>
          </button>
        </div>
      </div>

      <!-- Recovery Wizard Stepper -->
      <div class="grid grid-cols-3 gap-4">
        <div class="glass-panel p-4 rounded-xl border-l-4 border-primary shadow-[0_0_15px_rgba(0,88,190,0.3)] flex items-center space-x-4">
          <div class="w-10 h-10 rounded-full bg-primary text-white flex items-center justify-center font-bold">1</div>
          <div>
            <p class="text-[12px] text-primary font-bold">选择快照</p>
            <p class="text-xs text-on-surface-variant">已选择：{{ selectedSnapshot?.name || '未选择' }} {{ selectedSnapshot?.createdAt ? '(' + new Date(selectedSnapshot.createdAt).toLocaleDateString('zh-CN') + ')' : '' }}</p>
          </div>
        </div>
        <div class="glass-panel p-4 rounded-xl border-l-4 border-outline-variant flex items-center space-x-4 opacity-70">
          <div class="w-10 h-10 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-bold">2</div>
          <div>
            <p class="text-[12px] text-on-surface font-bold">模拟与验证</p>
            <p class="text-xs text-on-surface-variant">准备进行风险扫描和冲突检测</p>
          </div>
        </div>
        <div class="glass-panel p-4 rounded-xl border-l-4 border-outline-variant flex items-center space-x-4 opacity-70">
          <div class="w-10 h-10 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-bold">3</div>
          <div>
            <p class="text-[12px] text-on-surface font-bold">执行回滚</p>
            <p class="text-xs text-on-surface-variant">待开始：执行原子状态恢复</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Main Layout: Comparison and AI Advisor -->
    <div class="grid grid-cols-12 gap-[16px]">
      <!-- Left Column: Comparison View -->
      <div class="col-span-8 space-y-[16px]">
        <div class="glass-panel rounded-2xl overflow-hidden">
          <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
            <h3 class="text-[24px] font-semibold flex items-center">
              <span class="material-symbols-outlined mr-2">difference</span>
              状态差异对比 (Delta View)
            </h3>
            <div class="flex items-center space-x-2">
              <span v-if="selectedSnapshot?.hash" class="px-2 py-1 rounded bg-secondary-container/20 text-on-secondary-container text-xs font-[Geist]">HASH: {{ selectedSnapshot.hash }}</span>
            </div>
          </div>
          <div class="p-6">
            <div class="grid grid-cols-2 gap-8 mb-6">
              <div class="space-y-3">
                <p class="text-[12px] font-bold text-outline uppercase tracking-wider">当前运行状态 (Active)</p>
                <div class="bg-surface-container p-4 rounded-xl border border-outline-variant/50">
                  <div class="flex items-center justify-between mb-2">
                    <span class="font-[Geist] text-sm">{{ selectedSnapshot?.name || '当前状态' }}</span>
                    <span class="w-2 h-2 rounded-full bg-primary animate-pulse"></span>
                  </div>
                  <ul class="text-xs space-y-2 text-on-surface-variant font-[Geist]">
                    <li class="flex items-center"><span class="material-symbols-outlined text-[14px] mr-2 text-primary">circle</span> 服务状态: 运行中</li>
                    <li class="flex items-center"><span class="material-symbols-outlined text-[14px] mr-2 text-primary">circle</span> 快照时间: {{ selectedSnapshot?.createdAt ? new Date(selectedSnapshot.createdAt).toLocaleString('zh-CN') : '-' }}</li>
                  </ul>
                </div>
              </div>
              <div class="space-y-3">
                <p class="text-[12px] font-bold text-outline uppercase tracking-wider">目标快照状态 (Target)</p>
                <div class="bg-primary/5 p-4 rounded-xl border border-primary/30">
                  <div class="flex items-center justify-between mb-2">
                    <span class="font-[Geist] text-sm text-primary">{{ selectedSnapshot?.name || '目标快照' }}</span>
                    <span class="w-2 h-2 rounded-full bg-outline"></span>
                  </div>
                  <ul class="text-xs space-y-2 text-on-surface-variant font-[Geist]">
                    <li class="flex items-center"><span class="material-symbols-outlined text-[14px] mr-2 text-outline">circle</span> 恢复模式: {{ recoveryMode === 'full' ? '全量恢复' : '细粒度提取' }}</li>
                    <li class="flex items-center"><span class="material-symbols-outlined text-[14px] mr-2 text-outline">circle</span> 预计耗时: {{ simulationResult?.estimatedTime || '待模拟' }}</li>
                  </ul>
                </div>
              </div>
            </div>
            <div class="bg-inverse-surface/5 rounded-xl p-4 font-[Geist] text-xs space-y-1 overflow-x-auto">
              <div v-for="line in deltaLines" :key="line.num" class="flex hover:bg-surface-container-high transition-colors">
                <span class="w-8 opacity-40 shrink-0">{{ line.num }}</span>
                <span v-if="line.type === 'remove'" class="text-error shrink-0 mr-4">{{ line.content }}</span>
                <span v-else-if="line.type === 'add'" class="text-green-500 shrink-0 mr-4">{{ line.content }}</span>
                <span v-else class="w-full">{{ line.content }}</span>
                <span v-if="line.comment" class="opacity-50 ml-2">{{ line.comment }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Recovery Options Bento -->
        <div class="grid grid-cols-2 gap-[16px]">
          <div @click="recoveryMode = 'full'" :class="recoveryMode === 'full' ? 'ring-2 ring-primary bg-primary/5' : ''" class="glass-panel p-6 rounded-2xl hover:bg-surface-container-high transition-all cursor-pointer group">
            <div class="flex justify-between items-start mb-4">
              <div class="p-3 bg-primary/10 text-primary rounded-xl group-hover:scale-110 transition-transform">
                <span class="material-symbols-outlined text-[32px]">settings_backup_restore</span>
              </div>
              <span class="bg-primary/10 text-primary text-[10px] px-2 py-0.5 rounded-full font-bold uppercase">推荐</span>
            </div>
            <h4 class="text-[24px] font-semibold mb-2">全量深度恢复</h4>
            <p class="text-on-surface-variant text-sm">完全重写磁盘镜像，确保系统状态与快照完全一致。适用于灾难性故障修复。</p>
          </div>
          <div @click="recoveryMode = 'partial'" :class="recoveryMode === 'partial' ? 'ring-2 ring-secondary bg-secondary/5' : ''" class="glass-panel p-6 rounded-2xl hover:bg-surface-container-high transition-all cursor-pointer group">
            <div class="flex justify-between items-start mb-4">
              <div class="p-3 bg-secondary/10 text-secondary rounded-xl group-hover:scale-110 transition-transform">
                <span class="material-symbols-outlined text-[32px]">folder_managed</span>
              </div>
            </div>
            <h4 class="text-[24px] font-semibold mb-2">细粒度组件提取</h4>
            <p class="text-on-surface-variant text-sm">仅恢复特定的文件系统、数据库表或微服务镜像。保留当前环境的非冲突数据。</p>
          </div>
        </div>

        <!-- Selective Restore File Selection (shown when partial mode is selected) -->
        <div v-if="recoveryMode === 'partial'" class="glass-panel rounded-2xl overflow-hidden">
          <div class="px-6 py-4 border-b border-outline-variant/30 flex justify-between items-center bg-surface-container-low/50">
            <h3 class="text-[24px] font-semibold flex items-center">
              <span class="material-symbols-outlined mr-2">folder_open</span>
              选择恢复文件
            </h3>
            <span class="text-[10px] px-2 py-0.5 rounded-full bg-secondary/10 text-secondary font-bold">
              {{ selectedRestorePaths.length }} 项已选
            </span>
          </div>
          <div class="p-6 space-y-4">
            <p class="text-[12px] text-on-surface-variant">选择需要恢复的文件或目录，未选择的文件将保持当前状态不变。</p>

            <!-- Preset paths -->
            <div class="space-y-2">
              <label class="text-[12px] font-bold text-on-surface-variant">快速选择</label>
              <div class="flex flex-wrap gap-2">
                <button v-for="preset in restorePresetPaths" :key="preset.value" @click="toggleRestorePath(preset.value)"
                  class="px-3 py-1.5 rounded-lg text-[11px] font-bold border transition-all"
                  :class="selectedRestorePaths.includes(preset.value)
                    ? 'bg-primary/10 border-primary text-primary'
                    : 'bg-white/50 border-outline-variant/30 text-on-surface-variant hover:border-primary/30'">
                  {{ preset.label }}
                </button>
              </div>
            </div>

            <!-- Custom path input -->
            <div class="space-y-2">
              <label class="text-[12px] font-bold text-on-surface-variant">自定义路径</label>
              <div class="flex gap-2">
                <input v-model="customRestorePath" @keydown.enter.prevent="addCustomRestorePath"
                  class="flex-1 px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
                  placeholder="/etc/nginx/nginx.conf" />
                <button @click="addCustomRestorePath"
                  class="px-3 py-2 bg-surface-container-high border border-outline-variant/30 rounded-lg text-[11px] font-bold text-on-surface-variant hover:bg-surface-container-highest transition-colors">
                  添加
                </button>
              </div>
            </div>

            <!-- Selected paths chips -->
            <div v-if="selectedRestorePaths.length > 0" class="flex flex-wrap gap-1.5">
              <span v-for="(p, i) in selectedRestorePaths" :key="i"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-primary/10 text-primary text-[10px] font-bold">
                {{ p }}
                <button @click="selectedRestorePaths.splice(i, 1)" class="hover:text-error transition-colors">&times;</button>
              </span>
            </div>

            <!-- Target path -->
            <div class="space-y-2">
              <label class="text-[12px] font-bold text-on-surface-variant">恢复目标路径（可选）</label>
              <input v-model="restoreTargetPath"
                class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
                placeholder="/var/chronovault/restore/1/" />
              <p class="text-[11px] text-outline">留空将使用默认路径 /var/chronovault/restore/{snapshotId}/</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: AI Advisor & Risk Assessment -->
      <div class="col-span-4 space-y-[16px]">
        <!-- AI Insight Card -->
        <div class="relative overflow-hidden p-6 rounded-2xl bg-gradient-to-br from-primary/10 via-surface-bright to-tertiary-container/10 border border-primary/20 shadow-lg">
          <div class="absolute top-0 right-0 w-32 h-32 bg-primary/10 blur-[60px] rounded-full"></div>
          <div class="relative z-10">
            <div class="flex items-center space-x-2 mb-4">
              <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">psychology</span>
              <span class="text-[12px] text-primary font-bold tracking-widest uppercase">AI 恢复顾问</span>
            </div>
            <p class="text-on-surface text-[16px] mb-6 italic leading-relaxed">
              {{ simulationResult?.aiInsight || '选择快照并执行模拟验证后，AI 将提供恢复建议和风险评估。' }}
            </p>
            <div class="space-y-4">
              <div class="flex items-center justify-between text-xs">
                <span class="text-on-surface-variant">成功率预测</span>
                <span class="text-primary font-bold">{{ simulationResult?.successRate ? simulationResult.successRate + '%' : '-' }}</span>
              </div>
              <div class="w-full bg-surface-container-high h-1.5 rounded-full overflow-hidden">
                <div class="bg-primary h-full" :style="{ width: (simulationResult?.successRate || 0) + '%' }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Risk Assessment -->
        <div class="glass-panel p-6 rounded-2xl border-l-4 border-error">
          <h4 class="text-[24px] font-semibold mb-4 flex items-center text-error">
            <span class="material-symbols-outlined mr-2">report_problem</span>
            风险评估
          </h4>
          <div class="space-y-4">
            <div v-if="simulationResult?.risks?.length" v-for="risk in simulationResult.risks" :key="risk.title" class="flex space-x-3 items-start p-3 rounded-lg" :class="risk.severity === 'HIGH' ? 'bg-error-container/30' : 'bg-surface-container'">
              <span class="material-symbols-outlined text-[18px] mt-0.5" :class="risk.severity === 'HIGH' ? 'text-error' : 'text-on-surface-variant'">{{ risk.severity === 'HIGH' ? 'warning' : 'info' }}</span>
              <div>
                <p class="text-sm font-bold" :class="risk.severity === 'HIGH' ? 'text-on-error-container' : 'text-on-surface'">{{ risk.title }}</p>
                <p class="text-xs" :class="risk.severity === 'HIGH' ? 'text-on-error-container/80' : 'text-on-surface-variant'">{{ risk.desc }}</p>
              </div>
            </div>
            <div v-if="!simulationResult?.risks?.length" class="flex space-x-3 items-start p-3 bg-surface-container rounded-lg">
              <span class="material-symbols-outlined text-on-surface-variant text-[18px] mt-0.5">info</span>
              <div>
                <p class="text-sm font-bold text-on-surface">待评估</p>
                <p class="text-xs text-on-surface-variant">执行模拟验证后将显示详细的风险评估结果。</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Server Status Card -->
        <div class="rounded-2xl overflow-hidden aspect-video relative group bg-gradient-to-br from-primary/20 to-secondary/10 flex items-center justify-center">
          <div class="text-center">
            <span class="material-symbols-outlined text-primary text-[48px] mb-2">dns</span>
            <p class="text-[12px] text-outline font-bold uppercase tracking-widest mb-1">目标服务器</p>
            <p class="text-[24px] font-semibold text-on-surface">{{ selectedSnapshot?.serverName || '未选择' }}</p>
          </div>
        </div>

        <!-- Action Trigger -->
        <div class="space-y-3">
          <button @click="startSimulation" :disabled="simulating" class="w-full py-4 bg-primary text-white rounded-2xl text-[24px] font-semibold shadow-lg hover:shadow-primary/30 hover:bg-primary/90 transition-all flex items-center justify-center space-x-3 disabled:opacity-50">
            <span class="material-symbols-outlined">rocket_launch</span>
            <span>{{ simulating ? '模拟验证中...' : '开始模拟验证' }}</span>
          </button>
          <button v-if="simulationResult && simulationResult.status === 'COMPLETED'" @click="executeRecovery" :disabled="executing" class="w-full py-4 bg-error text-white rounded-2xl text-[24px] font-semibold shadow-lg hover:shadow-error/30 hover:bg-error/90 transition-all flex items-center justify-center space-x-3 disabled:opacity-50">
            <span class="material-symbols-outlined">settings_backup_restore</span>
            <span>{{ executing ? '执行恢复中...' : '执行恢复 (Rollback)' }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Disaster Recovery Plans -->
    <section class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h3 class="text-[24px] font-semibold flex items-center gap-2">
            <span class="material-symbols-outlined text-error" style="font-variation-settings: 'FILL' 1;">emergency</span>
            灾难恢复计划
          </h3>
          <p class="text-[14px] text-on-surface-variant">管理和执行灾难恢复预案</p>
        </div>
        <button @click="showDrForm = !showDrForm"
          class="px-4 py-2 bg-primary text-white rounded-lg text-[12px] font-bold hover:bg-primary-container transition-all flex items-center gap-1.5">
          <span class="material-symbols-outlined text-[16px]">{{ showDrForm ? 'close' : 'add' }}</span>
          {{ showDrForm ? '取消' : '新建计划' }}
        </button>
      </div>

      <!-- DR Form -->
      <div v-if="showDrForm" class="glass-panel p-6 rounded-2xl space-y-4">
        <h4 class="text-[18px] font-bold">{{ editingDr ? '编辑恢复计划' : '新建恢复计划' }}</h4>
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">计划名称</label>
            <input v-model="drForm.name" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[14px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="例如 生产环境主站恢复" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">状态</label>
            <select v-model="drForm.status" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[14px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none appearance-none">
              <option value="DRAFT">草稿</option>
              <option value="ACTIVE">启用</option>
              <option value="ARCHIVED">归档</option>
            </select>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">RTO 目标（分钟）</label>
            <input v-model.number="drForm.estimatedRto" type="number" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[14px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="30" />
          </div>
          <div class="space-y-1.5">
            <label class="text-[12px] font-bold text-on-surface-variant">RPO 目标（分钟）</label>
            <input v-model.number="drForm.estimatedRpo" type="number" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[14px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="15" />
          </div>
        </div>
        <div class="space-y-1.5">
          <label class="text-[12px] font-bold text-on-surface-variant">描述</label>
          <textarea v-model="drForm.description" rows="2" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[14px] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none" placeholder="恢复计划描述"></textarea>
        </div>
        <div class="space-y-1.5">
          <label class="text-[12px] font-bold text-on-surface-variant">恢复步骤（JSON 格式）</label>
          <textarea v-model="drForm.steps" rows="4" class="w-full px-3 py-2 bg-white/50 border border-outline-variant rounded-lg text-[13px] font-[Geist] focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
            placeholder='[{"type":"RESTORE","description":"恢复数据库快照"},{"type":"START_SERVICES","description":"启动 Docker 服务"},{"type":"VERIFY_HEALTH","description":"健康检查"}]'></textarea>
        </div>
        <div class="flex justify-end gap-2">
          <button @click="showDrForm = false; editingDr = null" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
          <button @click="saveDrPlan" :disabled="!drForm.name"
            class="px-6 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-50">
            {{ editingDr ? '更新' : '创建' }}
          </button>
        </div>
      </div>

      <!-- DR Plans List -->
      <div v-if="drPlans.length === 0" class="glass-panel p-12 rounded-2xl text-center">
        <span class="material-symbols-outlined text-outline text-[48px] mb-3 block">emergency</span>
        <p class="text-[16px] text-on-surface-variant mb-4">暂无灾难恢复计划</p>
        <button @click="showDrForm = true" class="px-6 py-2.5 rounded-lg bg-primary text-white text-[13px] font-bold hover:opacity-90 transition-all">
          创建第一个恢复计划
        </button>
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div v-for="plan in drPlans" :key="plan.id"
          class="glass-panel p-5 rounded-2xl border-l-4 transition-all hover:shadow-lg"
          :class="plan.status === 'ACTIVE' ? 'border-l-green-500' : plan.status === 'DRAFT' ? 'border-l-outline' : 'border-l-outline-variant'">
          <div class="flex items-start justify-between mb-3">
            <div>
              <h4 class="text-[18px] font-bold">{{ plan.name }}</h4>
              <p class="text-[12px] text-on-surface-variant mt-0.5">{{ plan.description || '无描述' }}</p>
            </div>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold"
              :class="plan.status === 'ACTIVE' ? 'bg-green-500/10 text-green-600' : plan.status === 'DRAFT' ? 'bg-outline/10 text-outline' : 'bg-outline-variant/10 text-outline-variant'">
              {{ plan.status === 'ACTIVE' ? '启用' : plan.status === 'DRAFT' ? '草稿' : '归档' }}
            </span>
          </div>
          <div class="flex gap-4 text-[12px] text-outline mb-4">
            <span v-if="plan.estimatedRto">RTO: {{ plan.estimatedRto }}分钟</span>
            <span v-if="plan.estimatedRpo">RPO: {{ plan.estimatedRpo }}分钟</span>
            <span v-if="plan.lastExecutedAt">上次执行: {{ new Date(plan.lastExecutedAt).toLocaleDateString('zh-CN') }}</span>
          </div>
          <div class="flex gap-2">
            <button @click="executeDrPlan(plan.id)"
              class="px-3 py-1.5 bg-error text-white rounded-lg text-[11px] font-bold hover:bg-error/90 transition-all flex items-center gap-1">
              <span class="material-symbols-outlined text-[14px]">play_arrow</span>
              执行
            </button>
            <button @click="editDrPlan(plan)"
              class="px-3 py-1.5 bg-surface-container-high text-on-surface rounded-lg text-[11px] font-bold hover:bg-surface-container-highest transition-all">
              编辑
            </button>
            <button @click="deleteDrPlan(plan.id)"
              class="px-3 py-1.5 bg-surface-container-high text-error rounded-lg text-[11px] font-bold hover:bg-error/10 transition-all">
              删除
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Server Migration Toolbar -->
    <section class="glass-panel p-6 rounded-2xl border-dashed border-2 border-outline-variant/50">
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div class="flex items-center space-x-4">
          <div class="w-12 h-12 bg-surface-container-highest rounded-full flex items-center justify-center">
            <span class="material-symbols-outlined text-outline">move_down</span>
          </div>
          <div>
            <h5 class="text-[24px] font-semibold">跨服务器迁移工具</h5>
            <p class="text-sm text-on-surface-variant">将此快照作为镜像部署到新的计算节点。</p>
          </div>
        </div>
        <div class="flex items-center space-x-4">
          <div class="relative">
            <select v-model="targetServerId" class="bg-surface-container border-none rounded-xl pr-10 py-2 text-sm focus:ring-2 focus:ring-primary appearance-none">
              <option :value="0" disabled>选择目标服务器</option>
              <option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }} ({{ s.ip }})</option>
            </select>
            <span class="material-symbols-outlined absolute right-3 top-2 pointer-events-none text-outline text-[20px]">expand_more</span>
          </div>
          <button @click="openDeployConfirm" class="px-6 py-2 border-2 border-primary text-primary font-bold rounded-xl hover:bg-primary hover:text-white transition-all">部署迁移</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { useModalStore } from '@/stores/modal'
import { recoveryApi } from '@/api/recovery'
import { snapshotsApi } from '@/api/snapshots'
import { serversApi } from '@/api/servers'
import { drApi } from '@/api/disasterRecovery'
import ConfirmModal from '@/components/modals/ConfirmModal.vue'
import type { DisasterRecoveryPlan } from '@/types'

/** Extract a safe error message from an unknown catch value */
function getErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object' && 'message' in e) return (e as { message: string }).message
  if (typeof e === 'string') return e
  return fallback
}

const toast = useToastStore()
const modal = useModalStore()

function scrollToMigration() {
  document.querySelector('.border-dashed')?.scrollIntoView({ behavior: 'smooth' })
}

const recoveryMode = ref('full')
import type { Snapshot, Server } from '@/types'

const selectedSnapshot = ref<Snapshot | null>(null)
const snapshots = ref<Snapshot[]>([])
interface DeltaLine { num: number; type: string; content: string; comment?: string }
const deltaLines = ref<DeltaLine[]>([])
const simulationResult = ref<any>(null)
const servers = ref<Server[]>([])
const targetServerId = ref(0)
const simulating = ref(false)
const executing = ref(false)

// DR Plan state
const drPlans = ref<DisasterRecoveryPlan[]>([])
const showDrForm = ref(false)
const editingDr = ref<DisasterRecoveryPlan | null>(null)
const drForm = ref({ name: '', description: '', steps: '', estimatedRto: 30, estimatedRpo: 15, status: 'DRAFT' as DisasterRecoveryPlan['status'] })

async function loadDrPlans() {
  try {
    const res = await drApi.getAll()
    drPlans.value = res || []
  } catch (e) {
    console.error('Failed to load DR plans', e)
  }
}

async function saveDrPlan() {
  try {
    if (editingDr.value) {
      await drApi.update(editingDr.value.id, drForm.value)
      toast.success('恢复计划已更新')
    } else {
      await drApi.create(drForm.value)
      toast.success('恢复计划已创建')
    }
    editingDr.value = null
    showDrForm.value = false
    drForm.value = { name: '', description: '', steps: '', estimatedRto: 30, estimatedRpo: 15, status: 'DRAFT' }
    await loadDrPlans()
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '保存失败'))
  }
}

function editDrPlan(plan: DisasterRecoveryPlan) {
  editingDr.value = plan
  drForm.value = { name: plan.name, description: plan.description || '', steps: plan.steps || '', estimatedRto: plan.estimatedRto || 30, estimatedRpo: plan.estimatedRpo || 15, status: plan.status }
  showDrForm.value = true
}

async function executeDrPlan(id: number) {
  try {
    await drApi.execute(id)
    toast.success('恢复计划已执行')
    await loadDrPlans()
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '执行失败'))
  }
}

async function deleteDrPlan(id: number) {
  try {
    await drApi.delete(id)
    drPlans.value = drPlans.value.filter(p => p.id !== id)
    toast.success('恢复计划已删除')
  } catch (e: unknown) {
    toast.error(getErrorMessage(e, '删除失败'))
  }
}

// Selective restore state
const selectedRestorePaths = ref<string[]>([])
const customRestorePath = ref('')
const restoreTargetPath = ref('')

const restorePresetPaths = [
  { label: '/etc/nginx', value: '/etc/nginx' },
  { label: '/etc/ssh', value: '/etc/ssh' },
  { label: '/var/www', value: '/var/www' },
  { label: '/home', value: '/home' },
  { label: '/opt', value: '/opt' },
  { label: '/etc/crontab', value: '/etc/crontab' },
]

function toggleRestorePath(val: string) {
  const idx = selectedRestorePaths.value.indexOf(val)
  if (idx >= 0) selectedRestorePaths.value.splice(idx, 1)
  else selectedRestorePaths.value.push(val)
}

function addCustomRestorePath() {
  const v = customRestorePath.value.trim()
  if (v && !selectedRestorePaths.value.includes(v)) {
    selectedRestorePaths.value.push(v)
    customRestorePath.value = ''
  }
}

function openDeployConfirm() {
  modal.open({
    component: ConfirmModal,
    title: '确认部署迁移',
    props: {
      message: '即将把当前快照作为镜像部署到选定区域。迁移过程中源服务器不受影响。是否继续？',
      confirmText: '开始迁移',
      successMessage: '迁移任务已提交至部署队列',
      onConfirm: async () => {
        if (selectedSnapshot.value && targetServerId.value) {
          await recoveryApi.migrate({ sourceServerId: selectedSnapshot.value.serverId!, targetServerId: targetServerId.value, snapshotId: selectedSnapshot.value.id })
        } else {
          toast.error('请选择目标服务器')
        }
      },
    },
  })
}

async function startSimulation() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }
  simulating.value = true
  try {
    const res = await recoveryApi.simulate({ serverId: selectedSnapshot.value.serverId!, snapshotId: selectedSnapshot.value.id })
    simulationResult.value = res
    toast.success('模拟验证已完成')
  } catch (e) {
    toast.error('模拟验证失败')
  } finally {
    simulating.value = false
  }
}

async function executeRecovery() {
  if (!selectedSnapshot.value) {
    toast.error('请先选择一个快照')
    return
  }

  // Selective restore: when partial mode and files are selected
  if (recoveryMode.value === 'partial' && selectedRestorePaths.value.length > 0) {
    modal.open({
      component: ConfirmModal,
      title: '确认选择性恢复',
      props: {
        message: `即将从快照 "${selectedSnapshot.value.name || '选定快照'}" 中恢复 ${selectedRestorePaths.value.length} 个文件/目录。此操作将覆盖目标路径中的对应文件。是否继续？`,
        confirmText: '执行恢复',
        confirmClass: 'bg-error hover:bg-error/90',
        successMessage: '文件恢复任务已提交',
        onConfirm: async () => {
          executing.value = true
          try {
            if (!selectedSnapshot.value) return
            const result = await snapshotsApi.restoreFiles(selectedSnapshot.value.id, {
              paths: selectedRestorePaths.value,
              targetPath: restoreTargetPath.value || undefined,
            })
            toast.success(result || '文件恢复完成')
          } catch (e: unknown) {
            const msg = getErrorMessage(e, '文件恢复失败')
            toast.error(msg)
          } finally {
            executing.value = false
          }
        },
      },
    })
    return
  }

  // Full restore or partial without files selected
  modal.open({
    component: ConfirmModal,
    title: '确认执行恢复',
    props: {
      message: `即将执行${recoveryMode.value === 'full' ? '全量' : '细粒度'}恢复至 ${selectedSnapshot.value.name || '选定快照'}。此操作将覆盖当前系统状态，期间服务将短暂离线。是否继续？`,
      confirmText: '执行恢复',
      confirmClass: 'bg-error hover:bg-error/90',
      successMessage: '恢复任务已提交，正在后台执行',
      onConfirm: async () => {
        executing.value = true
        try {
          if (!selectedSnapshot.value) return
          await recoveryApi.execute({
            serverId: selectedSnapshot.value.serverId!,
            snapshotId: selectedSnapshot.value.id,
            mode: recoveryMode.value,
          })
          toast.success('恢复任务已提交，预计 2-5 分钟完成')
        } catch (e: unknown) {
          const msg = getErrorMessage(e, '恢复执行失败')
          toast.error(msg)
        } finally {
          executing.value = false
        }
      },
    },
  })
}

onMounted(async () => {
  try {
    const [snapshotsRes, serversRes] = await Promise.all([
      snapshotsApi.getAll(),
      serversApi.getAll().catch(() => []),
    ])
    snapshots.value = snapshotsRes || []
    servers.value = serversRes || []
    if (snapshots.value.length > 0) {
      selectedSnapshot.value = snapshots.value[0]
      const diffRes = await snapshotsApi.getDiff(snapshots.value[0].id)
      deltaLines.value = (diffRes || []).map((d, i) => ({ num: i + 1, type: 'context', content: `${d.path} ${d.prev} → ${d.next}` }))
    }
  } catch (e) {
    console.error('Failed to load recovery data', e)
  }
  loadDrPlans()
})
</script>
