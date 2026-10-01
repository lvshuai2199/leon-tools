<script setup lang="ts">
/** 羽毛球计费列表：手机卡片，电脑表格 */
import { useBreakpoint } from '@/composables/useBreakpoint'
import Desktop from './Desktop.vue'
import Mobile from './Mobile.vue'

const { isMobile } = useBreakpoint()
</script>

<template>
  <Mobile v-if="isMobile" />
  <Desktop v-else />
</template>
