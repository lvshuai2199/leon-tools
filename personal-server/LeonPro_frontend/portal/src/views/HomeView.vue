<script setup lang="ts">
import { MODULES } from '@/modules'
import ModuleCard from '@/components/ModuleCard.vue'
</script>

<template>
  <div class="home container">
    <div class="home__grid">
      <ModuleCard v-for="m in MODULES" :key="m.key" :module="m" />
    </div>
  </div>
</template>

<style scoped>
.home {
  padding-top: 32px;
  padding-bottom: 48px;
}
.home__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}
@media (min-width: 640px) {
  .home__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 24px;
  }
}
@media (max-width: 639px) {
  .home {
    padding-top: 16px;
  }
}
</style>
