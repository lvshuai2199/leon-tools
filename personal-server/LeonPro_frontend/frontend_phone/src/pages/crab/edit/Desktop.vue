<script setup lang="ts">
/** 电脑：列表 + 详情弹窗（关掉弹窗回到 /crab，保留日期筛选） */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ListDesktop from '../list/Desktop.vue'

const route = useRoute()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : null))
</script>

<template>
  <ListDesktop :detail-id="id" />
</template>
