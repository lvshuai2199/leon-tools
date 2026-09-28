/**
 * 壁纸浏览逻辑（电脑版、手机版共用）。从原展示端 portal 的 WallpaperBrowse.vue 抽出来，行为保持已验收的样子：
 * - 裸 /wallpaper 跳到第一个公开分组
 * - URL 里的分组不在公开列表里：不请求图片，大标题「分组不存在」（不显示网址里的 key）+ 灰色说明 +「查看其他分组」，不高亮标签
 * - 图片接口 404 同样显示不存在状态；网络错误和 5xx 才显示红色「图片加载失败」+「重试」
 * - 图片分页 total 回写到分组计数（标签、侧栏、标题数量一致）
 * - 无限滚动（哨兵元素 + IntersectionObserver），快速切换分组时丢弃过期请求
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchGroups, fetchImages } from '@/api/wallpaper'
import type { WallpaperGroup, WallpaperImage } from '@/api/types'
import { errorMessage, isNotFound, isRetryable } from '@/api/request'
import { WALLPAPER_PAGE_SIZE } from '@/config'

/** @param sentinel 列表底部哨兵元素（组件里用 useTemplateRef 取到后传进来） */
export function useWallpaperBrowse(sentinel: Readonly<Ref<HTMLElement | null | undefined>>) {
  const route = useRoute()
  const router = useRouter()

  /* ------------------------------ 分组 ------------------------------ */
  const groups = ref<WallpaperGroup[]>([])
  const groupsLoading = ref(true)
  const groupsError = ref('')

  const routeKey = computed(() => (typeof route.params.groupKey === 'string' ? route.params.groupKey : ''))
  const activeGroup = computed(() => groups.value.find((g) => g.groupKey === routeKey.value))

  async function loadGroups() {
    groupsLoading.value = true
    groupsError.value = ''
    try {
      groups.value = await fetchGroups()
      ensureValidGroup()
    } catch (e) {
      groupsError.value = errorMessage(e)
    } finally {
      groupsLoading.value = false
    }
  }

  /** 只有 URL 里没有分组（/wallpaper）时才跳到第一个分组 */
  function ensureValidGroup() {
    if (!groups.value.length || route.name !== 'wallpaper') return
    if (!routeKey.value) {
      router.replace({ name: 'wallpaper', params: { groupKey: groups.value[0].groupKey } })
    }
  }

  /** URL 指定的分组不在公开分组列表里（不存在或未公开）：不请求图片 */
  const unknownKey = computed(
    () => !groupsLoading.value && groups.value.length > 0 && !!routeKey.value && !activeGroup.value,
  )

  function selectGroup(key: string) {
    if (key === routeKey.value) return
    router.push({ name: 'wallpaper', params: { groupKey: key } })
  }

  /* ------------------------------ 图片分页 ------------------------------ */
  const images = shallowRef<WallpaperImage[]>([])
  const page = ref(0)
  const pages = ref(0)
  const total = ref(0)
  const loading = ref(false)
  /** notFound=true 表示 404（分组不存在或未公开）；retryable=true 表示网络错误 / 5xx */
  const pageError = ref<{ message: string; notFound: boolean; retryable: boolean } | null>(null)
  let reqSeq = 0

  const hasMore = computed(() => page.value === 0 || page.value < pages.value)
  const initialLoading = computed(() => loading.value && images.value.length === 0)
  const isEmpty = computed(() => !loading.value && !pageError.value && page.value > 0 && images.value.length === 0)
  /** 不存在状态：URL 分组未知，或图片请求返回 404 / 其他非重试类错误 */
  const notFound = computed(() => unknownKey.value || (!!pageError.value && !pageError.value.retryable))
  /** 「分组不存在」页的说明（大标题固定写「分组不存在」，不显示网址里的 key） */
  const notFoundTitle = computed(() => '链接可能已失效，或分组还没有公开')
  /** 红色错误：只在网络错误和 5xx */
  const loadError = computed(() => (pageError.value?.retryable ? pageError.value : null))
  const reachedEnd = computed(() => !loading.value && page.value > 0 && !hasMore.value && images.value.length > 0)

  function resetImages() {
    reqSeq++
    images.value = []
    page.value = 0
    pages.value = 0
    total.value = 0
    loading.value = false
    pageError.value = null
  }

  async function loadMore() {
    const group = activeGroup.value
    if (!group || loading.value || !hasMore.value || pageError.value) return
    const seq = ++reqSeq
    loading.value = true
    try {
      const res = await fetchImages(group.groupKey, page.value + 1, WALLPAPER_PAGE_SIZE)
      if (seq !== reqSeq) return
      images.value = [...images.value, ...res.records]
      page.value = res.current || page.value + 1
      pages.value = res.pages
      total.value = res.total
      // 以图片分页 total 为准回写分组计数，保证标签 / 侧栏 / 标题数量一致
      if (group.imageCount !== res.total) group.imageCount = res.total
      // 兜底：服务端返回空页时视为结束
      if (!res.records.length) pages.value = page.value
    } catch (e) {
      if (seq !== reqSeq) return
      if (isNotFound(e)) {
        pageError.value = { notFound: true, retryable: false, message: (e.fromServer && e.message) || '分组不存在或未公开' }
      } else {
        pageError.value = { notFound: false, retryable: isRetryable(e), message: errorMessage(e) }
      }
    } finally {
      if (seq === reqSeq) {
        loading.value = false
        recheckSentinel()
      }
    }
  }

  function retryPage() {
    pageError.value = null
    loadMore()
  }

  /** 不存在状态下「查看其他分组」：去第一个公开分组；没有或就是它自己则回首页 */
  function goOtherGroup() {
    const first = groups.value[0]
    if (!first || first.groupKey === routeKey.value) router.replace({ name: 'home' })
    else router.replace({ name: 'wallpaper', params: { groupKey: first.groupKey } })
  }

  /* ------------------------------ 查看器 ------------------------------ */
  const viewerIndex = ref(-1)
  function openViewer(i: number) {
    viewerIndex.value = i
  }
  function closeViewer() {
    viewerIndex.value = -1
  }

  watch(
    () => activeGroup.value?.groupKey,
    (key) => {
      viewerIndex.value = -1
      resetImages()
      if (key) {
        window.scrollTo({ top: 0 })
        loadMore()
      }
    },
  )

  watch(routeKey, () => {
    if (!groupsLoading.value) ensureValidGroup()
  })

  /* ------------------------------ 无限滚动 ------------------------------ */
  let io: IntersectionObserver | null = null

  async function recheckSentinel() {
    await nextTick()
    if (io && sentinel.value) {
      io.unobserve(sentinel.value)
      io.observe(sentinel.value)
    }
  }

  watch(sentinel, (el, old) => {
    if (old) io?.unobserve(old)
    if (el) io?.observe(el)
  })

  onMounted(() => {
    io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) loadMore()
      },
      { rootMargin: '0px 0px 600px 0px' },
    )
    if (sentinel.value) io.observe(sentinel.value)
    loadGroups()
  })
  onBeforeUnmount(() => {
    io?.disconnect()
    reqSeq++
  })

  return {
    groups,
    groupsLoading,
    groupsError,
    loadGroups,
    routeKey,
    activeGroup,
    selectGroup,
    images,
    total,
    loading,
    hasMore,
    initialLoading,
    isEmpty,
    notFound,
    notFoundTitle,
    loadError,
    pageError,
    reachedEnd,
    loadMore,
    retryPage,
    goOtherGroup,
    viewerIndex,
    openViewer,
    closeViewer,
  }
}

/** 下载文件名：标题 + 原图扩展名 */
export function downloadName(img?: WallpaperImage): string {
  if (!img) return ''
  const ext = (img.url.split('?')[0].match(/\.(jpe?g|png|webp|gif|bmp|avif)$/i)?.[0] ?? '.jpg').toLowerCase()
  const base = (img.title || `wallpaper-${img.id}`).replace(/[\\/:*?"<>|\s]+/g, '_')
  return `${base}${ext}`
}
