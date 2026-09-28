<template>
  <el-drawer
    :model-value="modelValue"
    :title="`上传图片到「${groupName || ''}」`"
    size="440px"
    class="wallpaper-upload-drawer"
    append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="upload-drawer">
      <DropZone @files="addFiles" />

      <div v-if="tasks.length" class="summary">
        <span>共 {{ tasks.length }} 张，已完成 {{ doneCount }}，失败 {{ failCount }}</span>
        <span>
          <el-button v-if="retryableCount" link type="primary" @click="retryAll">
            全部重试
          </el-button>
          <el-button link :disabled="uploading" @click="clearFinished">清除已完成</el-button>
        </span>
      </div>

      <div class="task-list">
        <div v-for="t in tasks" :key="t.uid" class="task" :class="t.status">
          <div class="task-main">
            <div class="task-name" :title="t.file.name">{{ t.file.name }}</div>
            <div class="task-meta">
              <span>{{ formatFileSize(t.file.size) }}</span>
              <span v-if="t.status === 'error'" class="err">{{ t.error || "上传失败" }}</span>
              <span v-else-if="t.status === 'done'" class="ok">已完成</span>
              <span v-else-if="t.status === 'waiting'">等待中</span>
            </div>
            <el-progress
              v-if="t.status === 'uploading'"
              :percentage="t.percent"
              :stroke-width="4"
              :show-text="false"
            />
          </div>
          <template v-if="t.status === 'error'">
            <el-button v-if="t.retryable" size="small" type="danger" plain @click="retry(t)">
              重试
            </el-button>
            <el-button v-else size="small" @click="remove(t)">移除</el-button>
          </template>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import WallpaperAPI, {
  formatFileSize,
  WALLPAPER_MAX_FILE_SIZE,
  WallpaperUploadError,
  type WallpaperId,
} from "@/api/tool/wallpaper";
import DropZone from "./DropZone.vue";

const props = defineProps<{ modelValue: boolean; groupId?: WallpaperId; groupName?: string }>();
const emit = defineEmits(["update:modelValue", "uploaded"]);

type Status = "waiting" | "uploading" | "done" | "error";
interface Task {
  uid: number;
  file: File;
  groupId: WallpaperId;
  status: Status;
  percent: number;
  error?: string;
  /** 失败后能否重试：只有网络错误/超时和 HTTP 5xx 可以；格式、大小等业务错误只能移除 */
  retryable?: boolean;
}

const CONCURRENCY = 3;
let seq = 0;
const tasks = ref<Task[]>([]);
let running = 0;

const doneCount = computed(() => tasks.value.filter((t) => t.status === "done").length);
const failCount = computed(() => tasks.value.filter((t) => t.status === "error").length);
const retryableCount = computed(
  () => tasks.value.filter((t) => t.status === "error" && t.retryable).length
);
const uploading = computed(() =>
  tasks.value.some((t) => t.status === "waiting" || t.status === "uploading")
);

function addFiles(files: File[]) {
  if (props.groupId == null) return;
  for (const file of files) {
    tasks.value.push({ uid: ++seq, file, groupId: props.groupId, status: "waiting", percent: 0 });
  }
  pump();
}

function pump() {
  while (running < CONCURRENCY) {
    const next = tasks.value.find((t) => t.status === "waiting");
    if (!next) break;
    run(next);
  }
}

async function run(t: Task) {
  if (t.file.size > WALLPAPER_MAX_FILE_SIZE) {
    // 后端单张上限 20MB，超出时后端只回英文 "Maximum upload size exceeded"，前端先拦下
    t.status = "error";
    t.error = `图片超过 ${WALLPAPER_MAX_FILE_SIZE / 1024 / 1024}MB，无法上传`;
    t.retryable = false;
    if (!uploading.value) emit("uploaded");
    return;
  }
  running++;
  t.status = "uploading";
  t.percent = 0;
  t.error = undefined;
  t.retryable = undefined;
  try {
    await WallpaperAPI.uploadImage(t.groupId, t.file, (p) => (t.percent = p));
    t.status = "done";
    t.percent = 100;
  } catch (e: any) {
    t.status = "error";
    t.error = typeof e === "string" ? e : e?.message || "上传失败";
    // 不是 WallpaperUploadError 的异常（理论上不会出现）按可重试处理
    t.retryable = e instanceof WallpaperUploadError ? e.retryable : true;
  } finally {
    running--;
    pump();
    if (!uploading.value) emit("uploaded");
  }
}

function retry(t: Task) {
  t.status = "waiting";
  pump();
}

function retryAll() {
  tasks.value.forEach((t) => {
    if (t.status === "error" && t.retryable) t.status = "waiting";
  });
  pump();
}

function remove(t: Task) {
  tasks.value = tasks.value.filter((x) => x.uid !== t.uid);
}

function clearFinished() {
  tasks.value = tasks.value.filter((t) => t.status !== "done");
}

defineExpose({ addFiles });
</script>

<style scoped lang="scss">
.upload-drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}
.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.task-list {
  flex: 1;
  overflow-y: auto;
}
.task {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  &.error {
    background: #fdf0f0;
    .task-name {
      color: #d14343;
    }
  }
  .task-main {
    flex: 1;
    min-width: 0;
  }
  .task-name {
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .task-meta {
    display: flex;
    gap: 10px;
    margin: 2px 0 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    .err {
      color: #d14343;
    }
    .ok {
      color: var(--el-color-success);
    }
  }
}
</style>

<style lang="scss">
/* 抽屉挂在 body 下，scoped 样式够不到；用专属 class 只收紧这个抽屉的标题与内容间距（16px） */
.wallpaper-upload-drawer {
  .el-drawer__header {
    padding-bottom: 0;
    margin-bottom: 0;
  }
  .el-drawer__body {
    padding-top: 16px;
  }
}
</style>
