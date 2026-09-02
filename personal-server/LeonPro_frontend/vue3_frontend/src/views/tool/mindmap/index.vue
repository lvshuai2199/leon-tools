<template>
  <div class="mindmap-container">
    <el-card shadow="never">
      <template #header>
        <div class="flex-x-between">
          <span class="font-bold">Markdown 思维导图工具</span>
          <el-tag type="info" size="small">marked + 自绘 SVG · 本地渲染</el-tag>
        </div>
      </template>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-button size="small" @click="loadExample">📝 加载示例</el-button>
        <el-button size="small" type="primary" :icon="Download" @click="exportPNG">下载 PNG</el-button>
        <el-button size="small" type="success" :icon="Download" @click="exportSVG">下载 SVG</el-button>
        <el-button size="small" :icon="Download" @click="exportMarkdown">下载 Markdown</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" type="warning" :loading="saveLoading" @click="openSaveDialog(false)">
          {{ currentId ? "保存修改" : "存储并生成链接" }}
        </el-button>
        <el-button v-if="currentId" size="small" @click="openSaveDialog(true)">另存为</el-button>
        <el-button size="small" @click="openStoredList">已存储列表</el-button>
        <span class="toolbar-tip">
          <template v-if="currentId">正在编辑：{{ currentTitle }}</template>
          <template v-else>存储后可查看本地图片链接，供外部访问</template>
        </span>
      </div>

      <div class="main">
        <!-- 左：Markdown 编辑器 -->
        <div class="editor-pane">
          <div class="pane-title">Markdown 源码</div>
          <Codemirror
            v-model:value="markdown"
            :options="cmOptions"
            height="100%"
            width="100%"
            border
          />
        </div>

        <!-- 右：思维导图预览 -->
        <div class="preview-pane">
          <div class="pane-title">
            思维导图预览
            <span class="pane-tip">鼠标拖拽平移 · 滚轮缩放</span>
          </div>
          <div
            ref="mapWrapRef"
            class="map-wrap"
            @mousedown="onMouseDown"
            @mousemove="onMouseMove"
            @mouseup="onMouseUp"
            @mouseleave="onMouseUp"
          >
            <svg ref="svgRef" class="map-svg"></svg>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="saveDialog.visible" :title="saveDialog.saveAs ? '另存为' : currentId ? '保存修改' : '存储思维导图'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="saveDialog.title" maxlength="80" show-word-limit placeholder="用于列表展示" />
        </el-form-item>
        <p class="save-hint">将生成 PNG 存到服务器，并可查看本地图片链接。</p>
      </el-form>
      <template #footer>
        <el-button @click="saveDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="confirmSave">确定存储</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="listDrawer.visible" title="已存储思维导图" size="640px" destroy-on-close>
      <div class="list-toolbar">
        <el-input
          v-model="listQuery.title"
          placeholder="按标题搜索"
          clearable
          style="width: 220px"
          @keyup.enter="loadStoredList"
        />
        <el-button type="primary" @click="loadStoredList">查询</el-button>
      </div>
      <el-table v-loading="listDrawer.loading" :data="listDrawer.records" border>
        <el-table-column label="预览" width="88" align="center">
          <template #default="{ row }">
            <el-image
              class="thumb"
              :src="mindmapPreviewUrl(row)"
              :preview-src-list="[mindmapPublicUrl(row)]"
              fit="contain"
              preview-teleported
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="loadStored(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="viewStoredLink(row)">查看链接</el-button>
            <el-button type="danger" link size="small" @click="deleteStored(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-if="listDrawer.total > 0"
        v-model:page="listQuery.current"
        v-model:limit="listQuery.size"
        :total="listDrawer.total"
        @pagination="loadStoredList"
      />
    </el-drawer>

    <el-dialog v-model="linkDialog.visible" title="图片链接" width="560px">
      <el-input v-model="linkDialog.url" readonly>
        <template #append>
          <el-button @click="openLink">打开</el-button>
        </template>
      </el-input>
      <p class="save-hint" style="margin: 10px 0 0">选中上方地址后自行复制即可。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { marked, type Tokens } from "marked";
import Codemirror from "codemirror-editor-vue3";
import "codemirror/mode/markdown/markdown.js";
import type { EditorConfiguration } from "codemirror";
import { Download } from "@element-plus/icons-vue";
import MindmapAPI, { mindmapPreviewUrl, mindmapPublicUrl, type MindmapVO } from "@/api/tool/mindmap";

