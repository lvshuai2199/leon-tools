<template>
  <div class="app-container wallpaper-admin">
    <!-- 左栏：组列表 -->
    <aside class="group-pane">
      <div class="group-head">
        <span class="pane-title">壁纸组</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openGroupDialog()">
          新建组
        </el-button>
      </div>
      <el-scrollbar v-loading="groupLoading" class="group-list">
        <div
          v-for="(g, idx) in groups"
          :key="g.id"
          class="group-row"
          :class="{ active: g.id === currentId, dragging: groupDrag.from === idx }"
          draggable="true"
          @click="selectGroup(g.id)"
          @dragstart="onGroupDragStart(idx, $event)"
          @dragover.prevent="onGroupDragOver(idx)"
          @dragend="onGroupDragEnd"
        >
          <el-icon class="drag-handle"><Rank /></el-icon>
          <div class="group-info">
            <div class="group-name" :title="g.name">{{ g.name }}</div>
            <div class="group-sub">
              <span class="group-key">{{ g.groupKey }}</span>
              <span>{{ g.imageCount ?? 0 }} 张</span>
            </div>
          </div>
          <el-tooltip content="对外开放" placement="top" :show-after="400">
            <el-switch
              :model-value="toBool(g.isPublic)"
              size="small"
              @click.stop
              @change="(v: any) => togglePublic(g, !!v)"
            />
          </el-tooltip>
        </div>
        <el-empty
          v-if="!groupLoading && !groups.length"
          :image-size="60"
          description="还没有壁纸组"
        />
      </el-scrollbar>
    </aside>

    <!-- 右栏：当前组 -->
    <section class="image-pane">
      <template v-if="currentGroup">
        <div class="group-header">
          <div class="gh-left">
            <div class="gh-name">
              {{ currentGroup.name }}
              <el-tag v-if="!toBool(currentGroup.isPublic)" size="small" type="info">未开放</el-tag>
            </div>
            <div class="gh-sub">
              <span class="group-key">{{ currentGroup.groupKey }}</span>
              <span v-if="currentGroup.description" class="gh-desc">
                {{ currentGroup.description }}
              </span>
            </div>
          </div>
          <div class="gh-actions">
            <el-button :icon="CopyDocument" @click="copyRandomUrl(currentGroup)">
              复制接口
            </el-button>
            <el-button :icon="Edit" @click="openGroupDialog(currentGroup)">编辑</el-button>
            <el-button :icon="Delete" text @click="removeGroup(currentGroup)">删除组</el-button>
          </div>
        </div>

        <div class="toolbar">
          <div class="tb-left">
            <el-button type="primary" :icon="Upload" @click="uploadVisible = true">
              上传图片
            </el-button>
            <el-checkbox
              v-if="images.length"
              :model-value="allChecked"
              :indeterminate="selectedIds.length > 0 && !allChecked"
              @change="toggleAll"
            >
              全选
            </el-checkbox>
            <span v-if="selectedIds.length" class="sel-count">
              已选 {{ selectedIds.length }} 张
            </span>
          </div>
          <div class="tb-right">
            <el-button :disabled="!selectedIds.length" @click="batch('enable')">批量启用</el-button>
            <el-button :disabled="!selectedIds.length" @click="batch('disable')">
              批量停用
            </el-button>
            <el-button
              :disabled="!selectedIds.length || otherGroups.length === 0"
              @click="openMove(selectedIds)"
            >
              移到其他组
            </el-button>
            <el-button :disabled="!selectedIds.length" type="danger" plain @click="batch('delete')">
              删除
            </el-button>
            <el-radio-group v-model="viewMode" class="view-switch">
              <el-radio-button value="card" title="卡片视图">
                <el-icon><Grid /></el-icon>
              </el-radio-button>
              <el-radio-button value="table" title="列表视图">
                <el-icon>
                  <!-- 列表视图：三条横线（Element Plus 图标库里没有纯三横线图标） -->
                  <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
                    <path
                      fill="currentColor"
                      d="M160 224h704v64H160zm0 256h704v64H160zm0 256h704v64H160z"
                    />
                  </svg>
                </el-icon>
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <el-scrollbar
          ref="imageScrollRef"
          v-loading="imageLoading && !images.length"
          class="image-area"
          @scroll="onImageScroll"
        >
          <!-- 空组：直接显示拖拽区 -->
          <DropZone v-if="!imageLoading && !images.length" tall @files="onEmptyDrop" />

          <!-- 卡片网格 -->
          <div v-else-if="viewMode === 'card'" class="card-grid">
            <div
              v-for="(img, idx) in images"
              :key="img.id"
              class="img-card"
              :class="{
                disabled: !toBool(img.enabled),
                checked: selectedSet.has(img.id),
                dragging: imageDrag.from === idx,
              }"
              draggable="true"
              @dragstart="onImageDragStart(idx, $event)"
              @dragover.prevent="onImageDragOver(idx)"
              @dragend="onImageDragEnd"
            >
              <div class="thumb">
                <el-image
                  :src="wallpaperFileUrl(img.thumbUrl || img.url)"
                  :preview-src-list="previewList"
                  :initial-index="idx"
                  fit="cover"
                  lazy
                  preview-teleported
                  hide-on-click-modal
                  draggable="false"
                />
                <el-checkbox
                  class="card-check"
                  :model-value="selectedSet.has(img.id)"
                  @change="() => toggleOne(img.id)"
                  @click.stop
                />
                <el-switch
                  class="card-switch"
                  size="small"
                  :model-value="toBool(img.enabled)"
                  @change="(v: any) => toggleEnabled(img, !!v)"
                  @click.stop
                />
              </div>
              <div class="card-foot">
                <span class="card-title" :title="img.title">{{ img.title || "未命名" }}</span>
                <el-dropdown trigger="click" @command="(c: string) => onImageCommand(c, img)">
                  <el-icon class="more-btn" @click.stop><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">改标题</el-dropdown-item>
                      <el-dropdown-item command="replace">换图</el-dropdown-item>
                      <el-dropdown-item command="move" :disabled="otherGroups.length === 0">
                        移组
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided class="danger-item">
                        删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </div>

          <!-- 表格 -->
          <el-table
            v-else
            ref="tableRef"
            class="wp-table"
            :data="images"
            row-key="id"
            :row-class-name="({ row }: any) => (toBool(row.enabled) ? '' : 'row-disabled')"
            @selection-change="onTableSelection"
          >
            <el-table-column type="selection" width="36" reserve-selection />
            <el-table-column label="缩略图" width="72">
              <template #default="{ row, $index }">
                <el-image
                  class="table-thumb"
                  :src="wallpaperFileUrl(row.thumbUrl || row.url)"
                  :preview-src-list="previewList"
                  :initial-index="$index"
                  fit="cover"
                  preview-teleported
                  hide-on-click-modal
                />
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="title" min-width="120" show-overflow-tooltip />
            <el-table-column label="分辨率" width="84">
              <template #default="{ row }">
                {{ row.width && row.height ? `${row.width}×${row.height}` : "-" }}
              </template>
            </el-table-column>
            <el-table-column label="文件大小" width="76">
              <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="启用" width="56">
              <template #default="{ row }">
                <el-switch
                  size="small"
                  :model-value="toBool(row.enabled)"
                  @change="(v: any) => toggleEnabled(row as WallpaperImageVO, !!v)"
                />
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="96">
              <template #default="{ row }">
                <div class="time-cell">
                  <div>{{ splitDateTime(row.createTime)[0] }}</div>
                  <div class="time-sub">{{ splitDateTime(row.createTime)[1] }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" class-name="op-col">
              <template #default="{ row }">
                <el-button
                  link
                  size="small"
                  type="primary"
                  @click="onImageCommand('rename', row as WallpaperImageVO)"
                >
                  改标题
                </el-button>
                <el-button
                  link
                  size="small"
                  type="primary"
                  @click="onImageCommand('replace', row as WallpaperImageVO)"
                >
                  换图
                </el-button>
                <el-button
                  link
                  size="small"
                  type="primary"
                  :disabled="otherGroups.length === 0"
                  @click="onImageCommand('move', row as WallpaperImageVO)"
                >
                  移组
                </el-button>
                <el-button
                  link
                  size="small"
                  type="danger"
                  @click="onImageCommand('delete', row as WallpaperImageVO)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="images.length" class="load-tip">
            <span v-if="imageLoading">加载中…</span>
            <el-button v-else-if="hasMore" link type="primary" @click="loadMore">
              加载更多
            </el-button>
            <span v-else>共 {{ total }} 张</span>
          </div>
        </el-scrollbar>
      </template>

      <el-empty v-else-if="!groupLoading" class="no-group" description="先在左边新建一个壁纸组">
        <el-button type="primary" :icon="Plus" @click="openGroupDialog()">新建组</el-button>
      </el-empty>
    </section>

    <GroupDialog
      v-model="groupDialogVisible"
      :group="editingGroup"
      @saved="onGroupSaved"
      @token-changed="onTokenChanged"
    />

    <UploadDrawer
      ref="uploadRef"
      v-model="uploadVisible"
      :group-id="currentGroup?.id"
      :group-name="currentGroup?.name"
      @uploaded="onUploaded"
    />

    <el-dialog v-model="moveVisible" title="移到其他组" width="380px" append-to-body>
      <el-select v-model="moveTarget" placeholder="选择目标组" style="width: 100%">
        <el-option
          v-for="g in otherGroups"
          :key="g.id"
          :label="`${g.name}（${g.groupKey}）`"
          :value="g.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="moveTarget == null"
          :loading="moving"
          @click="confirmMove"
        >
          移动
        </el-button>
      </template>
    </el-dialog>

    <input ref="replaceInput" type="file" accept="image/*" hidden @change="onReplaceFile" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: "WallpaperAdmin" });

