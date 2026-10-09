<template>
  <div class="app-container notes-admin">
    <el-card shadow="never" class="mb-4">
      <template #header>
        <div class="flex-x-between">
          <span class="font-bold">笔记仓库</span>
          <span class="status">{{ statusText }}</span>
        </div>
      </template>
      <el-form label-width="88px" @submit.prevent>
        <el-form-item label="仓库地址">
          <el-input v-model="form.repoUrl" placeholder="https://github.com/你的名字/notes.git" clearable />
        </el-form-item>
        <el-form-item label="访问令牌">
          <el-input
            v-model="form.accessToken"
            type="password"
            show-password
            :placeholder="source.tokenSet ? '已保存，留空则不修改' : '公开仓库可留空；上传需要写入权限'"
          />
          <el-checkbox v-if="source.tokenSet" v-model="form.clearToken" class="ml-2">清除令牌</el-checkbox>
        </el-form-item>
        <el-form-item label="分支">
          <div class="branch-row">
            <el-select v-model="form.branch" filterable placeholder="先读取分支" class="branch-row__select">
              <el-option v-for="b in branches" :key="b" :label="b" :value="b" />
            </el-select>
            <el-button :loading="branchLoading" @click="loadBranches">读取分支</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存并同步</el-button>
            <el-button :disabled="!source.configured" @click="syncNow">立即检查</el-button>
          </div>
        </el-form-item>
      </el-form>
      <p class="hint">保存后自动克隆这个分支里的 Markdown。之后每分钟看一次有没有新提交，有就更新。随手记写完会提交到该分支的「随手记」目录。</p>
      <el-alert v-if="source.lastError" :title="source.lastError" :type="source.status === 'error' ? 'error' : 'warning'" show-icon :closable="false" />
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="仓库笔记" name="repo">
          <div class="split">
            <aside class="side">
              <el-input v-model="keyword" placeholder="搜索标题或路径" clearable class="mb-2" />
              <el-empty v-if="!filteredDocs.length" description="还没有 Markdown" />
              <button
                v-for="d in filteredDocs"
                :key="d.path"
                type="button"
                class="doc"
                :class="{ 'is-active': d.path === activePath }"
                @click="openDoc(d.path)"
              >
                <strong>{{ d.title }}</strong>
                <span>{{ d.path }}</span>
              </button>
            </aside>
            <section class="main">
              <p v-if="doc" class="path">{{ doc.path }}</p>
              <div v-if="doc" ref="bodyRef" class="note-md" @click="onDocClick" v-html="html"></div>
              <el-empty v-else description="选一篇笔记" />
            </section>
          </div>
        </el-tab-pane>
        <el-tab-pane label="随手记" name="draft">
          <div class="split">
            <aside class="side">
              <el-button type="primary" class="mb-2" @click="createDraft">新建</el-button>
              <el-empty v-if="!drafts.length" description="还没有缓存" />
              <button
                v-for="d in drafts"
                :key="d.id"
                type="button"
                class="doc"
                :class="{ 'is-active': d.id === draftId }"
                @click="openDraft(d.id)"
              >
                <strong>{{ d.title || '未命名' }}</strong>
                <span>{{ d.updateTime }}</span>
              </button>
            </aside>
            <section class="main editor">
              <template v-if="editing">
                <el-input v-model="draftTitle" maxlength="200" placeholder="标题" class="mb-2" />
                <el-input v-model="draftContent" type="textarea" :rows="16" placeholder="直接写。没写完会自动缓存，写完再上传到仓库。" />
                <div class="editor__bar">
                  <span>{{ saveState || '开始写就会缓存' }}</span>
                  <div>
                    <el-button @click="removeDraft">删除</el-button>
                    <el-button type="primary" :loading="uploading" @click="uploadDraft">完成并上传</el-button>
                  </div>
                </div>
              </template>
              <el-empty v-else description="新建一篇，或从左边打开缓存" />
            </section>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import NotesAPI, { type NoteContent, type NoteDoc, type NoteDraft, type NoteSource } from "@/api/tool/notes";
import { hydrateNoteImages, renderNoteMarkdown, revokeNoteAssets } from "./markdown";