defineOptions({ name: "Mindmap" });

const exampleMarkdown = `# LeonTools 工具平台

## 工具中心

- 轨迹分析
  - EliScript / CSV 轨迹解析
  - movej/movel/movep/movec 支持
  - 3D 姿态与平面投影
- 文件工具
  - 文件转换与处理
- 文档工具
  - 在线文档编辑
- 思维导图
  - Markdown 一键生成
  - 支持 PNG / SVG 下载

## 业务管理

- 任务管理
  - 焊接任务编排
  - 工艺参数配置
- 注册申请
  - 客户设备注册

## 系统管理

- 用户管理
  - 账号与角色绑定
- 角色管理
  - 可访问路由授权
- 路由配置
  - 菜单树维护
  - 动态路由驱动

## 技术栈

- 前端
  - Vue 3 + TypeScript
  - Element Plus
  - Vite
- 后端
  - Spring Boot + MyBatis-Plus
  - MySQL
`;

const markdown = ref(exampleMarkdown);
const svgRef = ref<SVGSVGElement>();
const mapWrapRef = ref<HTMLElement>();
const currentId = ref("");
const currentTitle = ref("");
const saveLoading = ref(false);

const saveDialog = reactive({
  visible: false,
  saveAs: false,
  title: "",
});

const listQuery = reactive({
  current: 1,
  size: 10,
  title: "",
});

const listDrawer = reactive({
  visible: false,
  loading: false,
  records: [] as MindmapVO[],
  total: 0,
});

const cmOptions: EditorConfiguration = {
  mode: "markdown",
  lineNumbers: true,
  lineWrapping: true,
};

interface TreeNode {
  /** 显示文本 */
  text: string;
  /** 子节点 */
  children: TreeNode[];
  /** 布局后赋值 */
  x: number;
  y: number;
  width: number;
  height: number;
}

const LEVEL_GAP = 56; // 父子节点水平间距（父右缘 → 子左缘）
const SIBLING_GAP = 14; // 兄弟节点垂直间距
const NODE_PAD_X = 14;
const NODE_PAD_Y = 8;
const NODE_MIN_WIDTH = 48;
const FONT_SIZE = 14;
const FONT_LINE = 20;
const FONT_FAMILY =
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif";

// 主题色（按层级）
const PALETTE = [
  "#4285f4",
  "#ea4335",
  "#fbbc05",
  "#34a853",
  "#ff6d01",
  "#46bdc6",
  "#7d3c98",
  "#c0392b",
];

