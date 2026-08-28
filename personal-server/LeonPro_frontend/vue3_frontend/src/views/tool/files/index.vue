<template>
  <div class="files-container">
    <el-tabs v-model="activeTab">
      <!-- 批量改名 -->
      <el-tab-pane label="批量改名" name="rename">
        <el-card shadow="never">
          <el-row :gutter="16">
            <el-col :span="8" :xs="24">
              <el-text tag="b" size="small" class="mb-1 block">选择要处理的文件</el-text>
              <input ref="renameFilesRef" type="file" multiple class="file-input" />
            </el-col>
            <el-col :span="8" :xs="24">
              <el-text tag="b" size="small" class="mb-1 block">文件名前缀</el-text>
              <el-input v-model="renamePrefix" placeholder="例如 weld-" />
            </el-col>
            <el-col :span="8" :xs="24">
              <el-text tag="b" size="small" class="mb-1 block">起始序号</el-text>
              <el-input-number v-model="renameStart" :min="0" class="w-full" />
            </el-col>
          </el-row>
          <div class="action-row">
            <el-button type="primary" @click="renderRenamePreview">
              <el-icon class="mr-1"><View /></el-icon>生成预览
            </el-button>
            <el-button type="success" :disabled="!renameEntries.length" @click="exportRenamedFiles">
              <el-icon class="mr-1"><Download /></el-icon>导出 ZIP
            </el-button>
          </div>
          <div v-if="renameEntries.length" class="result-box">
            <div class="result-summary">
              <div><strong>{{ renameEntries.length }}</strong><span>个文件</span></div>
              <div><strong class="ok">可导出</strong><span>原文件不会被改动</span></div>
            </div>
            <ol class="result-list">
              <li v-for="(entry, i) in renameEntries.slice(0, 80)" :key="i">
                {{ entry.file.name }} → <strong>{{ entry.name }}</strong>
              </li>
              <li v-if="renameEntries.length > 80">… 还有 {{ renameEntries.length - 80 }} 个文件</li>
            </ol>
          </div>
          <el-empty v-else description="选择文件后生成改名预览" :image-size="60" />
        </el-card>
      </el-tab-pane>

      <!-- 文件去重 -->
      <el-tab-pane label="文件去重" name="dedupe">
        <el-card shadow="never">
          <el-text tag="b" size="small" class="mb-1 block">选择文件或文件夹内文件</el-text>
          <input ref="dedupeFilesRef" type="file" multiple webkitdirectory class="file-input" />
          <div class="action-row">
            <el-button type="primary" @click="runDedupe">
              <el-icon class="mr-1"><Search /></el-icon>扫描重复文件
            </el-button>
            <el-button type="success" :disabled="!dedupeReport" @click="downloadText('dedupe-report.txt', dedupeReport)">
              <el-icon class="mr-1"><Download /></el-icon>下载报告
            </el-button>
          </div>
          <div v-if="dedupeResult" class="result-box">
            <div class="result-summary">
              <div><strong>{{ dedupeResult.total }}</strong><span>已扫描</span></div>
              <div><strong :class="dedupeResult.duplicates ? 'warn' : 'ok'">{{ dedupeResult.duplicates }}</strong><span>可移除副本</span></div>
              <div><strong>{{ dedupeResult.groups }}</strong><span>重复组</span></div>
            </div>
            <template v-if="dedupeResult.groupList.length">
              <ol class="result-list">
                <li v-for="(items, i) in dedupeResult.groupList.slice(0, 30)" :key="i">
                  {{ items.join(" ↔ ") }}
                </li>
              </ol>
            </template>
            <p v-else class="ok">没有发现内容相同的文件。</p>
          </div>
          <el-empty v-else description="浏览器会按文件内容计算 SHA-256，不修改源文件" :image-size="60" />
        </el-card>
      </el-tab-pane>

      <!-- 文件筛选 -->
      <el-tab-pane label="文件筛选" name="filter">
        <el-card shadow="never">
          <el-row :gutter="16">
            <el-col :span="12" :xs="24">
              <el-text tag="b" size="small" class="mb-1 block">选择文件</el-text>
              <input ref="filterFilesRef" type="file" multiple webkitdirectory class="file-input" />
            </el-col>
            <el-col :span="12" :xs="24">
              <el-text tag="b" size="small" class="mb-1 block">文件名关键词或正则表达式</el-text>
              <el-input v-model="filterPattern" placeholder="例如 pdf$ 或 video|wx" />
            </el-col>
          </el-row>
          <div class="action-row">
            <el-button type="primary" @click="runFilter">
              <el-icon class="mr-1"><Filter /></el-icon>筛选文件
            </el-button>
            <el-button type="success" :disabled="!filterReport" @click="downloadText('file-filter-report.txt', filterReport)">
              <el-icon class="mr-1"><Download /></el-icon>导出清单
            </el-button>
          </div>
          <div v-if="filterResult" class="result-box">
            <div class="result-summary">
              <div><strong>{{ filterResult.matched.length }}</strong><span>匹配文件</span></div>
              <div><strong>{{ filterResult.total }}</strong><span>总文件</span></div>
            </div>
            <ol v-if="filterResult.matched.length" class="result-list">
              <li v-for="(name, i) in filterResult.matched.slice(0, 100)" :key="i">{{ name }}</li>
            </ol>
            <p v-else class="muted">没有匹配项。</p>
          </div>
          <el-empty v-else description="从 history 的文件筛选脚本迁移而来，网页端只做清单筛选" :image-size="60" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import JSZip from "jszip";

