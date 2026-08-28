<template>
  <div class="documents-container">
    <el-tabs v-model="activeTab">
      <!-- PDF 合并 -->
      <el-tab-pane label="PDF 合并" name="pdf">
        <el-card shadow="never">
          <el-text tag="b" size="small" class="mb-1 block">选择 PDF（顺序即合并顺序）</el-text>
          <input ref="pdfFilesRef" type="file" accept="application/pdf,.pdf" multiple class="file-input" />
          <div class="action-row">
            <el-button type="primary" @click="mergePdfs">
              <el-icon class="mr-1"><Files /></el-icon>合并 PDF
            </el-button>
          </div>
          <div v-if="pdfResult" class="result-box">
            <div class="result-summary">
              <div><strong>{{ pdfResult.files }}</strong><span>个 PDF</span></div>
              <div><strong class="ok">{{ pdfResult.pages }}</strong><span>页已合并并下载</span></div>
            </div>
            <p class="ok">处理完成。文件顺序与选择顺序一致。</p>
          </div>
          <el-empty v-else description="基于 history 中的 PyPDF2 合并脚本，浏览器端使用 pdf-lib" :image-size="60" />
        </el-card>
      </el-tab-pane>

      <!-- Markdown 转 HTML -->
      <el-tab-pane label="Markdown 转 HTML" name="markdown">
        <el-card shadow="never">
          <el-row :gutter="16">
            <el-col :span="12" :xs="24">
              <el-text tag="b" size="small" class="mb-1 block">Markdown 内容</el-text>
              <el-input
                v-model="markdownInput"
                type="textarea"
                :rows="16"
                spellcheck="false"
                placeholder="# 标题"
                class="mono-textarea"
              />
            </el-col>
            <el-col :span="12" :xs="24">
              <el-text tag="b" size="small" class="mb-1 block">预览</el-text>
              <div class="markdown-preview markdown-body" v-html="markdownHtml" />
            </el-col>
          </el-row>
          <div class="action-row">
            <el-button @click="triggerLoadMarkdown">
              <el-icon class="mr-1"><Upload /></el-icon>载入 .md
            </el-button>
            <input ref="markdownFileRef" type="file" accept=".md,.markdown,text/markdown" class="hidden-input" />
            <el-button type="primary" @click="exportMarkdown">
              <el-icon class="mr-1"><Download /></el-icon>导出 HTML
            </el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 图片引用检查 -->
      <el-tab-pane label="图片引用检查" name="image-check">
        <el-card shadow="never">
          <el-text tag="b" size="small" class="mb-1 block">选择 Markdown 与图片文件（可选择文件夹）</el-text>
          <input ref="imageCheckFilesRef" type="file" multiple webkitdirectory class="file-input" />
          <div class="action-row">
            <el-button type="primary" @click="runImageCheck">
              <el-icon class="mr-1"><Search /></el-icon>检查引用
            </el-button>
            <el-button type="success" :disabled="!imageReport" @click="downloadText('image-reference-report.txt', imageReport)">
              <el-icon class="mr-1"><Download /></el-icon>下载报告
            </el-button>
          </div>
          <div v-if="imageResult" class="result-box">
            <div class="result-summary">
              <div><strong>{{ imageResult.markdownCount }}</strong><span>Markdown 文件</span></div>
              <div><strong :class="imageResult.missing.length ? 'warn' : 'ok'">{{ imageResult.missing.length }}</strong><span>失效引用</span></div>
              <div><strong :class="imageResult.unused.length ? 'warn' : 'ok'">{{ imageResult.unused.length }}</strong><span>未引用图片</span></div>
            </div>
            <template v-if="imageResult.missing.length">
              <p class="danger">失效引用</p>
              <ul class="result-list">
                <li v-for="(item, i) in imageResult.missing.slice(0, 40)" :key="i">{{ item }}</li>
              </ul>
            </template>
            <p v-else class="ok">没有发现失效图片引用。</p>
            <template v-if="imageResult.unused.length">
              <p class="warn">未引用图片</p>
              <ul class="result-list">
                <li v-for="(item, i) in imageResult.unused.slice(0, 40)" :key="i">{{ item }}</li>
              </ul>
            </template>
          </div>
          <el-empty v-else description="从 history 的 Markdown 图片链接工具迁移而来，会检查文件名匹配并给出报告" :image-size="60" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { PDFDocument } from "pdf-lib";
import { marked } from "marked";

defineOptions({
  name: "Documents",
  inheritAttrs: false,
});

const activeTab = ref("pdf");

// ============ PDF 合并 ============
const pdfFilesRef = ref<HTMLInputElement>();
const pdfResult = ref<{ files: number; pages: number } | null>(null);