/** 简单纯文本提取：去掉 inline markdown 标记（**、*、`、[]() 等） */
function plainText(s: string): string {
  return s
    .replace(/`([^`]+)`/g, "$1")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/\*([^*]+)\*/g, "$1")
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .trim();
}

let measureCtx: CanvasRenderingContext2D | null = null;

/** 按真实字体测量节点宽高（中文等宽字符不会估窄导致连线错位） */
function measure(text: string) {
  if (!measureCtx) {
    measureCtx = document.createElement("canvas").getContext("2d");
  }
  const ctx = measureCtx;
  const lines = text.split(/\n/);
  let maxW = 0;
  if (ctx) {
    ctx.font = `${FONT_SIZE}px ${FONT_FAMILY}`;
    for (const line of lines) {
      maxW = Math.max(maxW, ctx.measureText(line || " ").width);
    }
  } else {
    maxW = Math.max(...lines.map((l) => [...l].length)) * FONT_SIZE;
  }
  return {
    width: Math.max(NODE_MIN_WIDTH, Math.ceil(maxW) + NODE_PAD_X * 2),
    height: Math.max(lines.length, 1) * FONT_LINE + NODE_PAD_Y * 2,
  };
}

/** 递归将 token 列表转换为 TreeNode（追加到 parent.children） */
function buildFromTokens(tokens: Tokens.Generic[], parent: TreeNode) {
  let i = 0;
  while (i < tokens.length) {
    const tok = tokens[i];
    if (tok.type === "heading") {
      const h = tok as Tokens.Heading;
      const node: TreeNode = { text: plainText(h.text), children: [], x: 0, y: 0, width: 0, height: 0 };
      parent.children.push(node);
      const childrenTokens = collectSubTokens(tokens, i);
      if (childrenTokens.length > 0) {
        buildFromTokens(childrenTokens, node);
      }
      i += 1 + childrenTokens.length;
    } else if (tok.type === "list") {
      const list = tok as Tokens.List;
      for (const item of list.items) {
        const node = collectListItem(item);
        if (node) parent.children.push(node);
      }
      i++;
    } else {
      i++;
    }
  }
}

/** 收集从 heading i 开始、直到下一个同级/更高级 heading 之前的 tokens */
function collectSubTokens(tokens: Tokens.Generic[], startIdx: number): Tokens.Generic[] {
  const start = tokens[startIdx] as Tokens.Heading;
  const sub: Tokens.Generic[] = [];
  for (let i = startIdx + 1; i < tokens.length; i++) {
    const t = tokens[i];
    if (t.type === "heading" && (t as Tokens.Heading).depth <= start.depth) break;
    sub.push(t);
  }
  return sub;
}

/** 从 list_item 构造 TreeNode（递归处理嵌套 list） */
function collectListItem(item: Tokens.ListItem): TreeNode | null {
  // item.text 是渲染文本（含 list 缩进标记），item.tokens 是结构化 tokens
  // item.tokens 通常包含 text(tokens)+list（嵌套）
  // 用 item.text 第一行作为节点文本
  const firstLine = plainText(item.text.split("\n")[0]);
  const node: TreeNode = { text: firstLine, children: [], x: 0, y: 0, width: 0, height: 0 };
  // 递归处理 item.tokens 中的嵌套 list
  for (const sub of item.tokens) {
    if (sub.type === "list") {
      const list = sub as Tokens.List;
      for (const child of list.items) {
        const cn = collectListItem(child);
        if (cn) node.children.push(cn);
      }
    }
  }
  return node;
}

/** 解析 markdown 为 TreeNode（根节点） */
function parseMarkdown(md: string): TreeNode {
  const tokens = marked.lexer(md || "");
  // 找第一个 heading 作为根；若没有则使用首行
  const firstHeading = tokens.find((t) => t.type === "heading") as Tokens.Heading | undefined;
  const root: TreeNode = {
    text: firstHeading ? plainText(firstHeading.text) : "思维导图",
    children: [],
    x: 0,
    y: 0,
    width: 0,
    height: 0,
  };
  if (firstHeading) {
    const sub = collectSubTokens(tokens, tokens.indexOf(firstHeading));
    buildFromTokens(sub, root);
  } else {
    buildFromTokens(tokens, root);
  }
  return root;
}

/** 递归布局：子节点紧贴在父节点右侧（按真实宽度 + 水平间距），避免连线回穿。
 *  父节点垂直居中于首尾子节点；返回本子树占用的总高度。
 */
function layoutTree(node: TreeNode, xStart: number, yStart: number): number {
  const m = measure(node.text);
  node.width = m.width;
  node.height = m.height;
  node.x = xStart + node.width / 2;

  if (!node.children || node.children.length === 0) {
    node.y = yStart + node.height / 2;
    return node.height;
  }

  const childXStart = xStart + node.width + LEVEL_GAP;
  let cursor = yStart;
  node.children.forEach((child, i) => {
    if (i > 0) cursor += SIBLING_GAP;
    cursor += layoutTree(child, childXStart, cursor);
  });
  const first = node.children[0];
  const last = node.children[node.children.length - 1];
  node.y = (first.y + last.y) / 2;
  const occupied = cursor - yStart;
  const top = node.y - node.height / 2;
  const bottom = node.y + node.height / 2;
  const extraTop = Math.max(0, yStart - top);
  if (extraTop > 0) {
    shiftTree(node, extraTop);
  }
  return Math.max(occupied + extraTop, bottom + extraTop - yStart);
}

function shiftTree(node: TreeNode, dy: number) {
  node.y += dy;
  node.children.forEach((c) => shiftTree(c, dy));
}

/** 计算整棵树的边界（用于设置 viewBox） */
function computeBounds(root: TreeNode) {
  let minX = root.x - root.width / 2;
  let maxX = root.x + root.width / 2;
  let minY = root.y - root.height / 2;
  let maxY = root.y + root.height / 2;
  const walk = (n: TreeNode) => {
    minX = Math.min(minX, n.x - n.width / 2);
    maxX = Math.max(maxX, n.x + n.width / 2);
    minY = Math.min(minY, n.y - n.height / 2);
    maxY = Math.max(maxY, n.y + n.height / 2);
    n.children.forEach(walk);
  };
  walk(root);
  return { minX, maxX, minY, maxY, width: maxX - minX, height: maxY - minY };
}

/** SVG 命名空间 */
const NS = "http://www.w3.org/2000/svg";

function el(name: string, attrs: Record<string, string | number> = {}): SVGElement {
  const e = document.createElementNS(NS, name);
  for (const k in attrs) e.setAttribute(k, String(attrs[k]));
  return e;
}

/** 渲染整棵树到 svg */
function renderTree(root: TreeNode) {
  const svg = svgRef.value!;
  svg.innerHTML = "";
  if (!root.text && (!root.children || root.children.length === 0)) {
    const t = el("text", { x: 20, y: 40, fill: "#999", "font-size": 14 });
    t.textContent = "（无内容）";
    svg.appendChild(t);
    return;
  }

  // 布局
  layoutTree(root, 0, 0);
  const bounds = computeBounds(root);
  const padding = 40;
  const vbX = bounds.minX - padding;
  const vbY = bounds.minY - padding;
  const vbW = bounds.width + padding * 2;
  const vbH = bounds.height + padding * 2;
  svg.setAttribute("viewBox", `${vbX} ${vbY} ${vbW} ${vbH}`);
  svg.setAttribute("preserveAspectRatio", "xMidYMid meet");

  const gRoot = el("g", { id: "mm-root" });
  svg.appendChild(gRoot);

  // 1) 连线
  const drawLinks = (node: TreeNode) => {
    for (const child of node.children) {
      const x1 = node.x + node.width / 2;
      const y1 = node.y;
      const x2 = child.x - child.width / 2;
      const y2 = child.y;
      const midX = (x1 + x2) / 2;
      const path = el("path", {
        d: `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`,
        fill: "none",
        stroke: "#9aa0a6",
        "stroke-width": 1.5,
      });
      gRoot.appendChild(path);
      drawLinks(child);
    }
  };
  drawLinks(root);

  // 2) 节点
  const drawNode = (node: TreeNode, depth: number) => {
    const color = PALETTE[depth % PALETTE.length];
    const isRoot = depth === 0;
    const rx = isRoot ? node.height / 2 : 6;
    const rect = el("rect", {
      x: node.x - node.width / 2,
      y: node.y - node.height / 2,
      width: node.width,
      height: node.height,
      rx,
      ry: rx,
      fill: isRoot ? color : "#ffffff",
      stroke: color,
      "stroke-width": isRoot ? 0 : 1.5,
    });
    gRoot.appendChild(rect);
    const text = el("text", {
      x: node.x,
      y: node.y,
      "font-size": FONT_SIZE,
      "font-family": FONT_FAMILY,
      "text-anchor": "middle",
      "dominant-baseline": "middle",
      fill: isRoot ? "#ffffff" : "#222",
    });
    // 多行支持
    const lines = node.text.split("\n");
    if (lines.length === 1) {
      text.textContent = lines[0];
    } else {
      lines.forEach((line, i) => {
        const tspan = document.createElementNS(NS, "tspan");
        tspan.setAttribute("x", String(node.x));
        tspan.setAttribute("dy", String(i === 0 ? -(lines.length - 1) * (FONT_LINE / 2) : FONT_LINE));
        tspan.textContent = line;
        text.appendChild(tspan);
      });
    }
    gRoot.appendChild(text);
    node.children.forEach((c) => drawNode(c, depth + 1));
  };
  drawNode(root, 0);
}

/** 重新渲染（带防抖） */
let renderTimer: ReturnType<typeof setTimeout> | undefined;
function scheduleRender() {
  clearTimeout(renderTimer);
  renderTimer = setTimeout(render, 250);
}
function render() {
  if (!svgRef.value) return;
  try {
    const root = parseMarkdown(markdown.value);
    renderTree(root);
    applyTransform();
  } catch (e) {
    console.error("mindmap render error", e);
  }
}

watch(markdown, scheduleRender);

/** 下载辅助 */
function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function getExportSize() {
  const svg = svgRef.value!;
  const vb = (svg.getAttribute("viewBox") || "0 0 800 600").split(/\s+/).map(Number);
  const w = Math.max(1, vb[2] || 800);
  const h = Math.max(1, vb[3] || 600);
  return { vb, w, h };
}

function getSvgXml(): string {
  const svg = svgRef.value!;
  const { vb, w, h } = getExportSize();
  const clone = svg.cloneNode(true) as SVGSVGElement;
  // 导出完整画布，忽略预览里的平移/缩放，避免内容被裁切
  const g = clone.querySelector("#mm-root");
  g?.removeAttribute("transform");
  clone.removeAttribute("style");
  clone.setAttribute("width", String(Math.round(w)));
  clone.setAttribute("height", String(Math.round(h)));
  clone.setAttribute("viewBox", `${vb[0]} ${vb[1]} ${w} ${h}`);
  clone.setAttribute("preserveAspectRatio", "xMidYMid meet");
  clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
  const bg = el("rect", { x: vb[0], y: vb[1], width: w, height: h, fill: "#ffffff" });
  clone.insertBefore(bg, clone.firstChild);
  return new XMLSerializer().serializeToString(clone);
}

function exportSVG() {
  const xml = getSvgXml();
  downloadBlob(new Blob([xml], { type: "image/svg+xml;charset=utf-8" }), "mindmap.svg");
  ElMessage.success("SVG 已导出");
}

async function renderPngBlob(): Promise<Blob> {
  const xml = getSvgXml();
  const url = URL.createObjectURL(new Blob([xml], { type: "image/svg+xml;charset=utf-8" }));
  try {
    const img = new Image();
    await new Promise<void>((resolve, reject) => {
      img.onload = () => resolve();
      img.onerror = () => reject(new Error("SVG 转图片失败"));
      img.src = url;
    });
    const { w, h } = getExportSize();
    const scale = 2;
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(w * scale));
    canvas.height = Math.max(1, Math.round(h * scale));
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("无法创建画布");
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
    if (!blob) throw new Error("生成 PNG 失败");
    return blob;
  } finally {
    URL.revokeObjectURL(url);
  }
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = () => reject(new Error("读取图片失败"));
    reader.readAsDataURL(blob);
  });
}

function defaultTitleFromMarkdown() {
  const line = (markdown.value || "").split("\n").map((s) => s.trim().replace(/^#+\s*/, "")).find(Boolean);
  return line ? line.slice(0, 80) : "未命名思维导图";
}

function openSaveDialog(saveAs: boolean) {
  saveDialog.saveAs = saveAs;
  saveDialog.title = currentTitle.value || defaultTitleFromMarkdown();
  saveDialog.visible = true;
}

async function confirmSave() {
  if (!markdown.value?.trim()) {
    ElMessage.warning("当前没有可存储的内容");
    return;
  }
  saveLoading.value = true;
  try {
    clearTimeout(renderTimer);
    render();
    const blob = await renderPngBlob();
    const imageBase64 = await blobToDataUrl(blob);
    const saved = await MindmapAPI.save({
      id: saveDialog.saveAs ? undefined : currentId.value || undefined,
      title: saveDialog.title.trim() || defaultTitleFromMarkdown(),
      markdown: markdown.value,
      imageBase64,
    });
    currentId.value = saved.id || "";
    currentTitle.value = saved.title || saveDialog.title;
    saveDialog.visible = false;
    ElMessage.success("已存储");
    showLink(mindmapPublicUrl(saved));
  } catch (e) {
    console.error(e);
  } finally {
    saveLoading.value = false;
  }
}

function openStoredList() {
  listDrawer.visible = true;
  loadStoredList();
}

function loadStoredList() {
  listDrawer.loading = true;
  MindmapAPI.getPage(listQuery)
    .then((data) => {
      listDrawer.records = data.records || [];
      listDrawer.total = data.total || 0;
    })
    .catch((error) => {
      console.error(error);
    })
    .finally(() => {
      listDrawer.loading = false;
    });
}

async function loadStored(row: MindmapVO) {
  if (!row.id) return;
  try {
    const detail = await MindmapAPI.getById(row.id);
    markdown.value = detail.markdown || "";
    currentId.value = detail.id || "";
    currentTitle.value = detail.title || "";
    listDrawer.visible = false;
    ElMessage.success("已载入，可继续修改后保存");
  } catch (e) {
    console.error(e);
  }
}

const linkDialog = reactive({
  visible: false,
  url: "",
});

function showLink(url: string) {
  linkDialog.url = url;
  linkDialog.visible = true;
}

function viewStoredLink(row: MindmapVO) {
  showLink(mindmapPublicUrl(row));
}

function openLink() {
  if (linkDialog.url) window.open(linkDialog.url, "_blank");
}

function deleteStored(row: MindmapVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除「${row.title || "未命名"}」吗？删除后外链将失效。`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      MindmapAPI.deleteByIds([row.id!]).then(() => {
        ElMessage.success("已删除");
        if (currentId.value === row.id) {
          currentId.value = "";
          currentTitle.value = "";
        }
        loadStoredList();
      });
    })
    .catch(() => {});
}