import {
  CopyDocument,
  Delete,
  Edit,
  Grid,
  MoreFilled,
  Plus,
  Rank,
  Upload,
} from "@element-plus/icons-vue";
import { ElLoading } from "element-plus";
import WallpaperAPI, {
  formatDateTime,
  formatFileSize,
  reassignSorts,
  wallpaperFileUrl,
  wallpaperRandomUrl,
  type WallpaperBatchAction,
  type WallpaperGroupVO,
  type WallpaperId,
  type WallpaperImageVO,
} from "@/api/tool/wallpaper";
import GroupDialog from "./components/GroupDialog.vue";
import UploadDrawer from "./components/UploadDrawer.vue";
import DropZone from "./components/DropZone.vue";

const PAGE_SIZE = 60;
const VIEW_KEY = "wallpaper-admin-view";

const toBool = (v: unknown) => v === true || v === 1 || v === "1" || v === "true";
/** 表格里上传时间分两行显示（日期 / 时间），省出宽度给标题 */
const splitDateTime = (v?: string) => {
  const [date, time = ""] = formatDateTime(v).split(" ");
  return [date, time];
};
/** 弹框点「取消」/ 关闭时返回 false，不把 "cancel" 抛出去（避免控制台 Uncaught (in promise)） */
const confirmed = (p: Promise<unknown>) =>
  p.then(
    () => true,
    () => false
  );

