<template>
  <div class="min-h-screen bg-background text-on-surface selection:bg-primary/20">
    <!-- Top Navigation Bar -->
    <header class="fixed top-0 z-50 w-full h-16 bg-surface-bright/80 backdrop-blur-xl border-b border-outline-variant/30 flex justify-between items-center px-6 shadow-sm">
      <div class="flex items-center gap-3">
        <span class="material-symbols-outlined text-primary text-3xl" style="font-variation-settings: 'FILL' 1;">auto_awesome</span>
        <span class="font-headline-lg text-headline-lg font-bold tracking-tighter text-on-surface">ChronoVault</span>
      </div>
      <button @click="router.push('/dashboard')" class="font-label-md text-label-md font-medium px-4 py-2 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">
        跳过引导
      </button>
    </header>

    <!-- Main Content -->
    <main class="pt-24 pb-12 min-h-screen flex flex-col items-center">
      <div class="max-w-4xl w-full px-6">
        <!-- Welcome Header -->
        <div class="text-center mb-12">
          <h1 class="font-headline-xl text-headline-xl font-semibold text-on-surface mb-2">开始您的 ChronoVault 之旅</h1>
          <p class="font-body-md text-body-md text-on-surface-variant">只需几步，即可开启 AI 驱动的时间旅行级服务器管理。</p>
        </div>

        <!-- Steps: Left Timeline + Right Content -->
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <!-- Left: Steps Timeline -->
          <div class="lg:col-span-4 flex flex-col gap-6">
            <div class="relative flex flex-col gap-8">
              <!-- Vertical Line -->
              <div class="absolute left-5 top-10 h-[calc(100%-40px)] w-0.5 bg-outline-variant/30"></div>

              <div v-for="(s, i) in steps" :key="i" class="flex items-start gap-4 cursor-pointer group" @click="goToStep(i + 1)">
                <div class="z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-all duration-300"
                  :class="step === i + 1
                    ? 'bg-primary text-on-primary shadow-lg shadow-primary/20'
                    : step > i + 1
                      ? 'bg-primary/20 text-primary border-2 border-primary/30'
                      : 'bg-surface-container-high border-2 border-outline-variant text-on-surface-variant'">
                  <span v-if="step > i + 1" class="material-symbols-outlined text-[18px]">check</span>
                  <span v-else class="material-symbols-outlined text-[18px]">{{ s.icon }}</span>
                </div>
                <div class="pt-1 transition-opacity duration-300" :class="step === i + 1 ? 'opacity-100' : 'opacity-50'">
                  <h3 class="font-headline-lg text-[18px] font-semibold" :class="step === i + 1 ? 'text-primary' : 'text-on-surface'">{{ s.title }}</h3>
                  <p class="font-body-sm text-body-sm text-on-surface-variant">{{ s.desc }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- Right: Interactive Content -->
          <div class="lg:col-span-8">
            <div class="glass-panel rounded-xl p-card-padding border-outline-variant/20 flex flex-col gap-6 overflow-hidden animate-fade-in">

              <!-- Step 1: Server Details + SSH -->
              <template v-if="step === 1">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">dns</span>
                  <h2 class="font-headline-lg text-headline-lg font-semibold">连接服务器</h2>
                </div>

                <!-- Basic Info -->
                <div class="grid grid-cols-2 gap-4">
                  <div class="space-y-1.5">
                    <label class="font-label-md text-label-md font-medium text-on-surface-variant">服务器名称 *</label>
                    <input v-model="serverName" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-body-sm" placeholder="如：Prod-East-01" type="text" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="font-label-md text-label-md font-medium text-on-surface-variant">IP 地址 *</label>
                    <input v-model="serverIp" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-body-sm" placeholder="如：192.168.1.100" type="text" />
                  </div>
                </div>

                <!-- SSH Config -->
                <div class="border-t border-outline-variant/20 pt-4 space-y-4">
                  <p class="font-label-md text-label-md font-medium text-on-surface-variant flex items-center gap-1.5">
                    <span class="material-symbols-outlined text-[16px]">vpn_key</span> SSH 认证配置
                  </p>
                  <div class="grid grid-cols-3 gap-4">
                    <div class="space-y-1.5">
                      <label class="font-label-md text-label-md font-medium text-on-surface-variant">SSH 端口</label>
                      <input v-model.number="sshPort" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-body-sm" placeholder="22" type="number" />
                    </div>
                    <div class="space-y-1.5">
                      <label class="font-label-md text-label-md font-medium text-on-surface-variant">用户名</label>
                      <input v-model="sshUsername" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-body-sm" placeholder="root" type="text" />
                    </div>
                    <div class="space-y-1.5">
                      <label class="font-label-md text-label-md font-medium text-on-surface-variant">认证方式</label>
                      <select v-model="sshAuthMethod" class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-body-sm">
                        <option value="KEY">SSH 密钥</option>
                        <option value="PASSWORD">密码</option>
                      </select>
                    </div>
                  </div>
                  <div class="space-y-1.5">
                    <label class="font-label-md text-label-md font-medium text-on-surface-variant">
                      {{ sshAuthMethod === 'KEY' ? 'SSH 私钥 *' : '密码 *' }}
                    </label>
                    <textarea v-if="sshAuthMethod === 'KEY'" v-model="sshCredential"
                      class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-mono-code text-mono-code resize-none"
                      rows="5" placeholder="-----BEGIN OPENSSH PRIVATE KEY-----&#10;...&#10;-----END OPENSSH PRIVATE KEY-----"></textarea>
                    <input v-else v-model="sshCredential" type="password"
                      class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-body-sm"
                      placeholder="输入 SSH 密码" />
                  </div>
                </div>

                <!-- Connection Status -->
                <div v-if="connectionStatus" class="rounded-lg p-3 flex items-center gap-2 font-body-sm text-body-sm"
                  :class="connectionStatus === 'success' ? 'bg-green-50 text-green-700 border border-green-200' : connectionStatus === 'error' ? 'bg-error-container text-error border border-error/20' : 'bg-primary-container/10 text-primary border border-primary/20'">
                  <span class="material-symbols-outlined text-[18px]">
                    {{ connectionStatus === 'success' ? 'check_circle' : connectionStatus === 'error' ? 'error' : 'hourglass_top' }}
                  </span>
                  {{ connectionMessage }}
                </div>
              </template>

              <!-- Step 2: Install Agent -->
              <template v-if="step === 2">
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">code</span>
                    <h2 class="font-headline-lg text-headline-lg font-semibold">安装 Agent</h2>
                  </div>
                  <span class="px-2 py-1 bg-primary-container/10 text-primary rounded font-label-md text-label-md font-medium">可选</span>
                </div>
                <p class="font-body-sm text-body-sm text-on-surface-variant -mt-2">通过 SSH 自动安装 Agent 到目标服务器，安装后可获得高级监控和备份能力。</p>

                <!-- Install Progress -->
                <div v-if="agentInstalling" class="p-4 rounded-xl bg-surface-container border border-outline-variant/30 space-y-3">
                  <div class="flex items-center gap-3">
                    <div class="h-5 w-5 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
                    <span class="font-label-md text-label-md font-medium text-on-surface-variant">正在通过 SSH 安装 Agent...</span>
                  </div>
                  <div class="space-y-1.5 pl-8">
                    <div v-for="(s, i) in agentInstallSteps" :key="i"
                      class="flex items-center gap-2 font-body-sm text-body-sm"
                      :class="i < agentInstallSteps.length - 1 ? 'text-primary' : 'text-on-surface-variant'">
                      <span class="material-symbols-outlined text-[16px]">
                        {{ i < agentInstallSteps.length - 1 ? 'check_circle' : 'hourglass_top' }}
                      </span>
                      {{ s }}
                    </div>
                  </div>
                </div>

                <!-- Install Success -->
                <div v-if="agentInstalled && !agentInstallError" class="space-y-4">
                  <div class="p-4 rounded-xl bg-green-50 border border-green-200 flex items-center gap-3">
                    <span class="material-symbols-outlined text-green-500 text-[24px]">check_circle</span>
                    <div>
                      <p class="font-body-sm text-body-sm font-bold text-green-700">Agent 安装成功</p>
                      <p class="text-[12px] text-green-600">Agent 已注册并启动，将自动上报服务器状态。</p>
                    </div>
                  </div>
                  <div v-if="agentApiKey" class="p-4 rounded-xl bg-surface-container border border-outline-variant/20 space-y-2">
                    <p class="font-label-md text-label-md font-medium text-on-surface-variant">Agent API 密钥（仅显示一次）</p>
                    <div class="flex gap-2">
                      <input :value="agentApiKey" readonly
                        class="flex-1 bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[13px] font-[Geist] text-on-surface" />
                      <button @click="copyAgentKey"
                        class="px-3 py-2 bg-primary text-white rounded-lg hover:bg-primary/90 transition-all">
                        <span class="material-symbols-outlined text-[18px]">{{ agentKeyCopied ? 'check' : 'content_copy' }}</span>
                      </button>
                    </div>
                    <p class="text-[11px] text-outline">请妥善保管此密钥，它是 Agent 与 ChronoVault 通信的凭证。</p>
                  </div>
                </div>

                <!-- Install Error -->
                <div v-if="agentInstallError" class="space-y-3">
                  <div class="p-4 rounded-xl bg-error-container text-error border border-error/20 font-body-sm text-body-sm">
                    <span class="material-symbols-outlined text-[18px] mr-1 align-middle">error</span>
                    {{ agentInstallError }}
                  </div>
                  <button @click="installAgent"
                    class="px-4 py-2 border-2 border-primary/30 rounded-lg text-[12px] font-bold text-primary hover:bg-primary/5 transition-all">
                    重试安装
                  </button>
                </div>

                <!-- Not yet started -->
                <div v-if="!agentInstalling && !agentInstalled && !agentInstallError"
                  class="bg-surface-container rounded-xl p-4 border border-outline-variant/30 space-y-2">
                  <p class="font-label-md text-label-md font-medium text-on-surface-variant">安装过程将自动完成：</p>
                  <div class="flex items-center gap-2 font-body-sm text-body-sm text-on-surface-variant">
                    <span class="material-symbols-outlined text-primary text-[16px]">check_circle</span>
                    <span>通过 SSH 上传 Agent 二进制到 /usr/local/bin</span>
                  </div>
                  <div class="flex items-center gap-2 font-body-sm text-body-sm text-on-surface-variant">
                    <span class="material-symbols-outlined text-primary text-[16px]">check_circle</span>
                    <span>写入配置并注册到 ChronoVault</span>
                  </div>
                  <div class="flex items-center gap-2 font-body-sm text-body-sm text-on-surface-variant">
                    <span class="material-symbols-outlined text-primary text-[16px]">check_circle</span>
                    <span>创建 systemd 服务并启动</span>
                  </div>
                </div>
              </template>

              <!-- Step 3: Environment Detection -->
              <template v-if="step === 3">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">radar</span>
                  <h2 class="font-headline-lg text-headline-lg font-semibold">环境检测</h2>
                </div>
                <p class="font-body-sm text-body-sm text-on-surface-variant">
                  {{ scanDone ? '扫描完成！以下是服务器环境详情。' : '正在通过 SSH 扫描服务器运行环境，请稍候...' }}
                </p>

                <!-- Scanning Animation -->
                <div v-if="!scanDone" class="mt-4 p-4 rounded-xl bg-surface-container border border-outline-variant/30 flex flex-col gap-4">
                  <div class="flex items-center justify-between">
                    <div class="flex items-center gap-3">
                      <div class="relative">
                        <span class="material-symbols-outlined text-primary animate-pulse">hub</span>
                        <div class="absolute inset-0 bg-primary/20 blur-lg rounded-full"></div>
                      </div>
                      <span class="font-label-md text-label-md font-medium text-on-surface-variant">{{ scanLabel }}</span>
                    </div>
                    <div class="flex items-center gap-1">
                      <div class="h-1.5 w-1.5 rounded-full bg-primary animate-ping"></div>
                      <span class="font-mono-code text-[10px] text-primary">SCN-{{ scanId }}</span>
                    </div>
                  </div>
                  <div class="space-y-2">
                    <div class="flex justify-between items-end">
                      <span class="font-label-md text-label-md font-bold text-primary">{{ scanPhase }}</span>
                      <span class="font-mono-code text-label-md text-primary">{{ scanProgress }}%</span>
                    </div>
                    <div class="w-full h-2 bg-surface-container-high rounded-full overflow-hidden">
                      <div class="h-full bg-primary progress-flow transition-all duration-500" :style="{ width: scanProgress + '%' }"></div>
                    </div>
                  </div>
                  <div class="flex flex-col gap-1 min-h-[80px]">
                    <div v-for="(log, i) in visibleLogs" :key="i"
                      class="flex gap-2 font-mono-code text-mono-code text-outline italic">
                      <span class="shrink-0" :class="log.type === 'INFO' ? 'text-primary-container' : log.type === 'SCAN' ? 'text-secondary' : 'text-tertiary'">{{ log.type }}</span>
                      <span>[{{ log.time }}] {{ log.text }}</span>
                    </div>
                  </div>
                </div>

                <!-- Scan Results -->
                <div v-if="scanDone && scanResult" class="space-y-4">
                  <div class="space-y-2">
                    <div class="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex items-center gap-3">
                      <span class="material-symbols-outlined text-primary text-[20px]">computer</span>
                      <div class="flex-1 min-w-0">
                        <p class="font-label-md text-label-md font-medium text-on-surface-variant">操作系统</p>
                        <p class="font-body-sm text-body-sm text-on-surface truncate">{{ formatOs(scanResult.os) }}</p>
                      </div>
                    </div>
                    <div class="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex items-center gap-3">
                      <span class="material-symbols-outlined text-primary text-[20px]">schedule</span>
                      <div class="flex-1">
                        <p class="font-label-md text-label-md font-medium text-on-surface-variant">运行时间</p>
                        <p class="font-body-sm text-body-sm text-on-surface">{{ formatUptime(scanResult.uptime) }}</p>
                      </div>
                    </div>
                    <div class="grid grid-cols-2 gap-2">
                      <div class="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex items-center gap-3">
                        <span class="material-symbols-outlined text-primary text-[20px]">storage</span>
                        <div>
                          <p class="font-label-md text-label-md font-medium text-on-surface-variant">磁盘</p>
                          <p class="font-body-sm text-body-sm text-on-surface">{{ formatDisk(scanResult.disk) }}</p>
                        </div>
                      </div>
                      <div class="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex items-center gap-3">
                        <span class="material-symbols-outlined text-primary text-[20px]">memory</span>
                        <div>
                          <p class="font-label-md text-label-md font-medium text-on-surface-variant">内存</p>
                          <p class="font-body-sm text-body-sm text-on-surface">{{ formatMemory(scanResult.memory) }}</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div v-if="scanResult.containers.length > 0" class="rounded-lg bg-surface-container border border-outline-variant/20 overflow-hidden">
                    <div class="p-3 border-b border-outline-variant/10 flex items-center gap-2">
                      <span class="material-symbols-outlined text-primary text-[18px]">deployed_code</span>
                      <span class="font-label-md text-label-md font-medium text-on-surface">Docker 容器 ({{ scanResult.containers.length }})</span>
                    </div>
                    <div class="divide-y divide-outline-variant/10">
                      <div v-for="c in scanResult.containers" :key="c.name" class="px-3 py-2 flex items-center justify-between">
                        <div>
                          <p class="font-body-sm text-body-sm font-medium text-on-surface">{{ c.name }}</p>
                          <p class="text-[12px] text-on-surface-variant">{{ c.image }}</p>
                        </div>
                        <div class="text-right">
                          <span class="inline-block px-2 py-0.5 rounded text-[10px] font-bold"
                            :class="c.status.includes('Up') ? 'bg-green-100 text-green-700' : 'bg-error-container text-error'">
                            {{ c.status }}
                          </span>
                          <p class="text-[11px] text-on-surface-variant mt-0.5">CPU {{ c.cpu }} | Mem {{ c.memory }}</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div v-if="scanResult.databases.length > 0" class="rounded-lg bg-surface-container border border-outline-variant/20 p-3">
                    <div class="flex items-center gap-2 mb-2">
                      <span class="material-symbols-outlined text-primary text-[18px]">database</span>
                      <span class="font-label-md text-label-md font-medium text-on-surface">检测到的数据库</span>
                    </div>
                    <div class="flex flex-wrap gap-2">
                      <span v-for="db in scanResult.databases" :key="db.type + db.port"
                        class="px-3 py-1 bg-primary/10 text-primary rounded-full font-label-md text-label-md font-medium">
                        {{ db.type }}:{{ db.port }}
                      </span>
                    </div>
                  </div>
                </div>

                <!-- Scan Error -->
                <div v-if="scanDone && scanError" class="rounded-lg p-4 bg-error-container text-error border border-error/20 font-body-sm text-body-sm">
                  <span class="material-symbols-outlined text-[18px] mr-1 align-middle">error</span>
                  {{ scanError }}
                </div>
              </template>

              <!-- Step 4: AI Configuration -->
              <template v-if="step === 4">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">tune</span>
                  <h2 class="font-headline-lg text-headline-lg font-semibold">AI 配置</h2>
                </div>
                <p class="font-body-sm text-body-sm text-on-surface-variant">配置 AI 模型和 API 参数，用于后续的智能环境分析。兼容 OpenAI API 格式。</p>

                <!-- Loading -->
                <div v-if="aiConfigLoading" class="flex items-center gap-3 py-6">
                  <div class="h-5 w-5 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
                  <span class="font-body-sm text-body-sm text-on-surface-variant">加载当前配置...</span>
                </div>

                <template v-if="!aiConfigLoading">
                  <!-- Enable Toggle -->
                  <div class="flex items-center justify-between p-4 rounded-lg bg-surface-container border border-outline-variant/20">
                    <div>
                      <p class="font-label-md text-label-md font-bold text-on-surface">启用 AI 功能</p>
                      <p class="font-body-sm text-body-sm text-on-surface-variant">关闭后 AI 分析将使用规则引擎替代</p>
                    </div>
                    <button @click="aiConfigForm.enabled = !aiConfigForm.enabled"
                      class="w-12 h-6 rounded-full relative transition-colors"
                      :class="aiConfigForm.enabled ? 'bg-primary' : 'bg-outline-variant'">
                      <div class="absolute top-0.5 w-5 h-5 bg-white rounded-full shadow transition-all"
                        :class="aiConfigForm.enabled ? 'right-0.5' : 'left-0.5'"></div>
                    </button>
                  </div>

                  <!-- Base URL -->
                  <div class="space-y-1.5">
                    <label class="font-label-md text-label-md font-medium text-on-surface-variant">API Base URL</label>
                    <input v-model="aiConfigForm.baseUrl"
                      class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]"
                      placeholder="https://api.openai.com/v1" />
                    <p class="text-[11px] text-outline">兼容 OpenAI API 格式的端点地址</p>
                  </div>

                  <!-- API Key -->
                  <div class="space-y-1.5">
                    <label class="font-label-md text-label-md font-medium text-on-surface-variant">API Key</label>
                    <div class="relative">
                      <input v-model="aiConfigForm.apiKey" :type="showApiKey ? 'text' : 'password'"
                        class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 pr-10 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]"
                        placeholder="sk-..." />
                      <button @click="showApiKey = !showApiKey" class="absolute right-2 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface transition-colors">
                        <span class="material-symbols-outlined text-[18px]">{{ showApiKey ? 'visibility_off' : 'visibility' }}</span>
                      </button>
                    </div>
                    <p class="text-[11px] text-outline">留空则不更新密钥。当前: {{ aiConfigForm.apiKey.includes('*') ? aiConfigForm.apiKey : '未设置' }}</p>
                  </div>

                  <!-- Model -->
                  <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1.5">
                      <label class="font-label-md text-label-md font-medium text-on-surface-variant">模型名称</label>
                      <input v-model="aiConfigForm.model"
                        class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]"
                        placeholder="gpt-4o" />
                    </div>
                    <div class="space-y-1.5">
                      <label class="font-label-md text-label-md font-medium text-on-surface-variant">Max Tokens</label>
                      <input v-model.number="aiConfigForm.maxTokens" type="number"
                        class="w-full bg-surface-container-low border border-outline-variant/50 rounded-lg px-3 py-2 text-[14px] text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-[Geist]" />
                    </div>
                  </div>

                  <!-- Temperature -->
                  <div class="space-y-1.5">
                    <div class="flex items-center justify-between">
                      <label class="font-label-md text-label-md font-medium text-on-surface-variant">Temperature</label>
                      <span class="text-[12px] font-[Geist] text-primary">{{ aiConfigForm.temperature.toFixed(1) }}</span>
                    </div>
                    <input v-model.number="aiConfigForm.temperature" type="range" min="0" max="2" step="0.1"
                      class="w-full h-2 bg-surface-container-high rounded-full appearance-none cursor-pointer accent-primary" />
                    <div class="flex justify-between text-[10px] text-outline">
                      <span>精确 (0)</span>
                      <span>平衡 (1)</span>
                      <span>创意 (2)</span>
                    </div>
                  </div>
                </template>
              </template>

              <!-- Step 5: AI Analysis & Complete -->
              <template v-if="step === 5">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">psychology</span>
                  <h2 class="font-headline-lg text-headline-lg font-semibold">AI 环境分析</h2>
                </div>

                <!-- Loading -->
                <div v-if="aiLoading" class="flex flex-col items-center gap-4 py-8">
                  <div class="relative">
                    <span class="material-symbols-outlined text-primary text-[48px] animate-pulse">auto_awesome</span>
                    <div class="absolute inset-0 bg-primary/20 blur-xl rounded-full"></div>
                  </div>
                  <p class="font-body-sm text-body-sm text-on-surface-variant">AI 正在分析服务器环境...</p>
                </div>

                <!-- AI Result -->
                <div v-if="!aiLoading && aiResult" class="space-y-4">
                  <div class="p-4 rounded-xl bg-gradient-to-br from-primary-container/5 to-secondary-container/10 border border-primary/10">
                    <div class="flex items-start gap-3 mb-3">
                      <span class="material-symbols-outlined text-primary text-[24px]">auto_awesome</span>
                      <div>
                        <h3 class="font-body-sm text-body-sm font-bold text-on-surface mb-1">AI 分析报告</h3>
                        <p class="font-body-sm text-body-sm text-on-surface-variant">
                          服务器 <strong>{{ serverName }}</strong> ({{ serverIp }}) 环境分析完成。
                        </p>
                      </div>
                    </div>
                    <div class="prose prose-sm max-w-none text-on-surface-variant bg-surface-container-low rounded-lg p-4 border border-outline-variant/20" v-html="renderMarkdown(aiResult)"></div>
                  </div>

                  <div class="grid grid-cols-3 gap-3">
                    <div class="p-3 rounded-lg bg-surface-container border border-outline-variant/20 text-center">
                      <span class="material-symbols-outlined text-green-500 text-[20px] mb-1 block">check_circle</span>
                      <p class="font-label-md text-label-md font-medium text-on-surface">Docker</p>
                      <p class="text-[10px] text-on-surface-variant">{{ scanResult?.dockerInstalled ? '已检测' : '未安装' }}</p>
                    </div>
                    <div class="p-3 rounded-lg bg-surface-container border border-outline-variant/20 text-center">
                      <span class="material-symbols-outlined text-green-500 text-[20px] mb-1 block">check_circle</span>
                      <p class="font-label-md text-label-md font-medium text-on-surface">数据库</p>
                      <p class="text-[10px] text-on-surface-variant">{{ scanResult?.databases.length ? '已连接' : '未检测' }}</p>
                    </div>
                    <div class="p-3 rounded-lg bg-surface-container border border-outline-variant/20 text-center">
                      <span class="material-symbols-outlined text-green-500 text-[20px] mb-1 block">check_circle</span>
                      <p class="font-label-md text-label-md font-medium text-on-surface">备份引擎</p>
                      <p class="text-[10px] text-on-surface-variant">就绪</p>
                    </div>
                  </div>
                </div>

                <!-- AI Error -->
                <div v-if="!aiLoading && aiError" class="rounded-lg p-4 bg-error-container text-error border border-error/20 font-body-sm text-body-sm">
                  <span class="material-symbols-outlined text-[18px] mr-1 align-middle">error</span>
                  {{ aiError }}
                </div>

                <!-- Next Steps -->
                <div v-if="!aiLoading" class="space-y-3">
                  <p class="font-label-md text-label-md font-medium text-on-surface-variant">接下来您可以：</p>
                  <div v-for="action in nextActions" :key="action.label"
                    class="flex items-start gap-4 p-4 rounded-xl bg-surface-container border border-outline-variant/20 hover:border-primary/30 transition-colors cursor-pointer"
                    @click="router.push(action.path)">
                    <div class="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                      <span class="material-symbols-outlined text-primary text-[18px]">{{ action.icon }}</span>
                    </div>
                    <div>
                      <h3 class="font-body-sm text-body-sm font-bold text-on-surface">{{ action.label }}</h3>
                      <p class="font-body-sm text-body-sm text-on-surface-variant">{{ action.desc }}</p>
                    </div>
                  </div>
                </div>
              </template>

              <!-- Actions Bar -->
              <div class="flex items-center justify-between pt-4">
                <button v-if="step > 1" @click="step--"
                  class="font-label-md text-label-md font-medium px-6 py-2.5 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-[16px]">arrow_back</span>
                  上一步
                </button>
                <button v-else @click="router.push('/dashboard')"
                  class="font-label-md text-label-md font-medium px-6 py-2.5 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">
                  取消安装
                </button>

                <!-- Step 1: Connect -->
                <button v-if="step === 1" @click="handleConnect"
                  :disabled="connecting || !serverName.trim() || !serverIp.trim() || !sshCredential.trim()"
                  class="font-label-md text-label-md font-medium px-8 py-2.5 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100">
                  {{ connecting ? '连接中...' : '测试连接并继续' }}
                </button>
                <!-- Step 2: Agent (skip or continue) -->
                <div v-else-if="step === 2" class="flex gap-3">
                  <button @click="step = 3; startScan()"
                    class="font-label-md text-label-md font-medium px-6 py-2.5 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">
                    跳过此步
                  </button>
                  <button v-if="!agentInstalling && !agentInstalled && !agentInstallError"
                    @click="installAgent"
                    class="font-label-md text-label-md font-medium px-8 py-2.5 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all">
                    安装 Agent
                  </button>
                  <button v-if="agentInstalling"
                    disabled
                    class="font-label-md text-label-md font-medium px-8 py-2.5 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 opacity-40 cursor-not-allowed">
                    安装中...
                  </button>
                  <button v-if="agentInstalled || agentInstallError"
                    @click="step = 3; startScan()"
                    class="font-label-md text-label-md font-medium px-8 py-2.5 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all">
                    继续
                  </button>
                </div>
                <!-- Step 3: Scan → go to AI config -->
                <button v-else-if="step === 3" @click="goToAiConfig"
                  :disabled="!scanDone"
                  class="font-label-md text-label-md font-medium px-8 py-2.5 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100">
                  {{ scanDone ? '配置 AI 并继续' : '扫描中...' }}
                </button>
                <!-- Step 4: Save AI config and start analysis -->
                <div v-else-if="step === 4" class="flex gap-3">
                  <button @click="skipAiToDashboard"
                    class="font-label-md text-label-md font-medium px-6 py-2.5 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">
                    跳过配置
                  </button>
                  <button @click="saveAiConfigAndAnalyze"
                    :disabled="aiConfigSaving"
                    class="font-label-md text-label-md font-medium px-8 py-2.5 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100">
                    {{ aiConfigSaving ? '保存中...' : '保存并开始分析' }}
                  </button>
                </div>
                <!-- Step 5: Done or Skip -->
                <div v-else class="flex gap-3">
                  <button @click="router.push('/dashboard')"
                    class="font-label-md text-label-md font-medium px-6 py-2.5 text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">
                    跳过分析
                  </button>
                  <button @click="router.push('/dashboard')"
                    class="font-label-md text-label-md font-medium px-8 py-2.5 bg-primary text-on-primary rounded-lg shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all">
                    完成并进入仪表盘
                  </button>
                </div>
              </div>
            </div>
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
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { serversApi } from '@/api/servers'
import { settingsApi } from '@/api/settings'
import type { EnvironmentScanResult, AiConfig } from '@/types'