async function exportPNG() {
  try {
    const blob = await renderPngBlob();
    downloadBlob(blob, "mindmap.png");
    ElMessage.success("PNG 已导出");
  } catch (e) {
    console.error(e);
    ElMessage.error("PNG 导出失败");
  }
}

function exportMarkdown() {
  if (!markdown.value) {
    ElMessage.warning("当前没有可导出的内容");
    return;
  }
  downloadBlob(
    new Blob([markdown.value], { type: "text/markdown;charset=utf-8" }),
    "mindmap.md",
  );
  ElMessage.success("Markdown 已导出");
}

function loadExample() {
  markdown.value = exampleMarkdown;
  currentId.value = "";
  currentTitle.value = "";
  ElMessage.success("已加载示例");
}

/** 预览区内滚轮缩放 + 拖拽平移（仅变换内部图形，不缩放整页） */
const viewState = reactive({ scale: 1, tx: 0, ty: 0 });
let dragStart: { x: number; y: number; tx: number; ty: number } | null = null;
let wrapRo: ResizeObserver | undefined;

function applyTransform() {
  const g = svgRef.value?.querySelector("#mm-root") as SVGElement | null;
  if (g) {
    g.setAttribute("transform", `translate(${viewState.tx},${viewState.ty}) scale(${viewState.scale})`);
  }
}

