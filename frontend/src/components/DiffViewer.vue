<script setup lang="ts">
import { computed } from 'vue'

interface DiffLine {
  type: 'added' | 'removed' | 'context' | 'separator'
  content: string
  oldLineNum?: number
  newLineNum?: number
}

const props = defineProps<{
  oldContent: string
  newContent: string
  filePath?: string
}>()

const emit = defineEmits<{
  close: []
}>()

/**
 * Simple diff algorithm (myers-like) that produces line-by-line comparison.
 * Good enough for config file diffs without external dependencies.
 */
const diffLines = computed<DiffLine[]>(() => {
  const oldLines = props.oldContent.split('\n')
  const newLines = props.newContent.split('\n')
  const result: DiffLine[] = []

  // Simple LCS-based diff
  const lcs = computeLCS(oldLines, newLines)
  let oldIdx = 0
  let newIdx = 0
  let lcsIdx = 0
  let oldLineNum = 1
  let newLineNum = 1

  while (oldIdx < oldLines.length || newIdx < newLines.length) {
    if (lcsIdx < lcs.length) {
      // Output removed lines (in old but not in LCS)
      while (oldIdx < oldLines.length && oldLines[oldIdx] !== lcs[lcsIdx]) {
        result.push({
          type: 'removed',
          content: oldLines[oldIdx],
          oldLineNum: oldLineNum++,
        })
        oldIdx++
      }
      // Output added lines (in new but not in LCS)
      while (newIdx < newLines.length && newLines[newIdx] !== lcs[lcsIdx]) {
        result.push({
          type: 'added',
          content: newLines[newIdx],
          newLineNum: newLineNum++,
        })
        newIdx++
      }
      // Output common line
      if (lcsIdx < lcs.length) {
        result.push({
          type: 'context',
          content: lcs[lcsIdx],
          oldLineNum: oldLineNum++,
          newLineNum: newLineNum++,
        })
        oldIdx++
        newIdx++
        lcsIdx++
      }
    } else {
      // Remaining lines
      while (oldIdx < oldLines.length) {
        result.push({
          type: 'removed',
          content: oldLines[oldIdx],
          oldLineNum: oldLineNum++,
        })
        oldIdx++
      }
      while (newIdx < newLines.length) {
        result.push({
          type: 'added',
          content: newLines[newIdx],
          newLineNum: newLineNum++,
        })
        newIdx++
      }
    }
  }

  return result
})

function computeLCS(a: string[], b: string[]): string[] {
  const m = a.length
  const n = b.length
  const dp: number[][] = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0))

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (a[i - 1] === b[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1])
      }
    }
  }

  // Backtrack to find the LCS
  const lcs: string[] = []
  let i = m, j = n
  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) {
      lcs.unshift(a[i - 1])
      i--
      j--
    } else if (dp[i - 1][j] > dp[i][j - 1]) {
      i--
    } else {
      j--
    }
  }
  return lcs
}

const addedCount = computed(() => diffLines.value.filter(l => l.type === 'added').length)
const removedCount = computed(() => diffLines.value.filter(l => l.type === 'removed').length)
</script>

<template>
  <div class="rounded-lg border border-gray-200 overflow-hidden bg-white">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-2 bg-gray-50 border-b border-gray-200">
      <div class="flex items-center gap-2">
        <span v-if="filePath" class="text-sm font-mono text-gray-700">{{ filePath }}</span>
        <span class="text-xs text-gray-500">
          <span class="text-green-600">+{{ addedCount }}</span>
          <span class="text-red-600 ml-1">-{{ removedCount }}</span>
        </span>
      </div>
      <button @click="emit('close')" class="text-gray-400 hover:text-gray-600 text-sm">✕</button>
    </div>

    <!-- Diff content -->
    <div class="overflow-x-auto text-sm font-mono">
      <table class="w-full border-collapse">
        <tbody>
          <tr
            v-for="(line, idx) in diffLines"
            :key="idx"
            class="border-b border-gray-100"
            :class="{
              'bg-green-50': line.type === 'added',
              'bg-red-50': line.type === 'removed',
              'bg-white': line.type === 'context',
            }"
          >
            <!-- Line numbers -->
            <td class="w-12 text-right pr-2 py-0.5 select-none text-gray-400 text-xs">
              {{ line.oldLineNum ?? '' }}
            </td>
            <td class="w-12 text-right pr-2 py-0.5 select-none text-gray-400 text-xs">
              {{ line.newLineNum ?? '' }}
            </td>
            <!-- Prefix -->
            <td class="w-6 text-center py-0.5 select-none font-bold"
              :class="{
                'text-green-600': line.type === 'added',
                'text-red-600': line.type === 'removed',
                'text-gray-300': line.type === 'context',
              }"
            >
              {{ line.type === 'added' ? '+' : line.type === 'removed' ? '-' : ' ' }}
            </td>
            <!-- Content -->
            <td class="py-0.5 pr-4 whitespace-pre">{{ line.content }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Empty state -->
    <div v-if="diffLines.length === 0" class="py-8 text-center text-gray-400 text-sm">
      无差异
    </div>
  </div>
</template>