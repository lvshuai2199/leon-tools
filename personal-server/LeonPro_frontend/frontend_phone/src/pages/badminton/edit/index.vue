<script setup lang="ts">
/** /badminton/new、/badminton/:id：手机独立页，电脑同一套表单 */
import { useBreakpoint } from '@/composables/useBreakpoint'
import Desktop from './Desktop.vue'
import Mobile from './Mobile.vue'

const { isMobile } = useBreakpoint()
</script>

<template>
  <Mobile v-if="isMobile" />
  <Desktop v-else />
</template>
