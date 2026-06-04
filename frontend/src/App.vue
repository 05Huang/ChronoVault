<template>
  <ErrorBoundary>
    <router-view />
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