const router = useRouter()
const toast = useToastStore()

const step = ref(1)
const serverName = ref('')
const serverIp = ref('')


// SSH config
const sshPort = ref(22)
const sshUsername = ref('root')
const sshAuthMethod = ref<'KEY' | 'PASSWORD'>('KEY')
const sshCredential = ref('')

// Step 2: Agent install state
const agentInstalling = ref(false)
const agentInstalled = ref(false)
const agentInstallError = ref('')
const agentInstallSteps = ref<string[]>([])
const agentApiKey = ref('')
const agentKeyCopied = ref(false)

// Connection state
const connecting = ref(false)
const connectionStatus = ref<'success' | 'error' | 'loading' | null>(null)
const connectionMessage = ref('')
const serverId = ref<number | null>(null)

// Step 3: scan state
const scanProgress = ref(0)
const scanDone = ref(false)
const scanError = ref('')
const scanId = ref(String(Math.floor(Math.random() * 900) + 100))
const scanLabel = ref('正在建立 SSH 连接...')
const scanPhase = ref('准备扫描...')
const visibleLogs = ref<{ type: string; time: string; text: string }[]>([])
const scanResult = ref<EnvironmentScanResult['data'] | null>(null)
let scanTimer: ReturnType<typeof setInterval> | null = null