async function mergePdfs() {
  const files = Array.from(pdfFilesRef.value?.files ?? []);
  if (!files.length) {
    ElMessage.warning("请先选择 PDF 文件");
    return;
  }
  const loading = ElLoading.service({ text: "正在合并 PDF…", background: "rgba(0,0,0,.3)" });
  try {
    const merged = await PDFDocument.create();
    let pages = 0;
    for (const file of files) {
      const source = await PDFDocument.load(await file.arrayBuffer());
      const copied = await merged.copyPages(source, source.getPageIndices());
      copied.forEach((page) => merged.addPage(page));
      pages += copied.length;
    }
    const pdfBytes = await merged.save();
    downloadBlob(
      `merged-${new Date().toISOString().slice(0, 10)}.pdf`,
      new Blob([new Uint8Array(pdfBytes)], { type: "application/pdf" })
    );
    pdfResult.value = { files: files.length, pages };
  } catch (error: any) {
    ElMessage.error(`合并失败：${error.message}`);
  } finally {
    loading.close();
  }
}

// ============ Markdown ============
const markdownInput = ref(
  `# LeonTools 笔记

这是一个可以直接预览的 **Markdown** 文档。

- 选择上方工具
- 在本地处理文件
- 导出 HTML 结果`
);
const markdownFileRef = ref<HTMLInputElement>();

const markdownHtml = computed(() => marked.parse(markdownInput.value) as string);

function triggerLoadMarkdown() {
  markdownFileRef.value?.click();
}

async function loadMarkdownFile() {
  const file = markdownFileRef.value?.files?.[0];
  if (file) {
    markdownInput.value = await file.text();
  }
}

function exportMarkdown() {
  const content = marked.parse(markdownInput.value) as string;
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LeonTools Markdown Export</title><style>body{max-width:850px;margin:40px auto;padding:0 20px;font:16px/1.7 system-ui;color:#29373d}pre{background:#14242c;color:#fff;padding:16px;overflow:auto}code{font-family:Consolas,monospace}img{max-width:100%}</style></head><body>${content}</body></html>`;
  downloadBlob("markdown-export.html", new Blob([html], { type: "text/html;charset=utf-8" }));
}

// ============ 图片引用检查 ============
const imageCheckFilesRef = ref<HTMLInputElement>();
const imageResult = ref<{
  markdownCount: number;
  missing: string[];
  unused: string[];
} | null>(null);
const imageReport = ref("");

async function runImageCheck() {
  const files = Array.from(imageCheckFilesRef.value?.files ?? []);
  if (!files.length) {
    ElMessage.warning("请先选择包含 Markdown 和图片的文件夹");
    return;
  }
  const markdownFiles = files.filter((file) => /\.(md|markdown)$/i.test(file.name));
  const imageFiles = files.filter((file) => /\.(png|jpe?g|gif|bmp|svg|webp)$/i.test(file.name));

  const imageNames = new Set(imageFiles.map((file) => file.name.toLowerCase()));
  const referenced = new Set<string>();
  const missing: string[] = [];

  for (const file of markdownFiles) {
    const content = await file.text();
    const matches = [...content.matchAll(/!\[[^\]]*]\(([^)]+)\)/g)];
    for (const match of matches) {
      const raw = match[1].split("?")[0].split("#")[0].replaceAll("\\", "/");
      const name = (raw.split("/").pop() ?? "").toLowerCase();
      if (imageNames.has(name)) referenced.add(name);
      else missing.push(`${fileLabel(file)} -> ${raw}`);
    }
  }
  const unused = [...imageNames].filter((name) => !referenced.has(name));

  imageResult.value = { markdownCount: markdownFiles.length, missing, unused };
  imageReport.value = [
    "LeonTools 图片引用检查报告",
    `Markdown 文件: ${markdownFiles.length}`,
    `图片文件: ${imageFiles.length}`,
    "",
    "无法找到的引用:",
    ...missing.map((item) => `- ${item}`),
    "",
    "未被引用的图片:",
    ...unused.map((item) => `- ${item}`),
  ].join("\n");
}

// ============ 通用工具 ============
const fileLabel = (file: File) =>
  (file as any).webkitRelativePath || file.name;

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

onMounted(() => {
  markdownFileRef.value?.addEventListener("change", loadMarkdownFile);
});
</script>

<style lang="scss" scoped>
.documents-container {
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

.hidden-input {
  display: none;
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

.danger {
  color: var(--el-color-danger);
}

.mono-textarea {
  :deep(textarea) {
    font-family: "Cascadia Code", Consolas, monospace;
    font-size: 13px;
    line-height: 1.55;
  }
}

.markdown-preview {
  min-height: 370px;
  padding: 20px;
  overflow: auto;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: #38464d;

  :deep(h1),
  :deep(h2),
  :deep(h3) {
    margin: 0 0 12px;
  }

  :deep(h1) {
    font-size: 25px;
  }

  :deep(h2) {
    font-size: 20px;
  }

  :deep(h3) {
    font-size: 16px;
  }

  :deep(pre) {
    padding: 13px;
    overflow: auto;
    background: #16252d;
    color: #eaf1f3;
  }

  :deep(code) {
    font-family: Consolas, monospace;
  }

  :deep(img) {
    max-width: 100%;
  }

  :deep(blockquote) {
    margin-left: 0;
    padding-left: 14px;
    color: #68767d;
    border-left: 3px solid var(--el-color-primary);
  }
}
</style>