/* ---------------- 组 ---------------- */
const groups = ref<WallpaperGroupVO[]>([]);
const groupLoading = ref(false);
const currentId = ref<WallpaperId>();
const currentGroup = computed(() => groups.value.find((g) => g.id === currentId.value));
const otherGroups = computed(() => groups.value.filter((g) => g.id !== currentId.value));

async function loadGroups(keepId?: WallpaperId) {
  groupLoading.value = true;
  try {
    groups.value = (await WallpaperAPI.listGroups()) || [];
    const want = keepId ?? currentId.value;
    if (want != null && groups.value.some((g) => g.id === want)) {
      if (currentId.value !== want) selectGroup(want);
    } else if (groups.value.length) {
      selectGroup(groups.value[0].id);
    } else {
      currentId.value = undefined;
      images.value = [];
    }
  } catch {
    // request 拦截器已提示（会话过期时会提示并跳登录），页面这里吞掉，避免 Uncaught (in promise)
  } finally {
    groupLoading.value = false;
  }
}

function selectGroup(id: WallpaperId) {
  if (currentId.value === id) return;
  currentId.value = id;
  clearSelection();
  loadImages(true);
}

async function togglePublic(g: WallpaperGroupVO, v: boolean) {
  const old = g.isPublic;
  g.isPublic = v;
  try {
    await WallpaperAPI.updateGroup(g.id, {
      name: g.name,
      groupKey: g.groupKey,
      description: g.description,
      sort: g.sort,
      isPublic: v,
    });
  } catch {
    g.isPublic = old;
  }
}

