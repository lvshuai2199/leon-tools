<script setup lang="ts">
/** 壁纸浏览：按断点切换电脑版 / 手机版（窗口缩放实时切换） */
import { useBreakpoint } from '@/composables/useBreakpoint'
import Desktop from './Desktop.vue'
import Mobile from './Mobile.vue'

const { isMobile } = useBreakpoint()
</script>

<template>
  <Mobile v-if="isMobile" />
  <Desktop v-else />
</template>