const form = ref({ repoUrl: "", branch: "", accessToken: "", clearToken: false });
const source = ref<NoteSource>({
  configured: false,
  repoUrl: "",
  branch: "",
  tokenSet: false,
  status: "idle",
  lastCommit: null,
  lastSyncTime: null,
  lastCheckTime: null,
  lastError: null,
  fileCount: 0,
});
const branches = ref<string[]>([]);
const branchLoading = ref(false);
const saving = ref(false);
const docs = ref<NoteDoc[]>([]);
const keyword = ref("");
const activePath = ref("");
const doc = ref<NoteContent | null>(null);
const bodyRef = ref<HTMLElement>();
const tab = ref("repo");
const drafts = ref<NoteDraft[]>([]);
const draftId = ref("");
const draftTitle = ref("");
const draftContent = ref("");
const editing = ref(false);
const saveState = ref("");
const uploading = ref(false);
const hydrating = ref(false);
let pollTimer: ReturnType<typeof setTimeout> | undefined;
let saveTimer: ReturnType<typeof setTimeout> | undefined;
let saveSeq = 0;

const filteredDocs = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return docs.value;
  return docs.value.filter((d) => d.path.toLowerCase().includes(kw) || (d.title || "").toLowerCase().includes(kw));
});
const html = computed(() => (doc.value ? renderNoteMarkdown(doc.value.content || "", doc.value.path) : ""));
const statusText = computed(() => {
  if (!source.value.configured) return "未配置";
  if (source.value.status === "syncing") return "正在同步…";
  if (source.value.status === "error") return "同步失败";
  const sha = source.value.lastCommit ? source.value.lastCommit.slice(0, 7) : "";
  return `已同步 ${source.value.fileCount} 篇${sha ? " · " + sha : ""}${source.value.lastSyncTime ? " · " + source.value.lastSyncTime : ""}`;
});

watch(html, async () => {
  revokeNoteAssets();
  await nextTick();
  if (bodyRef.value) hydrateNoteImages(bodyRef.value, (path) => NotesAPI.asset(path));
});

watch([draftTitle, draftContent], () => {
  if (hydrating.value || !editing.value) return;
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(saveDraft, 700);
});

async function refreshSource(silent = false) {
  const data = await NotesAPI.source();
  source.value = data;
  if (!silent || !form.value.repoUrl) {
    form.value.repoUrl = data.repoUrl || "";
    form.value.branch = data.branch || "";
    if (data.branch && !branches.value.includes(data.branch)) branches.value = [data.branch, ...branches.value];
  }
  schedule();
}

async function refreshDocs() {
  docs.value = (await NotesAPI.docs()) || [];
}

async function refreshDrafts() {
  drafts.value = (await NotesAPI.drafts()) || [];
}

function schedule() {
  if (pollTimer) clearTimeout(pollTimer);
  const wait = source.value.status === "syncing" ? 2000 : 30000;
  pollTimer = setTimeout(async () => {
    try {
      const prev = source.value.status;
      await refreshSource(true);
      if (prev === "syncing" || source.value.status === "ok") await refreshDocs();
    } catch {
      schedule();
    }
  }, wait);
}

async function loadBranches() {
  if (!form.value.repoUrl.trim()) {
    ElMessage.warning("请先填写仓库地址");
    return;
  }
  branchLoading.value = true;
  try {
    const list = await NotesAPI.branches({
      repoUrl: form.value.repoUrl.trim(),
      accessToken: form.value.accessToken || undefined,
    });
    branches.value = list || [];
    if (!form.value.branch || !branches.value.includes(form.value.branch)) {
      form.value.branch = branches.value.find((b) => b === "main" || b === "master") || branches.value[0] || "";
    }
  } finally {
    branchLoading.value = false;
  }
}

async function save() {
  if (!form.value.repoUrl.trim() || !form.value.branch) {
    ElMessage.warning("请填写仓库地址并选择分支");
    return;
  }
  saving.value = true;
  try {
    source.value = await NotesAPI.saveSource({
      repoUrl: form.value.repoUrl.trim(),
      branch: form.value.branch,
      accessToken: form.value.accessToken,
      clearToken: form.value.clearToken,
    });
    form.value.accessToken = "";
    form.value.clearToken = false;
    ElMessage.success("已保存，开始同步");
    schedule();
  } finally {
    saving.value = false;
  }
}

async function syncNow() {
  source.value = await NotesAPI.sync();
  schedule();
}

async function openDoc(path: string) {
  activePath.value = path;
  doc.value = await NotesAPI.doc(path);
}

function onDocClick(event: MouseEvent) {
  const target = event.target instanceof Element ? event.target : null;
  const link = target?.closest("a");
  const path = link?.getAttribute("data-note-doc");
  if (path) {
    event.preventDefault();
    openDoc(path);
  }
}

function createDraft() {
  hydrating.value = true;
  editing.value = true;
  draftId.value = "";
  draftTitle.value = "";
  draftContent.value = "";
  saveState.value = "";
  nextTick(() => {
    hydrating.value = false;
  });
}