// Step 4: AI config state
const aiConfigForm = ref<AiConfig>({
  enabled: true,
  baseUrl: '',
  apiKey: '',
  model: '',
  maxTokens: 4096,
  temperature: 0.7,
})
const aiConfigLoading = ref(false)
const aiConfigSaving = ref(false)
const showApiKey = ref(false)

// Step 5: AI state
const aiLoading = ref(false)
const aiResult = ref('')
const aiError = ref('')

const steps = [
  { icon: 'add_task', title: '连接服务器', desc: '配置 SSH 并测试连接' },
  { icon: 'terminal', title: '安装 Agent', desc: '可选的一键式部署脚本' },
  { icon: 'radar', title: '环境检测', desc: '扫描 Docker 与依赖' },
  { icon: 'tune', title: 'AI 配置', desc: '配置 AI 模型与 API' },
  { icon: 'psychology', title: 'AI 分析', desc: '生成智能优化方案（可选）' },
]

const nextActions = [
  { icon: 'dashboard', label: '仪表盘', desc: '查看服务器概览、存储用量和风险评分', path: '/dashboard' },
  { icon: 'photo_camera', label: '创建快照', desc: '备份服务器当前状态', path: '/snapshots' },
  { icon: 'storage', label: '配置存储', desc: '添加 S3/OSS/本地存储作为快照目标', path: '/storage' },
  { icon: 'smart_toy', label: 'AI 洞察', desc: '获取风险雷达和优化建议', path: '/ai-insights' },
]

