<script setup lang="ts">
/**
 * 手机版大图查看（自写）：全屏黑底，手指左右滑动切换（跟手），点一下显示/隐藏工具栏，底部「下载原图」。
 */
import { computed, ref } from 'vue'
import { ArrowLeft, Download } from '@element-plus/icons-vue'
import type { WallpaperImage } from '@/api/types'
import { useViewer } from '../useViewer'

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

const { current, loaded, failed, pendingNext, hasPrev, counterTotal, resolution, fileName, prev, next } = useViewer(props, emit)

const showUi = ref(true)

/** 三格轨道：上一张 / 当前 / 下一张 */
const slides = computed(() =>
  [-1, 0, 1].map((off) => ({ off, img: props.images[props.index + off] as WallpaperImage | undefined })),
)

/* ---------- 跟手滑动 ---------- */
const width = () => window.innerWidth || 375
const dragX = ref(0)
const animating = ref(false)
let startX = 0
let startY = 0
let tracking = false
let horizontal: boolean | null = null

function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1 || animating.value) return
  tracking = true
  horizontal = null
  startX = e.touches[0].clientX
  startY = e.touches[0].clientY
  dragX.value = 0
}
function onTouchMove(e: TouchEvent) {
  if (!tracking) return
  const dx = e.touches[0].clientX - startX
  const dy = e.touches[0].clientY - startY
  if (horizontal === null && Math.abs(dx) + Math.abs(dy) > 8) horizontal = Math.abs(dx) > Math.abs(dy)
  if (!horizontal) return
  // 两端没有图时加阻尼
  const atStart = !hasPrev.value && dx > 0
  const atEnd = props.index >= props.images.length - 1 && dx < 0
  dragX.value = atStart || atEnd ? dx * 0.3 : dx
}
function onTouchEnd() {
  if (!tracking) return
  tracking = false
  const dx = dragX.value
  if (!horizontal || Math.abs(dx) < Math.min(80, width() * 0.18)) {
    settle(0)
    return
  }
  if (dx < 0 && props.index < props.images.length - 1) settle(-width(), next)
  else if (dx > 0 && hasPrev.value) settle(width(), prev)
  else {
    if (dx < 0 && props.hasMore) next() // 末尾：加载下一页后自动前进
    settle(0)
  }
}

function settle(to: number, done?: () => void) {
  animating.value = true
  dragX.value = to
  window.setTimeout(() => {
    animating.value = false
    // 先换图再复位，关掉过渡避免闪回
    done?.()
    dragX.value = 0
  }, 220)
}

function toggleUi() {
  showUi.value = !showUi.value
}
</script>

