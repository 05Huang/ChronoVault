<template>
  <router-view />
  <ToastContainer />
  <ModalContainer />
  <TaskProgress />
</template>

<script setup lang="ts">
import ToastContainer from '@/components/ToastContainer.vue'
import ModalContainer from '@/components/ModalContainer.vue'
import TaskProgress from '@/components/TaskProgress.vue'
</script>
