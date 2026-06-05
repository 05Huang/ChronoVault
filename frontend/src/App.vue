<template>
  <ErrorBoundary>
    <router-view v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </router-view>
  </ErrorBoundary>
  <ToastContainer />
  <ModalContainer />
  <TaskProgress />
</template>

<script setup lang="ts">
import ErrorBoundary from '@/components/ErrorBoundary.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import ModalContainer from '@/components/ModalContainer.vue'
import TaskProgress from '@/components/TaskProgress.vue'
</script>

<style>
/* Page transition animations */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