<template>
  <Teleport to="body">
    <div class="mv" role="dialog" aria-modal="true" :aria-label="current?.title || '图片查看'">
      <div
        class="mv__stage"
        @click="toggleUi"
        @touchstart.passive="onTouchStart"
        @touchmove.passive="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
      >
        <div class="mv__track" :class="{ 'is-animating': animating }" :style="{ transform: `translate3d(calc(-100% + ${dragX}px), 0, 0)` }">
          <div v-for="s in slides" :key="s.img ? s.img.id : `empty${s.off}`" class="mv__slide">
            <template v-if="s.img">
              <template v-if="s.off === 0">
                <img v-if="!loaded" :src="s.img.thumbUrl" alt="" class="mv__img mv__img--thumb" />
                <img
                  :src="s.img.url"
                  :alt="s.img.title"
                  class="mv__img"
                  :class="{ 'is-loaded': loaded }"
                  @load="loaded = true"
                  @error="failed = true"
                />
                <span v-if="!loaded && !failed" class="mv__spinner" aria-hidden="true" />
                <p v-if="failed" class="mv__error">原图加载失败</p>
              </template>
              <img v-else :src="s.img.thumbUrl" alt="" class="mv__img is-loaded" />
            </template>
            <span v-else-if="s.off === 1 && pendingNext && loadingMore" class="mv__spinner" aria-hidden="true" />
          </div>
        </div>
      </div>

      <transition name="mv-fade">
        <header v-show="showUi" class="mv__top">
          <button type="button" class="mv__icon-btn" aria-label="关闭" @click="emit('close')">
            <el-icon :size="22"><ArrowLeft /></el-icon>
          </button>
          <span class="mv__counter">{{ index + 1 }} / {{ counterTotal }}</span>
          <span class="mv__icon-btn" aria-hidden="true" />
        </header>
      </transition>

      <transition name="mv-fade">
        <footer v-show="showUi && current" class="mv__bar">
          <div class="mv__info">
            <p class="mv__title">{{ current?.title || '未命名' }}</p>
            <p v-if="resolution" class="mv__meta">{{ resolution }}</p>
          </div>
          <a v-if="current" class="mv__download" :href="current.url" :download="fileName" target="_blank" rel="noopener">
            <el-icon :size="18"><Download /></el-icon>
            下载原图
          </a>
        </footer>
      </transition>
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
// 查看器是深色沉浸界面，黑底/白色半透明为写死颜色（见报告，待美工定）
.mv {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: #000;
  color: #fff;
  overflow: hidden;
  touch-action: none;
}
.mv__stage {
  position: absolute;
  inset: 0;
}
.mv__track {
  display: flex;
  width: 100%;
  height: 100%;
  will-change: transform;
  &.is-animating {
    transition: transform 0.22s ease-out;
  }
}
.mv__slide {
  position: relative;
  flex: 0 0 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.mv__img {
  position: absolute;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  opacity: 0;
  transition: opacity 0.3s ease;
  -webkit-user-drag: none;
  user-select: none;
  &.is-loaded {
    opacity: 1;
  }
}
.mv__img--thumb {
  opacity: 1;
  width: 100%;
  filter: blur(6px);
}
.mv__spinner {
  position: absolute;
  width: 32px;
  height: 32px;
  border: 2.5px solid rgba(255, 255, 255, 0.2);
  border-top-color: #fff;
  border-radius: 50%;
  animation: lp-spin 0.8s linear infinite;
}
.mv__error {
  position: absolute;
  margin: 0;
  padding: lp.$space-2 14px;
  border-radius: lp.$radius-base;
  background: rgba(0, 0, 0, 0.6);
  font-size: lp.$font-size-base;
}
.mv__top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: calc(#{lp.$mobile-topbar-height} + env(safe-area-inset-top));
  padding: env(safe-area-inset-top) lp.$space-1 0;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), transparent);
}
.mv__icon-btn {
  width: lp.$component-size-mobile;
  height: lp.$component-size-mobile;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  color: #fff;
}
.mv__counter {
  font-size: lp.$font-size-mobile-body;
  font-variant-numeric: tabular-nums;
}
.mv__bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: lp.$space-3;
  padding: lp.$space-5 lp.$space-4 calc(#{lp.$space-3} + env(safe-area-inset-bottom));
  background: linear-gradient(to top, rgba(0, 0, 0, 0.65), transparent);
}
.mv__info {
  min-width: 0;
}
.mv__title {
  margin: 0;
  font-size: lp.$font-size-mobile-body;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mv__meta {
  margin: 2px 0 0;
  font-size: lp.$font-size-extra-small;
  color: rgba(255, 255, 255, 0.65);
  font-variant-numeric: tabular-nums;
}
.mv__download {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: lp.$component-size-mobile;
  padding: 0 lp.$space-4;
  border-radius: lp.$radius-base;
  background: var(--el-color-primary);
  color: #fff;
  font-size: lp.$font-size-mobile-body;
  font-weight: lp.$font-weight-medium;
}
.mv-fade-enter-active,
.mv-fade-leave-active {
  transition: opacity 0.2s ease;
}
.mv-fade-enter-from,
.mv-fade-leave-to {
  opacity: 0;
}
</style>
