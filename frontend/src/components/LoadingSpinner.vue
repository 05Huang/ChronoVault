<template>
  <div class="flex items-center justify-center" :class="containerClass">
    <div class="relative">
      <div class="w-8 h-8 border-3 border-outline-variant/30 rounded-full"
        :class="{ 'border-t-primary animate-spin': spinning }"></div>
    </div>
    <span v-if="label" class="ml-3 text-sm text-on-surface-variant">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  spinning?: boolean
  label?: string
  containerClass?: string
}>(), {
  spinning: true,
  label: '',
  containerClass: 'py-8',
})
</script>