const groupDialogVisible = ref(false);
const editingGroup = ref<WallpaperGroupVO | null>(null);

function openGroupDialog(g?: WallpaperGroupVO) {
  editingGroup.value = g ?? null;
  groupDialogVisible.value = true;
}

function onGroupSaved(saved?: WallpaperGroupVO) {
  loadGroups(saved?.id ?? editingGroup.value?.id);
}

function onTokenChanged(token: string) {
  const g = groups.value.find((x) => x.id === editingGroup.value?.id);
  if (g) g.token = token;
}

/** 删除组确认框正文：第一行加粗「删除「组名」？」，第二行说明后果（VNode 渲染，组名不会被当成 HTML） */
function deleteGroupMessage(name: string, detail: string) {
  return h("div", { class: "wp-del-msg" }, [
    h("div", { style: "font-weight: 600; margin-bottom: 4px" }, `删除「${name}」？`),
    h("div", detail),
  ]);
}
const DELETE_CONFIRM_OPTS = {
  type: "warning" as const,
  confirmButtonClass: "el-button--danger wp-danger-confirm",
};

async function removeGroup(g: WallpaperGroupVO) {
  if (!(g.imageCount ?? 0)) {
    const ok = await confirmed(
      ElMessageBox.confirm(deleteGroupMessage(g.name, "接口地址会失效。"), "删除组", {
        ...DELETE_CONFIRM_OPTS,
        confirmButtonText: "删除",
      })
    );
    if (!ok) return;
  }
  try {
    const res = await WallpaperAPI.deleteGroup(g.id);
    if (res.conflict) {
      const ok = await confirmed(
        ElMessageBox.confirm(
          deleteGroupMessage(
            g.name,
            `组内 ${res.imageCount ?? g.imageCount ?? 0} 张图会一起删除，接口地址也会失效。`
          ),
          "删除组",
          { ...DELETE_CONFIRM_OPTS, confirmButtonText: "一起删除" }
        )
      );
      if (!ok) {
        // 本地张数可能过期（例如别处上传过），顺便同步一下
        refreshGroupCounts();
        return;
      }
      await WallpaperAPI.deleteGroup(g.id, true);
    }
  } catch {
    return; // 接口报错，request 拦截器已提示
  }
  ElMessage.success("已删除");
  currentId.value = undefined;
  loadGroups();
}

function copyRandomUrl(g: WallpaperGroupVO) {
  if (!g.token) ElMessage.warning("这个组还没有访问密钥，请先在编辑里生成");
  const okMsg = toBool(g.isPublic)
    ? "接口地址已复制"
    : "接口地址已复制（该组未开放，打开「对外开放」后地址才能取到图）";
  copyText(wallpaperRandomUrl(g), okMsg);
}

/* 组拖动排序（原生 HTML5 拖拽） */
const groupDrag = reactive<{ from: number; snapshot: string }>({ from: -1, snapshot: "" });

function onGroupDragStart(idx: number, e: DragEvent) {
  groupDrag.from = idx;
  groupDrag.snapshot = groups.value.map((g) => g.id).join(",");
  e.dataTransfer?.setData("text/plain", "group");
  if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
}
function onGroupDragOver(idx: number) {
  if (groupDrag.from < 0 || idx === groupDrag.from) return;
  const list = groups.value;
  const [item] = list.splice(groupDrag.from, 1);
  list.splice(idx, 0, item);
  groupDrag.from = idx;
}
async function onGroupDragEnd() {
  groupDrag.from = -1;
  const ids = groups.value.map((g) => g.id);
  if (ids.join(",") === groupDrag.snapshot) return;
  try {
    await WallpaperAPI.sortGroups(ids);
    reassignSorts(groups.value);
  } catch {
    loadGroups();
  }
}

