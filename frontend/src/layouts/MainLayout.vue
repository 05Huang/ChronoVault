<template>
  <div class="flex min-h-screen overflow-hidden">
    <SideNavBar />
    <main class="flex-1 flex flex-col min-w-0 overflow-hidden md:ml-60">
      <TopNavBar />
      <div class="flex-1 overflow-y-auto">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import SideNavBar from '@/components/SideNavBar.vue'
import TopNavBar from '@/components/TopNavBar.vue'
</script>