defineOptions({
  name: "Files",
  inheritAttrs: false,
});

const activeTab = ref("rename");

// ============ 批量改名 ============
const renameFilesRef = ref<HTMLInputElement>();
const renamePrefix = ref("file-");
const renameStart = ref(1);
const renameEntries = ref<{ file: File; name: string }[]>([]);

function renderRenamePreview() {
  const files = Array.from(renameFilesRef.value?.files ?? []);
  const prefix = renamePrefix.value || "file-";
  const start = renameStart.value || 0;
  renameEntries.value = files.map((file, index) => {
    const extension = file.name.includes(".") ? `.${file.name.split(".").pop()}` : "";
    return { file, name: `${prefix}${start + index}${extension}` };
  });
  if (!renameEntries.value.length) {
    ElMessage.warning("请先选择至少一个文件");
  }
}

async function exportRenamedFiles() {
  if (!renameEntries.value.length) return;
  const zip = new JSZip();
  for (const entry of renameEntries.value) {
    zip.file(entry.name, await entry.file.arrayBuffer());
  }
  const blob = await zip.generateAsync({ type: "blob" });
  downloadBlob(`renamed-${new Date().toISOString().slice(0, 10)}.zip`, blob);
}

// ============ 文件去重 ============
const dedupeFilesRef = ref<HTMLInputElement>();
const dedupeResult = ref<{
  total: number;
  duplicates: number;
  groups: number;
  groupList: string[][];
} | null>(null);
const dedupeReport = ref("");

async function digest(file: File) {
  const buffer = await file.arrayBuffer();
  const bytes = await crypto.subtle.digest("SHA-256", buffer);
  return [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

const fileLabel = (file: File) =>
  (file as any).webkitRelativePath || file.name;

async function runDedupe() {
  const files = Array.from(dedupeFilesRef.value?.files ?? []);
  if (!files.length) {
    ElMessage.warning("请先选择文件或文件夹");
    return;
  }
  const loading = ElLoading.service({ text: "正在计算文件指纹…", background: "rgba(0,0,0,.3)" });
  try {
    const hashes = new Map<string, string[]>();
    for (const file of files) {
      const hash = await digest(file);
      if (!hashes.has(hash)) hashes.set(hash, []);
      hashes.get(hash)!.push(fileLabel(file));
    }
    const groups = [...hashes.values()].filter((items) => items.length > 1);
    const duplicateCount = groups.reduce((sum, items) => sum + items.length - 1, 0);

    dedupeResult.value = {
      total: files.length,
      duplicates: duplicateCount,
      groups: groups.length,
      groupList: groups,
    };
    dedupeReport.value = [
      "LeonTools 文件去重报告",
      `生成时间: ${new Date().toLocaleString()}`,
      `扫描文件: ${files.length}`,
      `重复组: ${groups.length}`,
      "",
      ...groups.map((items, index) => `重复组 ${index + 1}\n${items.map((item) => `- ${item}`).join("\n")}`),
    ].join("\n");
  } finally {
    loading.close();
  }
}

// ============ 文件筛选 ============
const filterFilesRef = ref<HTMLInputElement>();
const filterPattern = ref("video|wx|VID");
const filterResult = ref<{ matched: string[]; total: number } | null>(null);
const filterReport = ref("");

function runFilter() {
  const files = Array.from(filterFilesRef.value?.files ?? []);
  const source = filterPattern.value.trim();
  if (!files.length) {
    ElMessage.warning("请先选择文件");
    return;
  }
  let pattern: RegExp;
  try {
    pattern = new RegExp(source, "i");
  } catch (error: any) {
    ElMessage.error(`正则表达式无效：${error.message}`);
    return;
  }
  const matched = files.map(fileLabel).filter((name) => pattern.test(name));
  filterResult.value = { matched, total: files.length };
  filterReport.value = ["LeonTools 文件筛选清单", `筛选条件: ${source}`, "", ...matched].join("\n");
}

// ============ 通用工具 ============
function downloadBlob(name: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function downloadText(name: string, text: string) {
  if (!text) return;
  downloadBlob(name, new Blob([text], { type: "text/plain;charset=utf-8" }));
}
</script>

<style lang="scss" scoped>
.files-container {
  padding: 16px;
}

.file-input {
  display: block;
  width: 100%;
  padding: 8px;
  font-size: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.action-row {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.result-box {
  min-height: 120px;
  margin-top: 16px;
  padding: 15px;
  overflow: auto;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.result-summary {
  display: flex;
  gap: 25px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  strong {
    display: block;
    font-size: 20px;
    color: var(--el-text-color-primary);
  }

  span {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}

.result-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.ok {
  color: var(--el-color-success);
}

.warn {
  color: var(--el-color-warning);
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
