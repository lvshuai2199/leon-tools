<script setup lang="ts">
/**
 * 全屏查看器
 * - 左右箭头按钮 / 键盘 ←→ / Esc 关闭 / 移动端左右滑动
 * - 到达已加载末尾时，若还有下一页则通知父组件加载，并在加载完成后自动前进
 * - 打开期间锁定 body 滚动
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { WallpaperImage } from '@/api/types'
import { lockBodyScroll, unlockBodyScroll } from '@/composables/useScrollLock'

const props = defineProps<{
  images: WallpaperImage[]
  index: number
  hasMore: boolean
  loadingMore: boolean
  total?: number
}>()

const emit = defineEmits<{
  close: []
  'update:index': [index: number]
  loadMore: []
}>()

const current = computed(() => props.images[props.index])
const loaded = ref(false)
const failed = ref(false)
const pendingNext = ref(false)

const hasPrev = computed(() => props.index > 0)
const hasNext = computed(() => props.index < props.images.length - 1 || props.hasMore)
const counterTotal = computed(() => props.total || props.images.length)

const resolution = computed(() => {
  const img = current.value
  return img && img.width && img.height ? `${img.width}×${img.height}` : ''
})

/** 下载文件名：标题 + 原图扩展名 */
const downloadName = computed(() => {
  const img = current.value
  if (!img) return ''
  const ext = (img.url.split('?')[0].match(/\.(jpe?g|png|webp|gif|bmp|avif)$/i)?.[0] ?? '.jpg').toLowerCase()
  const base = (img.title || `wallpaper-${img.id}`).replace(/[\\/:*?"<>|\s]+/g, '_')
  return `${base}${ext}`
})

function go(i: number) {
  if (i >= 0 && i < props.images.length) emit('update:index', i)
}
function prev() {
  if (hasPrev.value) go(props.index - 1)
}
function next() {
  if (props.index < props.images.length - 1) {
    go(props.index + 1)
  } else if (props.hasMore) {
    pendingNext.value = true
    if (!props.loadingMore) emit('loadMore')
  }
}

// 新一页加载完成后，若用户刚才在末尾点了“下一张”，自动前进
watch(
  () => props.images.length,
  (len, old) => {
    if (pendingNext.value && len > old) {
      pendingNext.value = false
      go(props.index + 1)
    }
  },
)
watch(
  () => props.hasMore,
  (v) => {
    if (!v) pendingNext.value = false
  },
)

// 切换图片时重置加载状态，并预加载相邻图片
watch(
  () => current.value?.url,
  () => {
    loaded.value = false
    failed.value = false
    for (const i of [props.index + 1, props.index - 1]) {
      const u = props.images[i]?.url
      if (u) new Image().src = u
    }
    // 接近末尾时提前加载下一页
    if (props.hasMore && !props.loadingMore && props.index >= props.images.length - 3) emit('loadMore')
  },
  { immediate: true },
)

function onKey(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    prev()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    next()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    emit('close')
  }
}

/* ---------- 触摸滑动 ---------- */
let startX = 0
let startY = 0
let tracking = false
const dragX = ref(0)

function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1) return
  tracking = true
  startX = e.touches[0].clientX
  startY = e.touches[0].clientY
  dragX.value = 0
}
function onTouchMove(e: TouchEvent) {
  if (!tracking) return
  const dx = e.touches[0].clientX - startX
  const dy = e.touches[0].clientY - startY
  if (Math.abs(dx) > Math.abs(dy)) dragX.value = dx
}
function onTouchEnd(e: TouchEvent) {
  if (!tracking) return
  tracking = false
  const t = e.changedTouches[0]
  const dx = t.clientX - startX
  const dy = t.clientY - startY
  dragX.value = 0
  if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.2) {
    if (dx < 0) next()
    else prev()
  }
}

const root = ref<HTMLElement>()
let lastFocus: Element | null = null

