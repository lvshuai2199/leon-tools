<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchGroups, fetchImages } from '@/api/wallpaper'
import type { WallpaperGroup, WallpaperImage } from '@/api/types'
import { errorMessage, isNotFound } from '@/api/http'
import { GROUP_SIDEBAR_THRESHOLD, WALLPAPER_PAGE_SIZE } from '@/config'
import { useMediaQuery } from '@/composables/useMediaQuery'
import StateBlock from '@/components/StateBlock.vue'
import GroupTabs from './components/GroupTabs.vue'
import GroupSidebar from './components/GroupSidebar.vue'
import ImageGrid from './components/ImageGrid.vue'
import ImageViewer from './components/ImageViewer.vue'

const route = useRoute()
const router = useRouter()

/* ------------------------------ 分组 ------------------------------ */
const groups = ref<WallpaperGroup[]>([])
const groupsLoading = ref(true)
const groupsError = ref('')

const isDesktop = useMediaQuery('(min-width: 1024px)')
/** 分组 > 8 且桌面端：左侧 200px 分组列表；否则顶部横向 tabs */
const useSidebar = computed(() => isDesktop.value && groups.value.length > GROUP_SIDEBAR_THRESHOLD)

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

/**
 * 仅当 URL 中没有分组（/wallpaper）时默认跳到第一个分组；
 * URL 中的分组不在公开列表里时不跳转，显示「分组不存在或未公开」（见 unknownKey）
 */
function ensureValidGroup() {
  if (!groups.value.length) return
  if (!routeKey.value) {
    router.replace({ name: 'wallpaper', params: { groupKey: groups.value[0].groupKey } })
  }
}

/** URL 指定的分组不在公开分组列表中（不存在或未公开）：不请求图片，直接显示不存在状态 */
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
/** 图片请求失败：notFound=true 表示 404（分组不存在或未公开），其余为加载错误 */
const pageError = ref<{ message: string; notFound: boolean } | null>(null)
let reqSeq = 0 // 丢弃过期请求（快速切换分组时）

const hasMore = computed(() => page.value === 0 || page.value < pages.value)
const initialLoading = computed(() => loading.value && images.value.length === 0)
const isEmpty = computed(() => !loading.value && !pageError.value && page.value > 0 && images.value.length === 0)
/** 不存在状态：URL 分组未知，或图片请求返回 404 */
const notFound = computed(() => unknownKey.value || !!pageError.value?.notFound)
const notFoundTitle = computed(() =>
  unknownKey.value ? '分组不存在或未公开' : pageError.value?.message || '分组不存在或未公开',
)
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
    // 以图片分页 total 为准，回写到分组列表，保证 tab / 侧栏 / 标题数量一致
    if (group.imageCount !== res.total) group.imageCount = res.total
    // 兜底：服务端返回空页时视为结束
    if (!res.records.length) pages.value = page.value
  } catch (e) {
    if (seq !== reqSeq) return
    pageError.value = isNotFound(e)
      ? { notFound: true, message: (e.fromServer && e.message) || '分组不存在或未公开' }
      : { notFound: false, message: errorMessage(e) }
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

/** 404 状态下「查看其他分组」：跳到第一个公开分组；若 404 的就是第一个或没有分组，则回首页 */
function goOtherGroup() {
  const first = groups.value[0]
  if (!first || first.groupKey === routeKey.value) router.replace({ name: 'home' })
  else router.replace({ name: 'wallpaper', params: { groupKey: first.groupKey } })
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
const sentinel = ref<HTMLElement>()
let io: IntersectionObserver | null = null

function setupObserver() {
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMore()
    },
    { rootMargin: '0px 0px 600px 0px' },
  )
}

/** 加载完一页后若哨兵仍在视口内（内容不足一屏），重新观察以再次触发 */
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

/* ------------------------------ 查看器 ------------------------------ */
const viewerIndex = ref(-1)
function openViewer(i: number) {
  viewerIndex.value = i
}

onMounted(() => {
  setupObserver()
  if (sentinel.value) io!.observe(sentinel.value)
  loadGroups()
})
onBeforeUnmount(() => {
  io?.disconnect()
  reqSeq++
})
</script>

