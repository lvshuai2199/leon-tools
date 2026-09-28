<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { SITE_NAME } from '@/config'

const route = useRoute()
const section = computed(() => (route.name !== 'home' ? route.meta.title : undefined))
</script>

<template>
  <header class="topbar">
    <div class="topbar__inner container">
      <div class="topbar__left">
        <RouterLink to="/" class="topbar__brand">{{ SITE_NAME }}</RouterLink>
        <template v-if="section">
          <span class="topbar__sep" aria-hidden="true">/</span>
          <span class="topbar__section">{{ section }}</span>
        </template>
      </div>
      <div class="topbar__right">
        <slot name="right" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 50;
  height: var(--topbar-h);
  background: rgba(245, 245, 247, 0.8);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid var(--line);
}
.topbar__inner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.topbar__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.topbar__brand {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.topbar__sep {
  color: var(--text-3);
}
.topbar__section {
  font-size: 15px;
  color: var(--text-2);
}
.topbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
