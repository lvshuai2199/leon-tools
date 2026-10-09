<script setup lang="ts">
import { useDrafts } from './useDrafts'

const drafts = useDrafts()
</script>

<template>
  <div class="editor">
    <input v-model="drafts.title.value" class="editor__title" maxlength="200" placeholder="标题" />
    <textarea v-model="drafts.content.value" class="editor__body" placeholder="直接写。没写完会自动缓存，写完再上传到仓库。"></textarea>
    <div class="editor__bar">
      <span class="editor__state">{{ drafts.saveState.value || '开始写就会缓存' }}</span>
      <div class="editor__actions">
        <el-button @click="drafts.remove()">删除</el-button>
        <el-button type="primary" :loading="drafts.uploading.value" @click="drafts.upload()">完成并上传</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.editor {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  gap: 10px;
}
.editor__title,
.editor__body {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font: inherit;
}
.editor__title {
  padding: 10px 12px;
  font-size: 18px;
  font-weight: 650;
}
.editor__body {
  flex: 1;
  min-height: 240px;
  padding: 12px;
  resize: vertical;
  line-height: 1.7;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.editor__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.editor__state {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.editor__actions {
  display: flex;
  gap: 8px;
}
</style>
