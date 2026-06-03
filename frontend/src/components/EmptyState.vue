<template>
  <div class="flex flex-col items-center justify-center py-16 px-4 text-center">
    <div class="w-16 h-16 rounded-full bg-primary-container/20 flex items-center justify-center mb-4">
      <span class="material-symbols-outlined text-3xl text-primary/60">{{ icon }}</span>
    </div>
    <h3 class="text-lg font-medium text-on-surface mb-1">{{ title }}</h3>
    <p class="text-sm text-on-surface-variant max-w-sm mb-6">{{ description }}</p>
    <slot name="action" />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  icon?: string
  title?: string
  description?: string
}>(), {
  icon: 'inbox',
  title: '暂无数据',
  description: '',
})
</script>