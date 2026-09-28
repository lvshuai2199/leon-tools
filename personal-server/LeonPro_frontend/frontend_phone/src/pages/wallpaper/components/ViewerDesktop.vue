<script setup lang="ts">
/**
 * 电脑版大图查看：图片居中、左右箭头、键盘 ←→、Esc 关闭、右上角「下载原图」
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, ArrowRight, Close, Download } from '@element-plus/icons-vue'
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

const { current, loaded, failed, pendingNext, hasPrev, hasNext, counterTotal, resolution, fileName, prev, next } = useViewer(
  props,
  emit,
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

const root = ref<HTMLElement>()
let lastFocus: Element | null = null
onMounted(() => {
  window.addEventListener('keydown', onKey)
  lastFocus = document.activeElement
  root.value?.focus()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  if (lastFocus instanceof HTMLElement) lastFocus.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <div ref="root" class="viewer" role="dialog" aria-modal="true" :aria-label="current?.title || '图片查看'" tabindex="-1">
      <div class="viewer__top">
        <div class="viewer__info">
          <p class="viewer__title">{{ current?.title || '未命名' }}</p>
          <p class="viewer__meta">
            <span>{{ index + 1 }} / {{ counterTotal }}</span>
            <span v-if="resolution">{{ resolution }}</span>
          </p>
        </div>
        <div class="viewer__actions">
          <a v-if="current" class="viewer__download" :href="current.url" :download="fileName" target="_blank" rel="noopener">
            <el-icon><Download /></el-icon>
            下载原图
          </a>
          <button type="button" class="viewer__icon-btn" aria-label="关闭（Esc）" title="关闭（Esc）" @click="emit('close')">
            <el-icon :size="22"><Close /></el-icon>
          </button>
        </div>
      </div>

      <div class="viewer__stage" @click.self="emit('close')">
        <template v-if="current">
          <!-- 缩略图垫底，原图加载完成后覆盖 -->
          <img v-if="!loaded && current.thumbUrl" :key="`t-${current.id}`" :src="current.thumbUrl" alt="" class="viewer__img viewer__img--thumb" />
          <img
            :key="current.id"
            :src="current.url"
            :alt="current.title"
            class="viewer__img"
            :class="{ 'is-loaded': loaded }"
            @load="loaded = true"
            @error="failed = true"
          />
          <span v-if="!loaded && !failed" class="viewer__spinner" aria-hidden="true" />
          <p v-if="failed" class="viewer__error">原图加载失败</p>
        </template>
        <span v-else-if="loadingMore" class="viewer__spinner" aria-hidden="true" />

        <button v-if="hasPrev" type="button" class="viewer__nav viewer__nav--prev" aria-label="上一张（←）" @click.stop="prev">
          <el-icon :size="26"><ArrowLeft /></el-icon>
        </button>
        <button
          v-if="hasNext"
          type="button"
          class="viewer__nav viewer__nav--next"
          :class="{ 'is-busy': pendingNext && loadingMore }"
          aria-label="下一张（→）"
          @click.stop="next"
        >
          <el-icon :size="26"><ArrowRight /></el-icon>
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
// 查看器是深色沉浸界面，底色/白色半透明为写死颜色（见报告，待美工定）
.viewer {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  background: #111;
  color: #fff;
  outline: none;
  animation: viewer-in 0.2s ease;
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
  gap: lp.$space-4;
  height: 64px;
  padding: 0 lp.$space-3 0 lp.$space-5;
}
.viewer__info {
  min-width: 0;
}
.viewer__title {
  margin: 0;
  font-size: lp.$font-size-medium;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.viewer__meta {
  display: flex;
  gap: lp.$space-3;
  margin: 2px 0 0;
  font-size: lp.$font-size-extra-small;
  color: rgba(255, 255, 255, 0.6);
  font-variant-numeric: tabular-nums;
}
.viewer__actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: lp.$space-2;
}
.viewer__download {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 lp.$space-4;
  border-radius: lp.$radius-base;
  background: var(--el-color-primary);
  color: #fff;
  font-size: lp.$font-size-base;
  font-weight: lp.$font-weight-medium;
  transition: background-color 0.2s;
  &:hover {
    background: var(--el-color-primary-light-3);
  }
}
.viewer__icon-btn {
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
  &:hover {
    background: rgba(255, 255, 255, 0.1);
  }
}
.viewer__stage {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 72px lp.$space-5;
  user-select: none;
}
.viewer__img {
  position: absolute;
  max-width: calc(100% - 144px);
  max-height: calc(100% - #{lp.$space-5});
  object-fit: contain;
  opacity: 0;
  transition: opacity 0.3s ease;
  -webkit-user-drag: none;
  &.is-loaded {
    opacity: 1;
  }
}
.viewer__img--thumb {
  opacity: 1;
  width: calc(100% - 144px);
  aspect-ratio: 16 / 9;
  filter: blur(8px);
}
.viewer__spinner {
  position: absolute;
  width: 32px;
  height: 32px;
  border: 2.5px solid rgba(255, 255, 255, 0.2);
  border-top-color: #fff;
  border-radius: 50%;
  animation: lp-spin 0.8s linear infinite;
}
.viewer__error {
  position: absolute;
  margin: 0;
  padding: lp.$space-2 14px;
  border-radius: lp.$radius-base;
  background: rgba(0, 0, 0, 0.6);
  font-size: lp.$font-size-base;
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
  transition: background-color 0.2s;
  &:hover {
    background: rgba(255, 255, 255, 0.18);
  }
  &.is-busy {
    opacity: 0.5;
  }
}
.viewer__nav--prev {
  left: lp.$space-3;
}
.viewer__nav--next {
  right: lp.$space-3;
}
</style>
