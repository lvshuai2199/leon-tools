<script setup lang="ts">
import { ref, watch } from 'vue'
import { buildTree } from './note-path.js'
import TreeNodes from './TreeNodes.vue'

const props = defineProps<{
  docs: { path: string; title: string }[]
  active: string
  keyword: string
}>()
const emit = defineEmits<{ open: [path: string] }>()

const openDirs = ref(new Set<string>())

watch(
  () => props.docs,
  (docs) => {
    const next = new Set(openDirs.value)
    for (const doc of docs || []) {
      const parts = doc.path.split('/')
      if (parts.length > 1) next.add(parts[0])
    }
    openDirs.value = next
  },
  { immediate: true },
)

watch(
  () => props.active,
  (path) => {
    if (!path) return
    const parts = path.split('/')
    const next = new Set(openDirs.value)
    for (let i = 1; i < parts.length; i++) next.add(parts.slice(0, i).join('/'))
    openDirs.value = next
  },
  { immediate: true },
)

function toggle(path: string) {
  const next = new Set(openDirs.value)
  if (next.has(path)) next.delete(path)
  else next.add(path)
  openDirs.value = next
}
</script>

<template>
  <TreeNodes :nodes="buildTree(docs, keyword)" :active="active" :open-dirs="openDirs" @open="emit('open', $event)" @toggle="toggle" />
</template>
