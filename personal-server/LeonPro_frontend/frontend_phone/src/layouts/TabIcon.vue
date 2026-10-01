<script setup lang="ts">
/** 底部标签栏图标：未选中线框、选中实心（24×24，线宽 1.8，和 Element Plus 图标视觉大小一致） */
defineProps<{ name: 'home' | 'wallpaper' | 'tools' | 'me'; active: boolean }>()
</script>

<template>
  <svg class="tab-icon" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
    <template v-if="name === 'home'">
      <path
        d="M4 10.2 12 4l8 6.2V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z"
        :fill="active ? 'currentColor' : 'none'"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'wallpaper'">
      <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" :fill="active ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.8" />
      <path
        d="m6.5 17 4-4.5 3 3 2-2 2.5 3.5"
        fill="none"
        :stroke="active ? 'var(--el-bg-color)' : 'currentColor'"
        stroke-width="1.8"
        stroke-linejoin="round"
        stroke-linecap="round"
      />
      <circle cx="15.5" cy="8.8" r="1.6" :fill="active ? 'var(--el-bg-color)' : 'none'" :stroke="active ? 'none' : 'currentColor'" stroke-width="1.6" />
    </template>
    <template v-else-if="name === 'tools'">
      <rect
        v-for="(p, i) in [
          [4, 4],
          [13.5, 4],
          [4, 13.5],
          [13.5, 13.5],
        ]"
        :key="i"
        :x="p[0]"
        :y="p[1]"
        width="6.5"
        height="6.5"
        rx="1.6"
        :fill="active ? 'currentColor' : 'none'"
        stroke="currentColor"
        stroke-width="1.8"
      />
    </template>
    <template v-else>
      <circle cx="12" cy="8.5" r="4" :fill="active ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.8" />
      <path
        d="M4.5 20c.6-3.8 3.7-6 7.5-6s6.9 2.2 7.5 6z"
        :fill="active ? 'currentColor' : 'none'"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
    </template>
  </svg>
</template>

<style scoped>
.tab-icon {
  display: block;
  width: 24px;
  height: 24px;
}
</style>