/* ---------------- 图片 ---------------- */
const images = ref<WallpaperImageVO[]>([]);
const imageLoading = ref(false);
const pageNum = ref(1);
const total = ref(0);
const hasMore = computed(() => images.value.length < total.value);
const previewList = computed(() => images.value.map((i) => wallpaperFileUrl(i.url)));
const viewMode = ref<"card" | "table">((localStorage.getItem(VIEW_KEY) as any) || "card");
watch(viewMode, (v) => {
  localStorage.setItem(VIEW_KEY, v);
  clearSelection();
});

let loadSeq = 0;
async function loadImages(reset = false) {
  if (currentId.value == null) return;
  if (reset) {
    pageNum.value = 1;
    images.value = [];
    total.value = 0;
  }
  const seq = ++loadSeq;
  const gid = currentId.value;
  imageLoading.value = true;
  try {
    const page = await WallpaperAPI.getImagePage({
      groupId: gid,
      current: pageNum.value,
      size: PAGE_SIZE,
    });
    if (seq !== loadSeq) return;
    const records = page?.records || [];
    images.value = reset ? records : images.value.concat(records);
    total.value = Number(page?.total ?? images.value.length);
  } catch {
    // request 拦截器已提示，这里吞掉
  } finally {
    if (seq === loadSeq) imageLoading.value = false;
  }
}

function loadMore() {
  if (imageLoading.value || !hasMore.value) return;
  pageNum.value++;
  loadImages();
}

function onImageScroll({ scrollTop }: { scrollTop: number }) {
  const wrap = imageScrollRef.value?.wrapRef as HTMLElement | undefined;
  if (!wrap) return;
  if (scrollTop + wrap.clientHeight >= wrap.scrollHeight - 200) loadMore();
}

const imageScrollRef = ref<any>();

/** 上传、批量操作后刷新：图片从头拉，组张数同步 */
async function refreshAll() {
  clearSelection();
  await Promise.all([loadImages(true), refreshGroupCounts()]);
}

async function refreshGroupCounts() {
  let list: WallpaperGroupVO[];
  try {
    list = (await WallpaperAPI.listGroups()) || [];
  } catch {
    return; // request 拦截器已提示
  }
  const map = new Map(list.map((g) => [g.id, g]));
  groups.value.forEach((g) => {
    const n = map.get(g.id);
    if (n) g.imageCount = n.imageCount;
  });
}

async function toggleEnabled(img: WallpaperImageVO, v: boolean) {
  const old = img.enabled;
  img.enabled = v;
  try {
    await WallpaperAPI.updateImage(img.id, { enabled: v });
  } catch {
    img.enabled = old;
  }
}

/* 选择 */
const selectedIds = ref<WallpaperId[]>([]);
const selectedSet = computed(() => new Set(selectedIds.value));
const allChecked = computed(
  () => images.value.length > 0 && selectedIds.value.length === images.value.length
);
const tableRef = ref<any>();

function toggleOne(id: WallpaperId) {
  const i = selectedIds.value.indexOf(id);
  if (i >= 0) selectedIds.value.splice(i, 1);
  else selectedIds.value.push(id);
}
function toggleAll(v: any) {
  if (viewMode.value === "table") {
    tableRef.value?.toggleAllSelection();
    return;
  }
  selectedIds.value = v ? images.value.map((i) => i.id) : [];
}
function onTableSelection(rows: WallpaperImageVO[]) {
  selectedIds.value = rows.map((r) => r.id);
}
function clearSelection() {
  selectedIds.value = [];
  tableRef.value?.clearSelection?.();
}

