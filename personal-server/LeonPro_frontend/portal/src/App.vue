<script setup lang="ts">
import TopBar from '@/components/TopBar.vue'
</script>

<template>
  <TopBar>
    <!-- 右侧预留插槽：暂时为空 -->
    <template #right />
  </TopBar>
  <main class="app-main">
    <RouterView />
  </main>
</template>

<style scoped>
.app-main {
  min-height: calc(100vh - var(--topbar-h));
}
</style>
