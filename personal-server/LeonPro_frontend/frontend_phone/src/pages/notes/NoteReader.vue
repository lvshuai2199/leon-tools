<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import notesApi from '@/api/notes'
import { showToast } from '@/utils/ui'
import { hydrateNoteImages, renderNoteMarkdown, revokeNoteAssets } from './markdown'
import { useNotes } from './useNotes'
import './note-md.scss'

const notes = useNotes()
const bodyRef = ref<HTMLElement>()
const html = computed(() => (notes.doc.value ? renderNoteMarkdown(notes.doc.value.content || '', notes.doc.value.path) : ''))

watch(html, async () => {
  revokeNoteAssets()
  await nextTick()
  if (bodyRef.value) hydrateNoteImages(bodyRef.value, (path) => notesApi.asset(path))
})

function onClick(event: MouseEvent) {
  const target = event.target instanceof Element ? event.target : null
  const link = target?.closest('a')
  if (!link) return
  const doc = link.getAttribute('data-note-doc')
  if (doc) {
    event.preventDefault()
    notes.openDoc(doc)
    return
  }
  const file = link.getAttribute('data-note-file')
  if (file) {
    event.preventDefault()
    notesApi.asset(file).then((blob) => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = file.split('/').pop() || 'file'
      a.click()
      URL.revokeObjectURL(url)
    }).catch(() => showToast('文件下载失败', 'error'))
  }
}

onBeforeUnmount(revokeNoteAssets)
</script>

<template>
  <article v-if="notes.doc.value" class="reader">
    <p class="reader__path">{{ notes.doc.value.path }}</p>
    <div ref="bodyRef" class="note-md" @click="onClick" v-html="html"></div>
  </article>
  <p v-else-if="notes.docLoading.value" class="reader__hint">正在打开…</p>
  <p v-else-if="notes.docError.value" class="reader__hint is-error">{{ notes.docError.value }}</p>
  <p v-else class="reader__hint">从左边选一篇笔记</p>
</template>

<style scoped lang="scss">
.reader__path {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  word-break: break-all;
}
.reader__hint {
  margin: 32px 0;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.reader__hint.is-error { color: var(--el-color-danger); }
</style>
