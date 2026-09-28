<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { PortalModule } from '@/modules'

const props = defineProps<{ module: PortalModule }>()

const cover = ref<string>()
const loading = ref(true)
const imgLoaded = ref(false)

onMounted(async () => {
  try {
    cover.value = await props.module.getCover()
  } catch {
    cover.value = undefined // 封面失败不影响卡片可用
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <RouterLink :to="module.route" class="card">
    <div class="card__cover" :class="{ 'is-loading': loading }">
      <img
        v-if="cover"
        :src="cover"
        :alt="module.name"
        class="fade-img"
        :class="{ 'is-loaded': imgLoaded }"
        @load="imgLoaded = true"
        @error="cover = undefined"
      />
      <span v-else-if="!loading" class="card__icon" aria-hidden="true">{{ module.icon }}</span>
    </div>
    <div class="card__body">
      <h2 class="card__name">{{ module.name }}</h2>
      <p class="card__desc">{{ module.description }}</p>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: block;
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.card:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.card__cover {
  position: relative;
  aspect-ratio: 16 / 9;
  background: var(--placeholder);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.card__cover.is-loading {
  background: linear-gradient(100deg, #ececf0 30%, #f6f6f8 50%, #ececf0 70%);
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
}
.card__cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card__icon {
  font-size: 44px;
  opacity: 0.6;
}
.card__body {
  padding: 14px 16px 16px;
}
.card__name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.card__desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}
</style>
