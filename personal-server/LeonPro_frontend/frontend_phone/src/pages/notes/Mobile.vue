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

const reading = computed(() => tab.value === 'repo' && !!activePath.value)
const title = computed(() => {
  if (reading.value) return notes.doc.value?.title || '笔记'
  if (tab.value === 'draft' && drafts.editing.value) return drafts.title.value || '随手记'
  return tab.value === 'draft' ? '随手记' : '笔记'
})
const back = computed(() => {
  if (reading.value) return { path: '/notes' }
  if (drafts.editing.value) return { path: '/notes', query: { tab: 'draft' } }
  return '/'
})
</script>

<template>
  <div class="mnotes">
    <PageBar :title="title" :back="back" />
    <div v-if="!reading && !drafts.editing.value" class="seg">
      <button type="button" :class="{ 'is-on': tab === 'repo' }" @click="notes.setTab('repo')">仓库</button>
      <button type="button" :class="{ 'is-on': tab === 'draft' }" @click="notes.setTab('draft')">随手记</button>
    </div>
    <p v-if="tab === 'repo' && !reading" class="status">{{ describe(source) }}</p>

    <template v-if="tab === 'repo'">
      <NoteReader v-if="reading" />
      <template v-else>
        <input v-model="keyword" class="search" placeholder="搜索标题或路径" />
        <StateBlock v-if="notes.loading.value && !docs.length" type="loading" />
        <StateBlock v-else-if="notes.error.value" type="error" :title="notes.error.value" @retry="notes.loadList()" />
        <StateBlock
          v-else-if="source && !source.configured"
          type="empty"
          icon="document"
          title="还没有配置仓库"
          desc="到管理端「工具中心 / 笔记」填写仓库地址并选择分支"
        />
        <StateBlock v-else-if="!docs.length" type="empty" icon="document" title="这个分支里还没有 Markdown" />
        <NoteTree v-else :docs="docs" :active="activePath" :keyword="keyword" @open="notes.openDoc" />
        <p v-if="source?.status === 'error'" class="warn">{{ source.lastError || '同步失败' }}</p>
      </template>
    </template>

    <template v-else>
      <DraftEditor v-if="drafts.editing.value" />
      <template v-else>
        <el-button type="primary" class="new" @click="drafts.createNew()">新建</el-button>
        <StateBlock v-if="drafts.loading.value && !drafts.list.value.length" type="loading" />
        <StateBlock v-else-if="drafts.error.value" type="error" :title="drafts.error.value" @retry="drafts.reload()" />
        <StateBlock v-else-if="!drafts.list.value.length" type="empty" icon="document" title="还没有缓存" desc="写的过程会自动缓存，写完再上传" />
        <button v-for="d in drafts.list.value" :key="d.id" type="button" class="draft" @click="drafts.openDraft(d.id)">
          <strong>{{ d.title || '未命名' }}</strong>
          <span>{{ d.updateTime }}</span>
          <em v-if="d.excerpt">{{ d.excerpt }}</em>
        </button>
      </template>
    </template>
  </div>
</template>

<style scoped lang="scss">
.mnotes {
  padding: 0 16px 24px;
}
.seg {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  button {
    height: 36px;
    padding: 0 14px;
    border: 1px solid var(--el-border-color);
    border-radius: 999px;
    background: var(--el-bg-color);
    cursor: pointer;
  }
  .is-on {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }
}
.status, .warn {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.warn { color: var(--el-color-warning); }
.search {
  width: 100%;
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font: inherit;
}
.draft {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  text-align: left;
  strong { font-weight: 650; }
  span, em { color: var(--el-text-color-secondary); font-size: 12px; font-style: normal; }
}
.new { width: 100%; margin-bottom: 8px; }
</style>