function goToStep(target: number) {
  if (target <= step.value) {
    step.value = target
  }
}

async function handleConnect() {
  if (!serverName.value.trim() || !serverIp.value.trim() || !sshCredential.value.trim()) return

  connecting.value = true
  connectionStatus.value = 'loading'
  connectionMessage.value = '正在创建服务器并配置 SSH...'

  try {
    // 1. Create server
    const server = await serversApi.create({
      name: serverName.value.trim(),
      ip: serverIp.value.trim(),
    })
    serverId.value = server.id

    // 2. Configure SSH
    connectionMessage.value = '正在配置 SSH 凭据...'
    await serversApi.updateSshConfig(server.id, {
      port: sshPort.value || 22,
      username: sshUsername.value || 'root',
      authMethod: sshAuthMethod.value,
      credential: sshCredential.value,
    })

    // 3. Test connection
    connectionMessage.value = '正在测试 SSH 连接...'
    const result = await serversApi.testConnection(server.id)

    if (result.success) {
      connectionStatus.value = 'success'
      connectionMessage.value = `连接成功！${result.osInfo ? ' 系统: ' + result.osInfo : ''}`
      toast.success('服务器连接成功')
      // Auto advance after a short delay
      setTimeout(() => { step.value = 2 }, 1200)
    } else {
      connectionStatus.value = 'error'
      connectionMessage.value = result.message || '连接失败'
    }
  } catch (e: any) {
    connectionStatus.value = 'error'
    connectionMessage.value = e?.response?.data?.message || e?.message || '操作失败，请检查输入信息'
  } finally {
    connecting.value = false
  }
}