onMounted(() => {
  lockBodyScroll()
  window.addEventListener('keydown', onKey)
  lastFocus = document.activeElement
  root.value?.focus()
})
onBeforeUnmount(() => {
  unlockBodyScroll()
  window.removeEventListener('keydown', onKey)
  if (lastFocus instanceof HTMLElement) lastFocus.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <div ref="root" class="viewer" role="dialog" aria-modal="true" :aria-label="current?.title || '图片查看'" tabindex="-1">
      <div class="viewer__top">
        <span class="viewer__counter">{{ index + 1 }} / {{ counterTotal }}</span>
        <button type="button" class="viewer__close" aria-label="关闭（Esc）" @click="emit('close')">
          <svg viewBox="0 0 24 24" width="22" height="22"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
        </button>
      </div>

      <div
        class="viewer__stage"
        @click.self="emit('close')"
        @touchstart.passive="onTouchStart"
        @touchmove.passive="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
      >
        <template v-if="current">
          <!-- 缩略图垫底，原图加载完成后覆盖 -->
          <img
            v-if="!loaded && current.thumbUrl"
            :key="`t-${current.id}`"
            :src="current.thumbUrl"
            alt=""
            class="viewer__img viewer__img--thumb"
            :style="{ transform: `translateX(${dragX}px)` }"
          />
          <img
            :key="current.id"
            :src="current.url"
            :alt="current.title"
            class="viewer__img"
            :class="{ 'is-loaded': loaded }"
            :style="{ transform: `translateX(${dragX}px)` }"
            @load="loaded = true"
            @error="failed = true"
          />
          <span v-if="!loaded && !failed" class="viewer__spinner" aria-hidden="true" />
          <p v-if="failed" class="viewer__error">原图加载失败</p>
        </template>
        <span v-else-if="loadingMore" class="viewer__spinner" aria-hidden="true" />

        <button v-if="hasPrev" type="button" class="viewer__nav viewer__nav--prev" aria-label="上一张（←）" @click.stop="prev">
          <svg viewBox="0 0 24 24" width="28" height="28"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <button
          v-if="hasNext"
          type="button"
          class="viewer__nav viewer__nav--next"
          :class="{ 'is-busy': pendingNext && loadingMore }"
          aria-label="下一张（→）"
          @click.stop="next"
        >
          <svg viewBox="0 0 24 24" width="28" height="28"><path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
      </div>

      <div v-if="current" class="viewer__bar">
        <div class="viewer__info">
          <p class="viewer__title">{{ current.title || '未命名' }}</p>
          <p v-if="resolution" class="viewer__meta">{{ resolution }}</p>
        </div>
        <a class="viewer__download" :href="current.url" :download="downloadName" target="_blank" rel="noopener">
          <svg viewBox="0 0 24 24" width="16" height="16"><path d="M12 4v11m0 0l-4.5-4.5M12 15l4.5-4.5M5 19h14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
          下载原图
        </a>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.viewer {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  background: #111;
  color: #f5f5f7;
  outline: none;
  animation: viewer-in 0.2s ease;
  touch-action: pan-y;
}
@keyframes viewer-in {
  from {
    opacity: 0;
  }
}
.viewer__top {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 12px 0 20px;
  padding-top: env(safe-area-inset-top);
}
.viewer__counter {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  font-variant-numeric: tabular-nums;
}
.viewer__close {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  cursor: pointer;
}
.viewer__close:hover {
  background: rgba(255, 255, 255, 0.1);
}
.viewer__stage {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 72px;
  user-select: none;
}
.viewer__img {
  position: absolute;
  max-width: calc(100% - 144px);
  max-height: 100%;
  object-fit: contain;
  opacity: 0;
  transition: opacity 0.3s ease;
  -webkit-user-drag: none;
}
.viewer__img.is-loaded,
.viewer__img--thumb {
  opacity: 1;
}
.viewer__img--thumb {
  width: calc(100% - 144px);
  aspect-ratio: 16 / 9;
  max-height: 100%;
  object-fit: contain;
  filter: blur(8px);
}
.viewer__spinner {
  position: absolute;
  width: 32px;
  height: 32px;
  border: 2.5px solid rgba(255, 255, 255, 0.2);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.viewer__error {
  position: absolute;
  margin: 0;
  padding: 8px 14px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.6);
  font-size: 13px;
}
.viewer__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}
.viewer__nav:hover {
  background: rgba(255, 255, 255, 0.18);
}
.viewer__nav.is-busy {
  opacity: 0.5;
}
.viewer__nav--prev {
  left: 12px;
}
.viewer__nav--next {
  right: 12px;
}
.viewer__bar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 64px;
  padding: 10px 20px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.viewer__info {
  min-width: 0;
}
.viewer__title {
  margin: 0;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.viewer__meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  font-variant-numeric: tabular-nums;
}
.viewer__download {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: #f5f5f7;
  color: #111;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.2s;
}
.viewer__download:hover {
  background: #fff;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
/* < 768px：隐藏左右箭头，仅支持滑动切换 */
@media (max-width: 767px) {
  .viewer__stage {
    padding: 0;
  }
  .viewer__img {
    max-width: 100%;
  }
  .viewer__img--thumb {
    width: 100%;
  }
  .viewer__nav {
    display: none;
  }
  .viewer__bar {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
