<script setup lang="ts">
/**
 * 壁纸缩略图格子（自写，不用组件库）
 * - mobile：两列，间距 8
 * - desktop：自适应列（最小 220px），间距 16
 * 竖图防止头顶被裁：
 * - 分组里竖图超过一半：格子改 3:4（grid--portrait）
 * - 混排分组：竖图裁切焦点 50% 10%（人物头部通常靠上），横图居中
 * 竖图判定优先用接口返回的宽高，没有时用加载后的 naturalWidth/naturalHeight
 * 格子标题：电脑悬停显示；手机不显示（没有悬停，标题在大图里看）
 */
import { computed, reactive } from 'vue'
import type { WallpaperImage } from '@/api/types'
import { vLazy } from '@/directives/lazy'

const props = withDefaults(defineProps<{ images: WallpaperImage[]; skeleton?: number; variant?: 'mobile' | 'desktop' }>(), {
  skeleton: 0,
  variant: 'desktop',
})
defineEmits<{ open: [index: number] }>()

/**
 * 用行内 style 而不是 class：v-lazy 会给 img 追加 is-loaded 类，
 * 若 :class 在加载后变化，Vue 重写 className 会把 is-loaded 冲掉导致图片变透明。
 */
const PORTRAIT_STYLE = { objectPosition: '50% 10%' }

/** 接口未返回宽高时，根据已加载图片的 naturalWidth/naturalHeight 判定竖图 */
const detectedPortrait = reactive(new Set<string>())

function isPortrait(img: WallpaperImage): boolean {
  if (img.width > 0 && img.height > 0) return img.height > img.width
  return detectedPortrait.has(img.id)
}

/** 竖图超过一半的分组，格子改成 3:4 */
const mostlyPortrait = computed(() => {
  const list = props.images
  if (!list.length) return false
  return list.filter(isPortrait).length * 2 > list.length
})

function onThumbLoad(img: WallpaperImage, e: Event) {
  if (img.width > 0 && img.height > 0) return
  const el = e.target as HTMLImageElement
  if (el.naturalHeight > el.naturalWidth) detectedPortrait.add(img.id)
}
</script>

<template>
  <ul class="grid" :class="[`grid--${variant}`, { 'grid--portrait': mostlyPortrait }]">
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
        <span v-if="img.title && variant === 'desktop'" class="tile__title">{{ img.title }}</span>
      </button>
    </li>
    <li v-for="n in skeleton" :key="`sk-${n}`" class="cell" aria-hidden="true">
      <div class="tile lp-skeleton" />
    </li>
  </ul>
</template>

<style scoped lang="scss">
.grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
}
.grid--mobile {
  gap: lp.$thumb-gap-mobile;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.grid--desktop {
  gap: lp.$thumb-gap-desktop;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}
.grid--desktop.grid--portrait {
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
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
  border-radius: lp.$radius-base;
  overflow: hidden;
  background: var(--el-fill-color);
  cursor: zoom-in;
  img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: opacity 0.35s ease, transform 0.5s ease;
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}
.grid--desktop .tile:hover img {
  transform: scale(1.03);
}
.grid--portrait .tile {
  aspect-ratio: 3 / 4;
}
div.tile {
  cursor: default;
}
.tile__title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: lp.$space-5 10px lp.$space-2;
  // 标题压在图片上的渐变遮罩（shared 主题变量）
  background: var(--lp-overlay-gradient);
  color: var(--el-color-white);
  font-size: lp.$font-size-extra-small;
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
</style>
