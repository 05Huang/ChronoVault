<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modal.isOpen" class="fixed inset-0 z-[9998] flex items-center justify-center p-4" @click.self="modal.close()">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm"></div>
        <div class="relative w-full bg-surface-bright rounded-2xl shadow-2xl border border-outline-variant/20 overflow-hidden" :class="modal.width">
          <div v-if="modal.title" class="flex items-center justify-between px-6 py-4 border-b border-outline-variant/20">
            <h3 class="text-[18px] font-semibold text-on-surface">{{ modal.title }}</h3>
            <button @click="modal.close()" class="w-8 h-8 rounded-full hover:bg-surface-container-high flex items-center justify-center transition-colors">
              <span class="material-symbols-outlined text-[20px] text-outline">close</span>
            </button>
          </div>
          <component v-if="modal.component" :is="modal.component" v-bind="modal.props" @close="modal.close()" />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { useModalStore } from '@/stores/modal'

const modal = useModalStore()
</script>

<style scoped>
.modal-enter-active { animation: modalIn 0.25s cubic-bezier(0.22, 1, 0.36, 1); }
.modal-leave-active { animation: modalIn 0.15s cubic-bezier(0.22, 1, 0.36, 1) reverse; }

@keyframes modalIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
