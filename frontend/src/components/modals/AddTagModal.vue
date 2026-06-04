<template>
  <div class="p-6 space-y-4">
    <div v-if="existingTags.length" class="space-y-2">
      <p class="text-[12px] font-bold text-on-surface-variant uppercase tracking-wider">现有标签</p>
      <div class="flex flex-wrap gap-2">
        <span
          v-for="tag in existingTags"
          :key="tag.id"
          class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[12px] font-medium"
          :style="{ backgroundColor: tag.color + '20', color: tag.color, border: '1px solid ' + tag.color + '40' }"
        >
          {{ tag.name }}
        </span>
      </div>
    </div>

    <div class="space-y-3">
      <div>
        <label class="block text-[12px] font-bold text-on-surface-variant uppercase tracking-wider mb-1.5">标签名称</label>
        <input
          v-model="tagName"
          type="text"
          placeholder="例如: production, hotfix, v2.0"
          maxlength="50"
          class="w-full px-4 py-2.5 bg-surface-container-high border border-outline-variant/30 rounded-xl text-[14px] text-on-surface placeholder:text-outline focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/30 transition-colors"
          @keyup.enter="handleAdd"
        />
      </div>

      <div>
        <label class="block text-[12px] font-bold text-on-surface-variant uppercase tracking-wider mb-1.5">标签颜色</label>
        <div class="flex items-center gap-3">
          <div class="flex gap-1.5">
            <button
              v-for="color in presetColors"
              :key="color"
              @click="selectedColor = color"
              class="w-7 h-7 rounded-full border-2 transition-all hover:scale-110"
              :class="selectedColor === color ? 'border-on-surface ring-2 ring-on-surface/20 scale-110' : 'border-transparent'"
              :style="{ backgroundColor: color }"
            />
          </div>
          <input
            v-model="selectedColor"
            type="color"
            class="w-8 h-8 rounded-lg cursor-pointer border border-outline-variant/30"
          />
          <span class="text-[12px] text-outline">{{ selectedColor }}</span>
        </div>
      </div>
    </div>

    <div v-if="error" class="text-[12px] text-error bg-error/10 px-3 py-2 rounded-lg">{{ error }}</div>

    <div class="flex justify-end gap-3 pt-2">
      <button @click="$emit('close')" class="px-4 py-2 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high rounded-lg transition-colors">取消</button>
      <button
        @click="handleAdd"
        :disabled="loading || !tagName.trim()"
        class="px-4 py-2 text-[12px] font-bold text-white bg-primary hover:bg-primary-container rounded-lg transition-all disabled:opacity-60 flex items-center gap-2"
      >
        <span v-if="loading" class="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
        {{ loading ? '添加中...' : '添加标签' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToastStore } from '@/stores/toast'
import { snapshotsApi } from '@/api/snapshots'
import type { SnapshotTag } from '@/types'

const props = defineProps<{
  snapshotId: number
  existingTags: SnapshotTag[]
  onAdded?: () => void
}>()

const emit = defineEmits<{ close: [] }>()
const toast = useToastStore()

const tagName = ref('')
const selectedColor = ref('#0058be')
const loading = ref(false)
const error = ref('')

const presetColors = [
  '#0058be', // primary blue
  '#1a6b37', // green
  '#924700', // amber
  '#ba1a1a', // red
  '#6750a4', // purple
  '#006a6a', // teal
  '#7c5800', // brown
  '#4a5568', // gray
]

async function handleAdd() {
  const name = tagName.value.trim()
  if (!name) {
    error.value = '请输入标签名称'
    return
  }

  if (props.existingTags.some(t => t.name === name)) {
    error.value = '该标签已存在'
    return
  }

  loading.value = true
  error.value = ''
  try {
    await snapshotsApi.addTag(props.snapshotId, { name, color: selectedColor.value })
    toast.success(`标签 "${name}" 已添加`)
    props.onAdded?.()
    emit('close')
  } catch (e: unknown) {
    error.value = (e instanceof Error ? e.message : null) || '添加标签失败'
  } finally {
    loading.value = false
  }
}
</script>