/* 批量 */
async function batch(
  action: WallpaperBatchAction,
  ids = selectedIds.value,
  targetGroupId?: WallpaperId
) {
  if (!ids.length) return false;
  if (action === "delete") {
    const ok = await confirmed(
      ElMessageBox.confirm(`确定删除选中的 ${ids.length} 张图片？删除后不能恢复。`, "删除图片", {
        type: "warning",
        confirmButtonText: "删除",
      })
    );
    if (!ok) return false;
  }
  try {
    await WallpaperAPI.batch({ ids: [...ids], action, targetGroupId });
  } catch {
    return false; // request 拦截器已提示
  }
  ElMessage.success("操作成功");
  await refreshAll();
  return true;
}

/* 移组 */
const moveVisible = ref(false);
const moveTarget = ref<WallpaperId>();
const moveIds = ref<WallpaperId[]>([]);
const moving = ref(false);

function openMove(ids: WallpaperId[]) {
  moveIds.value = [...ids];
  moveTarget.value = undefined;
  moveVisible.value = true;
}
async function confirmMove() {
  if (moveTarget.value == null) return;
  moving.value = true;
  try {
    if (await batch("move", moveIds.value, moveTarget.value)) moveVisible.value = false;
  } finally {
    moving.value = false;
  }
}

/* 单张操作 */
const replaceInput = ref<HTMLInputElement>();
let replacing: WallpaperImageVO | null = null;

async function onImageCommand(cmd: string, img: WallpaperImageVO) {
  if (cmd === "rename") {
    const r = await ElMessageBox.prompt("新的标题", "改标题", {
      inputValue: img.title || "",
      inputValidator: (v: string) => (v && v.length > 100 ? "标题不超过 100 个字" : true),
    }).catch(() => null);
    if (!r) return;
    const title = r.value?.trim() || "";
    try {
      await WallpaperAPI.updateImage(img.id, { title });
    } catch {
      return; // request 拦截器已提示
    }
    img.title = title;
    ElMessage.success("已修改");
  } else if (cmd === "replace") {
    replacing = img;
    replaceInput.value?.click();
  } else if (cmd === "move") {
    openMove([img.id]);
  } else if (cmd === "delete") {
    await batch("delete", [img.id]);
  }
}

async function onReplaceFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file || !replacing) return;
  const target = replacing;
  replacing = null;
  const loading = ElLoading.service({ text: "正在换图…" });
  try {
    const res = await WallpaperAPI.replaceImage(target.id, file);
    if (res && typeof res === "object") Object.assign(target, res);
    else await loadImages(true);
    ElMessage.success("已换图");
  } catch {
    // request 拦截器已提示
  } finally {
    loading.close();
  }
}

/* 图片拖动排序 */
const imageDrag = reactive<{ from: number; snapshot: string }>({ from: -1, snapshot: "" });

function onImageDragStart(idx: number, e: DragEvent) {
  imageDrag.from = idx;
  imageDrag.snapshot = images.value.map((i) => i.id).join(",");
  e.dataTransfer?.setData("text/plain", "image");
  if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
}
function onImageDragOver(idx: number) {
  if (imageDrag.from < 0 || idx === imageDrag.from) return;
  const list = images.value;
  const [item] = list.splice(imageDrag.from, 1);
  list.splice(idx, 0, item);
  imageDrag.from = idx;
}
async function onImageDragEnd() {
  imageDrag.from = -1;
  const ids = images.value.map((i) => i.id);
  if (ids.join(",") === imageDrag.snapshot) return;
  try {
    await WallpaperAPI.sortImages(ids);
    reassignSorts(images.value);
  } catch {
    loadImages(true);
  }
}

/* 上传 */
const uploadVisible = ref(false);
const uploadRef = ref<InstanceType<typeof UploadDrawer>>();

function onEmptyDrop(files: File[]) {
  uploadVisible.value = true;
  nextTick(() => uploadRef.value?.addFiles(files));
}
function onUploaded() {
  refreshAll();
}

function copyText(text: string, okMsg = "已复制") {
  navigator.clipboard.writeText(text).then(
    () => ElMessage.success(okMsg),
    () => ElMessageBox.alert(text, "复制失败，请手动复制")
  );
}

onMounted(() => loadGroups());
</script>

<style scoped lang="scss">
.wallpaper-admin {
  display: flex;
  gap: 16px;
  height: calc(100vh - var(--navbar-height, 50px) - var(--tags-view-height, 34px));
  min-height: 480px;
  box-sizing: border-box;
}

