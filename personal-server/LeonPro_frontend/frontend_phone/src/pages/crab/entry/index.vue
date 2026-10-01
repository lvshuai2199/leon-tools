<script setup lang="ts">
/** 快速录入：手机单栏 + 底部固定入库按钮；电脑左边粘贴框、右边识别结果 */
import { useBreakpoint } from '@/composables/useBreakpoint'
import Desktop from './Desktop.vue'
import Mobile from './Mobile.vue'

const { isMobile } = useBreakpoint()
</script>

<template>
  <Mobile v-if="isMobile" />
  <Desktop v-else />
</template>
