<script setup lang="ts">
/** 笔记：仓库 Markdown 阅读，以及随手记（未完成只缓存，完成后上传到当前分支） */
import { useBreakpoint } from '@/composables/useBreakpoint'
import Desktop from './Desktop.vue'
import Mobile from './Mobile.vue'
import { provideDrafts } from './useDrafts'
import { provideNotes } from './useNotes'

provideDrafts(provideNotes())
const { isMobile } = useBreakpoint()
</script>

<template>
  <Mobile v-if="isMobile" />
  <Desktop v-else />
</template>
