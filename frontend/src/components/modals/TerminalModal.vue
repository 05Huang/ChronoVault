<template>
  <div class="flex flex-col h-[70vh]">
    <!-- Terminal Header -->
    <div class="flex items-center justify-between px-4 py-2 bg-surface-container-high border-b border-outline-variant/20 rounded-t-lg">
      <div class="flex items-center gap-2">
        <span class="material-symbols-outlined text-primary text-[18px]">terminal</span>
        <span class="text-[13px] font-bold text-on-surface">{{ serverName || 'SSH Terminal' }}</span>
        <span v-if="connected" class="flex items-center gap-1 text-[10px] text-secondary font-bold">
          <span class="w-1.5 h-1.5 rounded-full bg-secondary"></span> 已连接
        </span>
        <span v-else class="flex items-center gap-1 text-[10px] text-error font-bold">
          <span class="w-1.5 h-1.5 rounded-full bg-error"></span> 未连接
        </span>
      </div>
      <div class="flex items-center gap-2">
        <button @click="clearTerminal" class="p-1 rounded hover:bg-surface-container-highest transition-colors" title="清屏">
          <span class="material-symbols-outlined text-[16px] text-on-surface-variant">delete_sweep</span>
        </button>
      </div>
    </div>

    <!-- Terminal Container -->
    <div ref="terminalRef" class="flex-1 bg-[#0d1117] p-1 overflow-hidden"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import '@xterm/xterm/css/xterm.css'
import apiClient from '@/api/client'

const props = defineProps<{
  serverId: number
  serverName?: string
}>()

const emit = defineEmits<{
  close: []
}>()

const terminalRef = ref<HTMLElement>()
const connected = ref(false)

let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let sessionId: string | null = null
let inputBuffer = ''
let executing = false

const TERM_THEME = {
  background: '#0d1117',
  foreground: '#e6edf3',
  cursor: '#58a6ff',
  cursorAccent: '#0d1117',
  selectionBackground: '#264f78',
  black: '#484f58',
  red: '#ff7b72',
  green: '#3fb950',
  yellow: '#d29922',
  blue: '#58a6ff',
  magenta: '#bc8cff',
  cyan: '#39c5cf',
  white: '#b1bac4',
}

function writePrompt() {
  if (!terminal) return
  terminal.write('\r\n\x1b[36m$\x1b[0m ')
}

async function initTerminal() {
  if (!terminalRef.value) return

  terminal = new Terminal({
    theme: TERM_THEME,
    fontFamily: 'JetBrains Mono, Fira Code, Consolas, monospace',
    fontSize: 13,
    lineHeight: 1.4,
    cursorBlink: true,
    cursorStyle: 'bar',
    scrollback: 5000,
    allowTransparency: true,
  })

  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.loadAddon(new WebLinksAddon())

  terminal.open(terminalRef.value)
  await nextTick()
  fitAddon.fit()

  terminal.writeln('\x1b[33m正在连接到服务器...\x1b[0m')

  try {
    const res = await apiClient.post<{ sessionId?: string }>(`/terminal/sessions?serverId=${props.serverId}`)
    sessionId = res?.data?.sessionId
    connected.value = true
    terminal.writeln(`\x1b[32m已连接到 ${props.serverName || '服务器'}\x1b[0m`)
    terminal.writeln('\x1b[90m输入命令并按 Enter 执行，支持所有 Shell 命令\x1b[0m')
    writePrompt()

    // Handle keyboard input directly in terminal
    terminal.onKey(({ key, domEvent }) => {
      if (!connected.value || executing) return

      const ev = domEvent
      const printable = !ev.altKey && !ev.ctrlKey && !ev.metaKey

      if (ev.key === 'Enter') {
        const cmd = inputBuffer.trim()
        inputBuffer = ''
        if (cmd) {
          executeCommand(cmd)
        } else {
          writePrompt()
        }
      } else if (ev.key === 'Backspace') {
        if (inputBuffer.length > 0) {
          inputBuffer = inputBuffer.slice(0, -1)
          terminal!.write('\b \b')
        }
      } else if (ev.ctrlKey && ev.key === 'c') {
        terminal!.write('^C')
        inputBuffer = ''
        writePrompt()
      } else if (ev.ctrlKey && ev.key === 'l') {
        terminal!.clear()
        writePrompt()
      } else if (printable) {
        inputBuffer += key
        terminal!.write(key)
      }
    })

    // Also handle paste
    terminal.onData((data) => {
      if (!connected.value || executing) return
      // Filter out control sequences that onKey already handles
      const cleanData = data.replace(/[\r\n]/g, '')
      if (cleanData && cleanData !== inputBuffer.slice(-cleanData.length)) {
        inputBuffer += cleanData
        terminal!.write(cleanData)
      }
    })

  } catch (e: unknown) {
    const msg = (e && typeof e === 'object' && 'response' in e)
      ? ((e as { response?: { data?: { message?: string } } }).response?.data?.message)
      : undefined
    terminal.writeln(`\x1b[31m连接失败: ${msg || (e instanceof Error ? e.message : '未知错误')}\x1b[0m`)
  }

  // Handle resize
  const resizeObserver = new ResizeObserver(() => {
    fitAddon?.fit()
  })
  resizeObserver.observe(terminalRef.value)
}

async function executeCommand(cmd: string) {
  if (!sessionId || executing) return

  executing = true
  terminal?.write('\r\n')

  try {
    const resp = await apiClient.post<{ error?: string; stdout?: string; stderr?: string }>(`/terminal/sessions/${sessionId}/exec`, { command: cmd })
    const data = resp?.data || {}

    if (data.error) {
      terminal?.writeln(`\x1b[31m错误: ${data.error}\x1b[0m`)
    } else {
      if (data.stdout) {
        const lines = data.stdout.split('\n')
        // Remove trailing empty line
        if (lines[lines.length - 1] === '') lines.pop()
        lines.forEach((line: string) => terminal?.writeln(line))
      }
      if (data.stderr) {
        const lines = data.stderr.split('\n')
        if (lines[lines.length - 1] === '') lines.pop()
        lines.forEach((line: string) => terminal?.writeln(`\x1b[31m${line}\x1b[0m`))
      }
    }
  } catch (e: unknown) {
    terminal?.writeln(`\x1b[31m请求失败: ${(e instanceof Error ? e.message : null) || '网络错误'}\x1b[0m`)
  } finally {
    executing = false
    writePrompt()
  }
}

function clearTerminal() {
  terminal?.clear()
  writePrompt()
}

onMounted(() => {
  initTerminal()
})

onUnmounted(() => {
  if (sessionId) {
    apiClient.delete(`/terminal/sessions/${sessionId}`).catch(() => {})
  }
  terminal?.dispose()
  terminal = null
})
</script>
