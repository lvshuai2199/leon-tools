<template>
  <view class="container">
    <web-view :src="url"></web-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      url: '' // 用于存储传入的链接
    }
  },
  onLoad(options) {
    if (options.url) {
      this.url = decodeURIComponent(options.url); // 获取并解码链接
    }
  }
}
</script>

<style>
.container {
  flex: 1; /* 使容器填满屏幕 */
}
</style>
