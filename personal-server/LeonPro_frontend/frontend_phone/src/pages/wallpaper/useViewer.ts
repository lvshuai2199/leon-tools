/**
 * 大图查看的翻页逻辑（电脑版、手机版共用）
 * - 到达已加载末尾时，若还有下一页则通知父组件加载，加载完成后自动前进
 * - 切换图片时预加载相邻原图，接近末尾时提前加载下一页
 * - 打开期间锁定 body 滚动
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { WallpaperImage } from '@/api/types'
import { lockBodyScroll, unlockBodyScroll } from '@/composables/useScrollLock'
import { downloadName } from './useWallpaperBrowse'

export interface ViewerProps {
  images: WallpaperImage[]
  index: number
  hasMore: boolean
  loadingMore: boolean
  total?: number
}

export interface ViewerEmit {
  (e: 'close'): void
  (e: 'update:index', index: number): void
  (e: 'loadMore'): void
}

export function useViewer(props: ViewerProps, emit: ViewerEmit) {
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
  const fileName = computed(() => downloadName(current.value))

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

  watch(
    () => current.value?.url,
    () => {
      loaded.value = false
      failed.value = false
      for (const i of [props.index + 1, props.index - 1]) {
        const u = props.images[i]?.url
        if (u) new Image().src = u
      }
      if (props.hasMore && !props.loadingMore && props.index >= props.images.length - 3) emit('loadMore')
    },
    { immediate: true },
  )

  onMounted(lockBodyScroll)
  onBeforeUnmount(unlockBodyScroll)

  return { current, loaded, failed, pendingNext, hasPrev, hasNext, counterTotal, resolution, fileName, go, prev, next }
}