.group-pane {
  display: flex;
  flex: 0 0 240px;
  flex-direction: column;
  width: 240px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  .pane-title {
    font-size: 14px;
    font-weight: 600;
  }
}
.group-list {
  flex: 1;
  padding: 6px;
}
.group-row {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 8px 8px 4px;
  margin-bottom: 2px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.12s;
  &:hover {
    background: var(--el-fill-color-light);
  }
  &.active {
    background: var(--el-color-primary-light-9);
    .group-name {
      color: var(--el-color-primary);
    }
  }
  &.dragging {
    opacity: 0.5;
  }
  .drag-handle {
    color: var(--el-text-color-placeholder);
    cursor: grab;
  }
  .group-info {
    flex: 1;
    min-width: 0;
  }
  .group-name {
    overflow: hidden;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .group-sub {
    display: flex;
    gap: 8px;
    margin-top: 2px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
.group-key {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.image-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.group-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  .gh-left {
    min-width: 0;
  }
  .gh-name {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 18px;
    font-weight: 600;
  }
  .gh-sub {
    display: flex;
    gap: 12px;
    margin-top: 4px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
  .gh-desc {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .gh-actions {
    flex-shrink: 0;
  }
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  .tb-left,
  .tb-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }
  .tb-right .el-button + .el-button {
    margin-left: 0;
  }
  .sel-count {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
  .view-switch {
    margin-left: 8px;
  }
}
.image-area {
  flex: 1;
  min-height: 0;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.img-card {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    box-shadow 0.15s,
    opacity 0.15s,
    border-color 0.15s;
  &:hover {
    box-shadow: var(--el-box-shadow-light);
  }
  &.checked {
    border-color: var(--el-color-primary);
  }
  &.disabled .thumb,
  &.disabled .card-title {
    opacity: 0.45;
  }
  &.dragging {
    opacity: 0.4;
  }
  .thumb {
    position: relative;
    aspect-ratio: 16 / 9;
    background: var(--el-fill-color-light);
    :deep(.el-image) {
      display: block;
      width: 100%;
      height: 100%;
    }
  }
  .card-check {
    position: absolute;
    top: 4px;
    left: 8px;
    height: auto;
    :deep(.el-checkbox__inner) {
      box-shadow: 0 0 0 2px rgb(255 255 255 / 70%);
    }
  }
  .card-switch {
    position: absolute;
    top: 6px;
    right: 8px;
  }
  .card-foot {
    display: flex;
    gap: 6px;
    align-items: center;
    padding: 6px 8px 6px 10px;
  }
  .card-title {
    flex: 1;
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .more-btn {
    padding: 4px;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    border-radius: 4px;
    &:hover {
      background: var(--el-fill-color);
    }
  }
}
.table-thumb {
  display: block;
  width: 56px;
  height: 32px;
  border-radius: 4px;
}
/* 表格单元格左右内边距收紧到 8px，1280 宽度下不出现横向滚动，剩余宽度都给标题 */
.wp-table :deep(.cell) {
  padding: 0 8px;
}
.time-cell {
  line-height: 1.3;
  .time-sub {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
/* 操作列：小号链接按钮，间距收紧，170px 内不换行 */
:deep(.op-col .cell) {
  white-space: nowrap;
  .el-button + .el-button {
    margin-left: 6px;
  }
}
:deep(.row-disabled) td {
  opacity: 0.5;
}
.load-tip {
  padding: 14px 0 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.no-group {
  margin: auto;
}
:global(.danger-item) {
  color: var(--el-color-danger) !important;
}
:global(.el-button.wp-danger-confirm) {
  --el-button-text-color: #fff;
  --el-button-bg-color: var(--el-color-danger);
  --el-button-border-color: var(--el-color-danger);
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: var(--el-color-danger-light-3);
  --el-button-hover-border-color: var(--el-color-danger-light-3);
  --el-button-active-bg-color: var(--el-color-danger-dark-2);
  --el-button-active-border-color: var(--el-color-danger-dark-2);
  opacity: 1;
}
</style>
