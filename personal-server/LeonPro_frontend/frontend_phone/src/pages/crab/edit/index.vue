<script setup lang="ts">
/** /crab/:id：手机是独立页面；电脑是列表上的 480 宽弹窗 */
import { useBreakpoint } from '@/composables/useBreakpoint'
import Desktop from './Desktop.vue'
import Mobile from './Mobile.vue'

const { isMobile } = useBreakpoint()
</script>

<template>
  <Mobile v-if="isMobile" />
  <Desktop v-else />
</template>
