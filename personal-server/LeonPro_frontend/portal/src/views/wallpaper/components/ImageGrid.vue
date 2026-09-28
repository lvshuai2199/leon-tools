<script setup lang="ts">
import { reactive } from 'vue'
import type { WallpaperImage } from '@/api/types'
import { vLazy } from '@/directives/lazy'

withDefaults(defineProps<{ images: WallpaperImage[]; skeleton?: number }>(), { skeleton: 0 })
defineEmits<{ open: [index: number] }>()

/**
 * 竖图裁切焦点。用行内 style 而不是 class：v-lazy 会给 img 追加 is-loaded 类，
 * 若 :class 在加载后变化，Vue 重写 className 会把 is-loaded 冲掉导致图片变透明。
 */
const PORTRAIT_STYLE = { objectPosition: '50% 20%' }

/** 接口未返回宽高时，根据已加载图片的 naturalWidth/naturalHeight 判定竖图 */
const detectedPortrait = reactive(new Set<string>())

/** 竖图：裁切焦点上移到 50% 20%（人物头部通常靠上）；横图保持居中 */
function isPortrait(img: WallpaperImage): boolean {
  if (img.width > 0 && img.height > 0) return img.height > img.width
  return detectedPortrait.has(img.id)
}

function onThumbLoad(img: WallpaperImage, e: Event) {
  if (img.width > 0 && img.height > 0) return
  const el = e.target as HTMLImageElement
  if (el.naturalHeight > el.naturalWidth) detectedPortrait.add(img.id)
}
</script>

<template>
  <ul class="grid">
    <li v-for="(img, i) in images" :key="img.id" class="cell">
      <button type="button" class="tile" :aria-label="`查看 ${img.title || '壁纸'}`" @click="$emit('open', i)">
        <img
          v-lazy="img.thumbUrl"
          :alt="img.title"
          loading="lazy"
          decoding="async"
          class="fade-img"
          :style="isPortrait(img) ? PORTRAIT_STYLE : undefined"
          @load="onThumbLoad(img, $event)"
        />
        <span v-if="img.title" class="tile__title">{{ img.title }}</span>
      </button>
    </li>
    <li v-for="n in skeleton" :key="`sk-${n}`" class="cell" aria-hidden="true">
      <div class="tile tile--skeleton" />
    </li>
  </ul>
</template>

<style scoped>
.grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
@media (min-width: 640px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (min-width: 1024px) {
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
.cell {
  min-width: 0;
}
.tile {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  padding: 0;
  border: none;
  border-radius: 8px;
  overflow: hidden;
  background: var(--placeholder);
  cursor: zoom-in;
}
.tile img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: opacity 0.35s ease, transform 0.5s ease;
}
.tile:hover img {
  transform: scale(1.03);
}
.tile:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.tile__title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24px 10px 8px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.6), transparent);
  color: #fff;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transform: translateY(100%);
  transition: transform 0.25s ease;
  pointer-events: none;
}
.tile:hover .tile__title,
.tile:focus-visible .tile__title {
  transform: translateY(0);
}
.tile--skeleton {
  cursor: default;
  background: linear-gradient(100deg, #ececf0 30%, #f6f6f8 50%, #ececf0 70%);
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
}
@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}
</style>