<template>
  <div class="browse container" :class="{ 'browse--sidebar': useSidebar }">
    <!-- 分组加载失败 -->
    <StateBlock v-if="groupsError" type="error" title="分组加载失败" :desc="groupsError" @retry="loadGroups" />

    <StateBlock v-else-if="groupsLoading" type="loading" desc="正在加载分组…" />

    <StateBlock v-else-if="!groups.length" type="empty" title="暂无公开的壁纸分组" desc="稍后再来看看吧" />

    <template v-else>
      <aside v-if="useSidebar" class="browse__aside">
        <!-- 未知分组时 active 为 undefined：不高亮任何分组 -->
        <GroupSidebar :groups="groups" :active="activeGroup?.groupKey" @select="selectGroup" />
      </aside>

      <section class="browse__main">
        <div v-if="!useSidebar" class="browse__tabs">
          <GroupTabs :groups="groups" :active="activeGroup?.groupKey" @select="selectGroup" />
        </div>

        <!-- 不存在状态：标题行只显示分组名（未知分组则显示 URL 中的 key），隐藏描述与数量 -->
        <header v-if="notFound" class="info">
          <h1 class="info__name">{{ activeGroup?.name || routeKey }}</h1>
        </header>
        <header v-else-if="activeGroup" class="info">
          <h1 class="info__name">{{ activeGroup.name }}</h1>
          <p v-if="activeGroup.description" class="info__desc">{{ activeGroup.description }}</p>
          <span class="info__count">{{ activeGroup.imageCount }} 张</span>
        </header>

        <ImageGrid :images="images" :skeleton="initialLoading ? 8 : 0" @open="openViewer" />

        <StateBlock v-if="isEmpty" type="empty" title="这个分组还没有壁纸" desc="去看看其他分组吧" />

        <!-- 不存在或未公开（URL 分组未知 / 图片接口 404）：中性提示，无重试 -->
        <StateBlock v-if="notFound" type="notfound" :title="notFoundTitle">
          <button class="btn state-action" type="button" @click="goOtherGroup">查看其他分组</button>
        </StateBlock>

        <!-- 网络错误 / 5xx 等：红色错误 + 重试 -->
        <StateBlock
          v-else-if="pageError"
          type="error"
          :compact="images.length > 0"
          :title="images.length ? '加载更多失败' : '图片加载失败'"
          :desc="pageError.message"
          @retry="retryPage"
        />

        <div ref="sentinel" class="sentinel" aria-hidden="true" />

        <div v-if="loading && images.length" class="more"><span class="more__spinner" />加载中…</div>
        <p v-if="reachedEnd" class="end">— 已经到底了 · 共 {{ images.length }} 张 —</p>
      </section>
    </template>

    <ImageViewer
      v-if="viewerIndex >= 0 && images.length"
      v-model:index="viewerIndex"
      :images="images"
      :has-more="hasMore && !pageError"
      :loading-more="loading"
      :total="total"
      @load-more="loadMore"
      @close="viewerIndex = -1"
    />
  </div>
</template>

<style scoped>
.browse {
  /* 分组标题行高度 & 与网格的间距（侧栏「分组」标签共用，保证对齐） */
  --group-head-h: 33px;
  --group-head-gap: 16px;
  padding-top: 20px;
  padding-bottom: 48px;
}
.browse--sidebar {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 32px;
  align-items: start;
}
.browse__main {
  min-width: 0;
}
.browse__tabs {
  position: sticky;
  top: var(--topbar-h);
  z-index: 10;
  margin: -8px calc(-1 * var(--page-pad)) 0;
  padding: 8px var(--page-pad);
  background: var(--bg);
}
.info {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 12px 0 var(--group-head-gap);
}
.browse--sidebar .info {
  margin-top: 0;
}
.info__name {
  margin: 0;
  font-size: 22px;
  line-height: var(--group-head-h);
  font-weight: 600;
}
.info__desc {
  margin: 0;
  font-size: 14px;
  color: var(--text-2);
}
.info__count {
  font-size: 13px;
  color: #8a8f98;
  font-variant-numeric: tabular-nums;
}
.sentinel {
  height: 1px;
}
.state-action {
  margin-top: 16px;
}
.more,
.end {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 20px 0 0;
  font-size: 13px;
  color: var(--text-3);
}
.more__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(0, 0, 0, 0.12);
  border-top-color: var(--text-2);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 639px) {
  .browse {
    padding-top: 12px;
  }
  .info {
    margin: 8px 0 12px;
  }
  .info__name {
    font-size: 18px;
    line-height: 1.5;
  }
  .info__desc {
    font-size: 13px;
  }
}
</style>