async function installAgent() {
  if (!serverId.value) return
  agentInstalling.value = true
  agentInstalled.value = false
  agentInstallError.value = ''
  agentInstallSteps.value = ['正在连接服务器...']
  agentApiKey.value = ''

  try {
    const result = await serversApi.installAgent(serverId.value)
    if (result.success) {
      agentInstalled.value = true
      agentApiKey.value = result.apiKey || ''
      agentInstallSteps.value = result.steps || ['安装完成']
    } else {
      agentInstallError.value = result.message || '安装失败'
    }
  } catch (e: any) {
    agentInstallError.value = e?.response?.data?.message || e?.message || 'Agent 安装失败，请检查 SSH 连接'
  } finally {
    agentInstalling.value = false
  }
}

function copyAgentKey() {
  if (agentApiKey.value) {
    navigator.clipboard.writeText(agentApiKey.value)
    agentKeyCopied.value = true
    toast.success('API 密钥已复制到剪贴板')
    setTimeout(() => { agentKeyCopied.value = false }, 2000)
  }
}

function startScan() {
  if (!serverId.value) {
    scanError.value = '未找到服务器 ID，请返回第一步重新连接'
    scanDone.value = true
    return
  }

  scanProgress.value = 0
  scanDone.value = false
  scanError.value = ''
  scanResult.value = null
  visibleLogs.value = []

  const scanLogs = [
    { type: 'INFO', text: 'Establishing SSH connection...' },
    { type: 'SCAN', text: 'Detecting operating system...' },
    { type: 'INFO', text: 'Checking disk and memory usage...' },
    { type: 'SCAN', text: 'Scanning Docker environment...' },
    { type: 'SCAN', text: 'Analyzing running containers...' },
    { type: 'INFO', text: 'Checking database ports (3306/5432/6379/27017)...' },
    { type: 'AI', text: 'Compiling environment report...' },
    { type: 'SCAN', text: 'Environment scan completed successfully' },
  ]
  let logIndex = 0

  // Start the actual API call in parallel
  const scanPromise = serversApi.scanEnvironment(serverId.value)

  // Animate progress while waiting
  scanTimer = setInterval(() => {
    const now = new Date()
    const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`

    scanProgress.value += Math.floor(Math.random() * 3) + 1
    if (scanProgress.value > 90) scanProgress.value = 90 // Cap at 90 until real result

    if (scanProgress.value < 30) {
      scanLabel.value = '正在建立 SSH 连接...'
      scanPhase.value = '连接中...'
    } else if (scanProgress.value < 60) {
      scanLabel.value = '正在扫描系统环境...'
      scanPhase.value = '扫描中...'
    } else {
      scanLabel.value = '正在分析 Docker 与数据库...'
      scanPhase.value = '深度分析中...'
    }

    const expectedLogCount = Math.floor(scanProgress.value / 12) + 1
    while (logIndex < expectedLogCount && logIndex < scanLogs.length) {
      visibleLogs.value.push({ ...scanLogs[logIndex], time: timeStr })
      logIndex++
    }
  }, 300)

  // Handle the actual result
  scanPromise.then((res) => {
    if (scanTimer) clearInterval(scanTimer)
    scanProgress.value = 100
    scanDone.value = true

    if (res.success && res.data) {
      scanResult.value = res.data
      scanLabel.value = '扫描完成'
      scanPhase.value = '扫描完成'
    } else {
      scanError.value = res.message || '扫描失败'
    }
  }).catch((e) => {
    if (scanTimer) clearInterval(scanTimer)
    scanProgress.value = 100
    scanDone.value = true
    scanError.value = e?.response?.data?.message || e?.message || '环境扫描失败'
  })
}

async function goToAiConfig() {
  step.value = 4
  aiConfigLoading.value = true
  try {
    const config = await settingsApi.getAiConfig()
    if (config) {
      aiConfigForm.value = { ...aiConfigForm.value, ...config }
    }
  } catch (e) {
    console.error('Failed to load AI config', e)
  } finally {
    aiConfigLoading.value = false
  }
}

function skipAiToDashboard() {
  router.push('/dashboard')
}

async function saveAiConfigAndAnalyze() {
  aiConfigSaving.value = true
  try {
    await settingsApi.updateAiConfig(aiConfigForm.value)
    toast.success('AI 配置已保存')
    // Proceed to AI analysis
    handleAiAnalyze()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '保存失败')
  } finally {
    aiConfigSaving.value = false
  }
}

function handleAiAnalyze() {
  if (!serverId.value) return
  step.value = 5
  aiLoading.value = true
  aiResult.value = ''
  aiError.value = ''

  serversApi.aiAnalyze(serverId.value).then((res) => {
    aiResult.value = res.analysis || '分析完成，但未返回详细结果。'
  }).catch((e) => {
    aiError.value = e?.response?.data?.message || e?.message || 'AI 分析失败'
  }).finally(() => {
    aiLoading.value = false
  })
}

function formatOs(raw: string): string {
  if (!raw) return '未知'
  // Extract kernel version and distro hints
  // e.g. "Linux hostname 5.15.0-173-generic #183-Ubuntu SMP ... x86_64 GNU/Linux"
  const kernelMatch = raw.match(/(\d+\.\d+\.\d+[-\w]*)/)
  const kernel = kernelMatch ? kernelMatch[1] : ''
  const isUbuntu = raw.toLowerCase().includes('ubuntu')
  const isDebian = raw.toLowerCase().includes('debian')
  const isCentos = raw.toLowerCase().includes('centos')
  const isRocky = raw.toLowerCase().includes('rocky')
  const arch = raw.includes('x86_64') ? 'x86_64' : raw.includes('aarch64') ? 'aarch64' : ''
  let distro = ''
  if (isUbuntu) distro = 'Ubuntu'
  else if (isDebian) distro = 'Debian'
  else if (isCentos) distro = 'CentOS'
  else if (isRocky) distro = 'Rocky Linux'
  else distro = 'Linux'
  return `${distro} ${kernel} ${arch}`.trim()
}

function formatUptime(raw: string): string {
  if (!raw) return '未知'
  // e.g. "23:09:21 up 18 days,  2:20,  1 user, load average: 0.31, 0.32, 0.35"
  const upMatch = raw.match(/up\s+(.+?),\s+\d+\s+user/)
  if (upMatch) return upMatch[1].trim()
  // fallback: just return the raw up to "user"
  const idx = raw.indexOf(' user')
  if (idx > 0) return raw.substring(0, idx).replace(/^\d+:\d+:\d+\s+up\s+/, '').trim()
  return raw
}

function formatDisk(raw: string): string {
  if (!raw) return '未知'
  // Parse df output, get the root filesystem line
  const lines = raw.split('\n').filter(l => l.trim())
  // Find the line with "/" that's not just "/"
  const rootLine = lines.find(l => {
    const parts = l.trim().split(/\s+/)
    return parts.length >= 6 && parts[5] === '/'
  })
  if (!rootLine) return raw.split('\n').pop() || raw
  const parts = rootLine.trim().split(/\s+/)
  // Format: Size Used Avail Use%
  return `${parts[1]} 总容量 | ${parts[2]} 已用 (${parts[4]}) | ${parts[3]} 可用`
}

function formatMemory(raw: string): string {
  if (!raw) return '未知'
  // Parse free output
  // "total        used        free      shared  buff/cache   available\nMem:           3.8Gi       2.3Gi       116Mi ..."
  const lines = raw.split('\n').filter(l => l.trim())
  const memLine = lines.find(l => l.startsWith('Mem:'))
  if (!memLine) return raw.split('\n').pop() || raw
  const parts = memLine.trim().split(/\s+/)
  // parts: Mem: total used free shared buff/cache available
  if (parts.length >= 7) {
    return `${parts[1]} 总内存 | ${parts[2]} 已用 | ${parts[6]} 可用`
  }
  if (parts.length >= 4) {
    return `${parts[1]} 总内存 | ${parts[2]} 已用 | ${parts[3]} 可用`
  }
  return memLine.replace('Mem:', '').trim()
}

function renderMarkdown(text: string): string {
  // Simple markdown to HTML
  return text
    .replace(/^### (.+)$/gm, '<h3 class="font-semibold text-on-surface mt-3 mb-1">$1</h3>')
    .replace(/^## (.+)$/gm, '<h2 class="font-bold text-on-surface mt-4 mb-2">$1</h2>')
    .replace(/^# (.+)$/gm, '<h1 class="font-bold text-on-surface mt-4 mb-2 text-lg">$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^- (.+)$/gm, '<li class="ml-4 list-disc">$1</li>')
    .replace(/^(\d+)\. (.+)$/gm, '<li class="ml-4 list-decimal">$2</li>')
    .replace(/\n\n/g, '<br/>')
    .replace(/\n/g, '<br/>')
}

onUnmounted(() => {
  if (scanTimer) clearInterval(scanTimer)
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.terminal-glow {
  box-shadow: inset 0 0 20px rgba(0, 88, 190, 0.1);
}
.progress-flow {
  background-size: 30px 30px;
  background-image: linear-gradient(135deg, rgba(255, 255, 255, .15) 25%, transparent 25%, transparent 50%, rgba(255, 255, 255, .15) 50%, rgba(255, 255, 255, .15) 75%, transparent 75%, transparent);
  animation: progress-move 1s linear infinite;
}
@keyframes progress-move {
  from { background-position: 0 0; }
  to { background-position: 30px 0; }
}
</style>
