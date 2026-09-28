<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useBreakpoint } from '@/composables/useBreakpoint'
import DesktopLayout from '@/layouts/DesktopLayout.vue'
import MobileLayout from '@/layouts/MobileLayout.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'

const route = useRoute()
const { isMobile } = useBreakpoint()

const layout = computed(() => {
  if (route.meta.layout === 'blank') return BlankLayout
  return isMobile.value ? MobileLayout : DesktopLayout
})
</script>

<template>
  <!-- 手机布局用 large 尺寸（配合 lp.mobile-vars 变成 44px） -->
  <el-config-provider :locale="zhCn" :size="isMobile ? 'large' : 'default'">
    <component :is="layout" />
  </el-config-provider>
</template>
