<script setup lang="ts">
import { useRoute } from 'vue-router'
import StateBlock from '@/components/StateBlock.vue'

const route = useRoute()
</script>

<template>
  <div class="nf">
    <StateBlock type="notfound" title="页面不存在" :desc="`没有找到 ${route.path}`">
      <router-link to="/" custom v-slot="{ navigate }">
        <el-button type="primary" @click="navigate">回首页</el-button>
      </router-link>
    </StateBlock>
  </div>
</template>

<style scoped lang="scss">
.nf {
  padding: lp.$space-6 lp.$page-padding-mobile;
  word-break: break-all;
}
</style>
