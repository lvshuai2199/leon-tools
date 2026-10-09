<script setup lang="ts">
import { computed } from 'vue'
import PageBar from '@/components/PageBar.vue'
import StateBlock from '@/components/StateBlock.vue'
import DraftEditor from './DraftEditor.vue'
import NoteReader from './NoteReader.vue'
import NoteTree from './NoteTree.vue'
import { describe } from './status'
import { useDrafts } from './useDrafts'
import { useNotes } from './useNotes'

const notes = useNotes()
const drafts = useDrafts()
const docs = notes.docs
const keyword = notes.keyword
const source = notes.source
const activePath = notes.activePath
const tab = notes.tab

const statusText = computed(() => describe(source.value))
const title = computed(() => {
  if (tab.value === 'draft') return drafts.editing.value ? drafts.title.value || '随手记' : '随手记'
  return notes.doc.value?.title || '笔记'
})
</script>

<template>
  <div class="notes">
    <PageBar :title="title" back="/" :desc="tab === 'repo' ? statusText : '没写完只存在缓存里，写完再上传到当前分支'" />
    <div class="seg">
      <button type="button" :class="{ 'is-on': tab === 'repo' }" @click="notes.setTab('repo')">仓库</button>
      <button type="button" :class="{ 'is-on': tab === 'draft' }" @click="notes.setTab('draft')">随手记</button>
    </div>

    <div v-if="tab === 'repo'" class="pane">
      <aside class="side">
        <input v-model="keyword" class="search" placeholder="搜索标题或路径" />
        <StateBlock v-if="notes.loading.value && !docs.length" type="loading" compact />
        <StateBlock v-else-if="notes.error.value" type="error" compact :title="notes.error.value" @retry="notes.loadList()" />
        <StateBlock
          v-else-if="source && !source.configured"
          type="empty"
          icon="document"
          compact
          title="还没有配置仓库"
          desc="到管理端「工具中心 / 笔记」填写仓库地址并选择分支"
        />
        <StateBlock v-else-if="!docs.length" type="empty" icon="document" compact title="这个分支里还没有 Markdown" />
        <NoteTree v-else :docs="docs" :active="activePath" :keyword="keyword" @open="notes.openDoc" />
        <p v-if="source?.lastError && source.status === 'ok'" class="warn">{{ source.lastError }}</p>
        <p v-if="source?.status === 'error'" class="warn">{{ source.lastError || '同步失败' }}</p>
      </aside>
      <section class="main">
        <NoteReader />
      </section>
    </div>

    <div v-else class="pane">
      <aside class="side">
        <el-button type="primary" class="new" @click="drafts.createNew()">新建</el-button>
        <StateBlock v-if="drafts.loading.value && !drafts.list.value.length" type="loading" compact />
        <StateBlock v-else-if="drafts.error.value" type="error" compact :title="drafts.error.value" @retry="drafts.reload()" />
        <StateBlock v-else-if="!drafts.list.value.length" type="empty" icon="document" compact title="还没有缓存" desc="新建一篇，写的过程会自动缓存" />
        <button
          v-for="d in drafts.list.value"
          :key="d.id"
          type="button"
          class="draft"
          :class="{ 'is-active': d.id === drafts.currentId.value }"
          @click="drafts.openDraft(d.id)"
        >
          <strong>{{ d.title || '未命名' }}</strong>
          <span>{{ d.updateTime }}</span>
          <em v-if="d.excerpt">{{ d.excerpt }}</em>
        </button>
      </aside>
      <section class="main">
        <DraftEditor v-if="drafts.editing.value" />
        <p v-else class="hint">选一篇缓存，或新建</p>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.notes {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  min-height: 520px;
  padding-bottom: 16px;
}
.seg {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  button {
    height: 32px;
    padding: 0 14px;
    border: 1px solid var(--el-border-color);
    border-radius: 999px;
    background: transparent;
    cursor: pointer;
  }
  .is-on {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }
}
.pane {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}
.side,
.main {
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  padding: 12px;
}
.search,
.draft,
.new {
  width: 100%;
}
.search {
  margin-bottom: 8px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
}
.draft {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  margin-top: 8px;
  padding: 8px;
  border: none;
  border-radius: lp.$radius-base;
  background: transparent;
  text-align: left;
  cursor: pointer;
  strong { font-weight: 650; }
  span, em { color: var(--el-text-color-secondary); font-size: 12px; font-style: normal; }
  &:hover, &.is-active { background: var(--el-color-primary-light-9); }
}
.new { margin-bottom: 8px; }
.warn { margin: 8px 0 0; color: var(--el-color-warning); font-size: 12px; }
.hint { margin: 48px 0; text-align: center; color: var(--el-text-color-secondary); }
</style>