function onWheel(e: WheelEvent) {
  e.preventDefault();
  e.stopPropagation();
  const delta = e.deltaY < 0 ? 1.08 : 1 / 1.08;
  viewState.scale = Math.min(4, Math.max(0.25, viewState.scale * delta));
  applyTransform();
}

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return;
  dragStart = { x: e.clientX, y: e.clientY, tx: viewState.tx, ty: viewState.ty };
}
function onMouseMove(e: MouseEvent) {
  if (!dragStart) return;
  viewState.tx = dragStart.tx + (e.clientX - dragStart.x);
  viewState.ty = dragStart.ty + (e.clientY - dragStart.y);
  applyTransform();
}
function onMouseUp() {
  dragStart = null;
}

onMounted(() => {
  render();
  const wrap = mapWrapRef.value;
  if (wrap) {
    wrap.addEventListener("wheel", onWheel, { passive: false });
    wrapRo = new ResizeObserver(() => applyTransform());
    wrapRo.observe(wrap);
  }
});

onBeforeUnmount(() => {
  mapWrapRef.value?.removeEventListener("wheel", onWheel);
  wrapRo?.disconnect();
  if (svgRef.value) svgRef.value.innerHTML = "";
});
</script>

<style lang="scss" scoped>
.mindmap-container {
  padding: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;

  .toolbar-tip {
    margin-left: auto;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.save-hint {
  margin: 0 0 0 80px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.list-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.thumb {
  width: 56px;
  height: 40px;
  border-radius: 4px;
  background: #fff;
}

.main {
  display: flex;
  gap: 16px;
  height: calc(100vh - 260px);
  min-height: 460px;
}

.editor-pane,
.preview-pane {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.editor-pane {
  flex: 0 0 42%;
}

.preview-pane {
  flex: 1;
}

.pane-title {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);

  .pane-tip {
    margin-left: 8px;
    font-weight: 400;
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}

.map-wrap {
  flex: 1;
  overflow: hidden;
  background: #fff;
  cursor: grab;
  touch-action: none;
  overscroll-behavior: contain;
}

.map-wrap:active {
  cursor: grabbing;
}

.map-svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: hidden;
  user-select: none;
}

html.dark {
  .map-wrap {
    background: var(--el-bg-color);
  }
}
</style>
