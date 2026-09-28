<template>
  <div
    class="drop-zone"
    :class="{ active: dragging, tall }"
    @click="pick"
    @dragenter.prevent="dragging = true"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="onLeave"
    @drop.prevent="onDrop"
  >
    <el-icon class="dz-icon"><UploadFilled /></el-icon>
    <div class="dz-title">把图片或整个文件夹拖到这里</div>
    <div class="dz-sub">
      或者
      <span class="link" @click.stop="pick">选择图片</span>
      <span class="sep">·</span>
      <span class="link" @click.stop="pickDir">选择文件夹</span>
    </div>
    <div class="dz-hint">支持 jpg / png / webp / gif / bmp</div>
    <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onInput" />
    <input ref="dirInput" type="file" webkitdirectory multiple hidden @change="onInput" />
  </div>
</template>

<script setup lang="ts">
import { UploadFilled } from "@element-plus/icons-vue";

defineProps<{ tall?: boolean }>();
const emit = defineEmits(["files"]);

const dragging = ref(false);
const fileInput = ref<HTMLInputElement>();
const dirInput = ref<HTMLInputElement>();

const IMAGE_RE = /\.(jpe?g|png|webp|gif|bmp)$/i;
const isImage = (f: File) => f.type.startsWith("image/") || IMAGE_RE.test(f.name);

function pick() {
  fileInput.value?.click();
}
function pickDir() {
  dirInput.value?.click();
}

function onInput(e: Event) {
  const input = e.target as HTMLInputElement;
  const files = Array.from(input.files || []).filter(isImage);
  input.value = "";
  if (files.length) emit("files", files);
}

function onLeave(e: DragEvent) {
  const el = e.currentTarget as HTMLElement;
  if (!el.contains(e.relatedTarget as Node)) dragging.value = false;
}

function readEntry(entry: any): Promise<File[]> {
  return new Promise((resolve) => {
    if (entry.isFile) {
      entry.file(
        (f: File) => resolve([f]),
        () => resolve([])
      );
    } else if (entry.isDirectory) {
      const reader = entry.createReader();
      const all: File[] = [];
      const readBatch = () =>
        reader.readEntries(
          async (entries: any[]) => {
            if (!entries.length) return resolve(all);
            for (const child of entries) all.push(...(await readEntry(child)));
            readBatch();
          },
          () => resolve(all)
        );
      readBatch();
    } else {
      resolve([]);
    }
  });
}

async function onDrop(e: DragEvent) {
  dragging.value = false;
  const dt = e.dataTransfer;
  if (!dt) return;
  let files: File[] = [];
  const entries = Array.from(dt.items || [])
    .map((it) => (it as any).webkitGetAsEntry?.())
    .filter(Boolean);
  if (entries.length) {
    for (const entry of entries) files.push(...(await readEntry(entry)));
  } else {
    files = Array.from(dt.files || []);
  }
  files = files.filter(isImage);
  if (files.length) emit("files", files);
  else ElMessage.warning("没有找到可上传的图片");
}
</script>

<style scoped lang="scss">
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px 16px;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1.5px dashed var(--el-border-color);
  border-radius: 8px;
  transition:
    border-color 0.15s,
    background 0.15s;
  &.tall {
    min-height: 320px;
  }
  &:hover,
  &.active {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary);
  }
  .dz-icon {
    font-size: 40px;
    color: var(--el-text-color-placeholder);
  }
  .dz-title {
    margin-top: 8px;
    font-size: 14px;
    color: var(--el-text-color-primary);
  }
  .dz-sub {
    margin-top: 6px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
    .link {
      color: var(--el-color-primary);
      cursor: pointer;
    }
    .sep {
      margin: 0 6px;
    }
  }
  .dz-hint {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }
}
</style>