async function openDraft(id: string) {
  hydrating.value = true;
  editing.value = true;
  try {
    const data = await NotesAPI.draft(id);
    draftId.value = data.id;
    draftTitle.value = data.title || "";
    draftContent.value = data.content || "";
    saveState.value = data.updateTime ? `已缓存 ${data.updateTime}` : "已缓存";
  } finally {
    nextTick(() => {
      hydrating.value = false;
    });
  }
}

async function saveDraft() {
  if (!draftTitle.value.trim() && !draftContent.value && !draftId.value) return;
  const my = ++saveSeq;
  const snapshot = { id: draftId.value, title: draftTitle.value, content: draftContent.value };
  saveState.value = "缓存中";
  try {
    const saved = await NotesAPI.saveDraft({ id: snapshot.id || undefined, title: snapshot.title, content: snapshot.content });
    if (my !== saveSeq) return;
    draftId.value = saved.id;
    saveState.value = saved.updateTime ? `已缓存 ${saved.updateTime}` : "已缓存";
    const row: NoteDraft = { id: saved.id, title: saved.title || "未命名", updateTime: saved.updateTime };
    const idx = drafts.value.findIndex((d) => d.id === saved.id);
    if (idx >= 0) drafts.value.splice(idx, 1, row);
    else drafts.value.unshift(row);
  } catch (e) {
    if (my !== saveSeq) return;
    saveState.value = e instanceof Error ? `缓存失败：${e.message}` : "缓存失败";
  }
}

async function removeDraft() {
  if (!draftId.value) {
    editing.value = false;
    return;
  }
  try {
    await ElMessageBox.confirm("还没上传的内容会丢掉。", "删除这篇缓存？", { type: "warning" });
  } catch {
    return;
  }
  await NotesAPI.deleteDraft(draftId.value);
  drafts.value = drafts.value.filter((d) => d.id !== draftId.value);
  editing.value = false;
  draftId.value = "";
  ElMessage.success("已删除");
}

async function uploadDraft() {
  if (!draftContent.value.trim()) {
    ElMessage.warning("内容是空的，还不能上传");
    return;
  }
  if (saveTimer) clearTimeout(saveTimer);
  await saveDraft();
  if (!draftId.value) return;
  try {
    await ElMessageBox.confirm("会提交到当前分支的「随手记」目录，这篇缓存随后删除。", "上传到仓库？");
  } catch {
    return;
  }
  uploading.value = true;
  try {
    const result = await NotesAPI.uploadDraft(draftId.value);
    drafts.value = drafts.value.filter((d) => d.id !== draftId.value);
    editing.value = false;
    draftId.value = "";
    draftTitle.value = "";
    draftContent.value = "";
    ElMessage.success("已上传");
    tab.value = "repo";
    await refreshDocs();
    await refreshSource(true);
    await openDoc(result.path);
  } finally {
    uploading.value = false;
  }
}

onMounted(async () => {
  await refreshSource();
  await Promise.all([refreshDocs(), refreshDrafts()]);
});
onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer);
  if (saveTimer) clearTimeout(saveTimer);
  revokeNoteAssets();
});
</script>

<style scoped lang="scss">
.status { color: var(--el-text-color-secondary); font-size: 13px; }
.hint { margin: 0 0 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.branch-row { display: flex; flex-wrap: wrap; gap: 8px; width: 100%; }
.branch-row__select { width: 240px; }
.ml-2 { margin-left: 8px; }
.mb-2 { margin-bottom: 8px; }
.mb-4 { margin-bottom: 16px; }
.split { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 16px; min-height: 520px; }
.side, .main { min-height: 0; max-height: 70vh; overflow: auto; }
.doc {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  margin: 0 0 6px;
  padding: 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  strong { font-weight: 650; }
  span { color: var(--el-text-color-secondary); font-size: 12px; word-break: break-all; }
  &:hover, &.is-active { background: var(--el-color-primary-light-9); }
}
.path { margin: 0 0 8px; color: var(--el-text-color-secondary); font-size: 12px; word-break: break-all; }
.editor__bar { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; color: var(--el-text-color-secondary); }
.note-md {
  line-height: 1.75;
  word-break: break-word;
  :deep(pre) { overflow: auto; padding: 12px; border-radius: 8px; background: #14242c; color: #f3f6f8; }
  :deep(code) { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
  :deep(img) { max-width: 100%; }
  :deep(a) { color: var(--el-color-primary); }
}
</style>
