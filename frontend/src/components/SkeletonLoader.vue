<template>
  <div class="animate-pulse space-y-3">
    <div v-if="type === 'card'" class="bg-surface-container rounded-2xl p-6 space-y-4">
      <div class="h-4 bg-outline-variant/30 rounded-full w-1/3"></div>
      <div class="space-y-2">
        <div class="h-3 bg-outline-variant/30 rounded-full w-full"></div>
        <div class="h-3 bg-outline-variant/30 rounded-full w-5/6"></div>
      </div>
      <div v-if="lines > 2" class="space-y-2">
        <div v-for="i in lines - 2" :key="i" class="h-3 bg-outline-variant/20 rounded-full"
          :style="{ width: (100 - i * 15) + '%' }"></div>
      </div>
    </div>
    <div v-else-if="type === 'table'" class="bg-surface-container rounded-2xl overflow-hidden">
      <div v-for="i in rows" :key="i"
        class="flex items-center gap-4 px-6 py-4"
        :class="i < rows ? 'border-b border-outline-variant/20' : ''">
        <div class="h-4 bg-outline-variant/30 rounded-full" :style="{ width: colWidths[0] }"></div>
        <div class="h-4 bg-outline-variant/20 rounded-full flex-1" :style="{ width: colWidths[1] }"></div>
        <div class="h-4 bg-outline-variant/20 rounded-full" :style="{ width: colWidths[2] }"></div>
      </div>
    </div>
    <div v-else class="space-y-3">
      <div v-for="i in lines" :key="i" class="h-4 bg-outline-variant/30 rounded-full"
        :style="{ width: (100 - (i % 3) * 20) + '%' }"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  type?: 'card' | 'table' | 'lines'
  lines?: number
  rows?: number
  colWidths?: string[]
}>(), {
  type: 'lines',
  lines: 4,
  rows: 5,
  colWidths: ['25%', '50%', '20%'],
})
</script>