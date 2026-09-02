<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="flex-x-between">
          <span>注册码生成</span>
          <el-tag type="info" size="small">按配置生成 · 写入注册码记录</el-tag>
        </div>
      </template>
      <RegCodeGenerator />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import RegCodeGenerator from "./RegCodeGenerator.vue";

defineOptions({
  name: "RegCode",
  inheritAttrs: false,
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}
</style>
