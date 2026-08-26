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
        <span class="toolbar-tip">用 Markdown 标题与列表层级自动构建思维导图</span>
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
          <div ref="mapWrapRef" class="map-wrap">
            <svg
              ref="svgRef"
              class="map-svg"
              @wheel.prevent="onWheel"
              @mousedown="onMouseDown"
              @mousemove="onMouseMove"
              @mouseup="onMouseUp"
              @mouseleave="onMouseUp"
            ></svg>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { marked, type Tokens } from "marked";
import Codemirror from "codemirror-editor-vue3";
import "codemirror/mode/markdown/markdown.js";
import type { EditorConfiguration } from "codemirror";
import { Download } from "@element-plus/icons-vue";

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

const LEVEL_GAP = 70; // 相邻层水平间距
const NODE_PAD_X = 14;
const NODE_PAD_Y = 8;
const NODE_MIN_WIDTH = 48;
const FONT_SIZE = 14;
const FONT_LINE = 20;
const CHAR_WIDTH = 8;

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

/** 估算节点宽高（基于字符数） */
function measure(text: string) {
  const lines = text.split(/\n/).length;
  const maxLineLen = Math.max(...text.split(/\n/).map((l) => l.length));
  return {
    width: Math.max(NODE_MIN_WIDTH, maxLineLen * CHAR_WIDTH + NODE_PAD_X * 2),
    height: lines * FONT_LINE + NODE_PAD_Y * 2,
  };
}

/** 递归将 token 列表转换为 TreeNode（追加到 parent.children） */
function buildFromTokens(tokens: Tokens.Generic[], parent: TreeNode) {
  for (const tok of tokens) {
    if (tok.type === "heading") {
      const h = tok as Tokens.Heading;
      const node: TreeNode = { text: plainText(h.text), children: [], x: 0, y: 0, width: 0, height: 0 };
      parent.children.push(node);
      // 后续同级 token 中，直到下一个同级/更高级 heading 之前的内容都视为该节点子内容
      const childrenTokens = collectSubTokens(tokens, tokens.indexOf(h));
      if (childrenTokens.length > 0) {
        buildFromTokens(childrenTokens, node);
      }
    } else if (tok.type === "list") {
      const list = tok as Tokens.List;
      for (const item of list.items) {
        const node = collectListItem(item);
        if (node) parent.children.push(node);
      }
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
  const firstLine = item.text.split("\n")[0].trim();
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
    // 无标题：根 = "思维导图"，把其它 heading 作为一级子
    for (const t of tokens) {
      if (t.type === "heading") {
        const h = t as Tokens.Heading;
        const node: TreeNode = { text: plainText(h.text), children: [], x: 0, y: 0, width: 0, height: 0 };
        const sub = collectSubTokens(tokens, tokens.indexOf(h));
        buildFromTokens(sub, node);
        root.children.push(node);
      } else if (t.type === "list") {
        const list = t as Tokens.List;
        for (const item of list.items) {
          const n = collectListItem(item);
          if (n) root.children.push(n);
        }
      }
    }
  }
  return root;
}

/** 递归布局：根在最左侧，子节点向右逐层展开。
 *  节点 x = depth * LEVEL_GAP + width/2（节点中心 x）
 *  父节点 y = 子节点 y 的中点；叶节点 y 由游标累加
 *  返回分配占用的总高度
 */
function layoutTree(node: TreeNode, depth: number, yStart: number): number {
  const m = measure(node.text);
  node.width = m.width;
  node.height = m.height;
  node.x = depth * LEVEL_GAP + node.width / 2;

  if (!node.children || node.children.length === 0) {
    node.y = yStart + node.height / 2;
    return node.height;
  }

  let cursor = yStart;
  for (const child of node.children) {
    cursor += layoutTree(child, depth + 1, cursor);
  }
  // 父节点 y 居中于第一个/最后一个子节点的中点
  const first = node.children[0];
  const last = node.children[node.children.length - 1];
  node.y = (first.y + last.y) / 2;
  return cursor - yStart;
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
  const padding = 24;
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
      "font-family":
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
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

function getSvgXml(): string {
  const svg = svgRef.value!;
  // 用实际渲染宽高，避免 viewBox 转换造成导出变形
  const wrap = mapWrapRef.value!;
  const w = wrap.clientWidth || 800;
  const h = wrap.clientHeight || 600;
  const clone = svg.cloneNode(true) as SVGSVGElement;
  clone.setAttribute("width", String(w));
  clone.setAttribute("height", String(h));
  // 用真实 viewBox（已设置）
  clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
  // 加白底
  const vb = (svg.getAttribute("viewBox") || "0 0 800 600").split(/\s+/).map(Number);
  const bg = el("rect", { x: vb[0], y: vb[1], width: vb[2], height: vb[3], fill: "#ffffff" });
  clone.insertBefore(bg, clone.firstChild);
  return new XMLSerializer().serializeToString(clone);
}

function exportSVG() {
  const xml = getSvgXml();
  downloadBlob(new Blob([xml], { type: "image/svg+xml;charset=utf-8" }), "mindmap.svg");
  ElMessage.success("SVG 已导出");
}

async function exportPNG() {
  const xml = getSvgXml();
  const url = URL.createObjectURL(new Blob([xml], { type: "image/svg+xml;charset=utf-8" }));
  const img = new Image();
  await new Promise<void>((resolve, reject) => {
    img.onload = () => resolve();
    img.onerror = () => reject(new Error("SVG 转图片失败"));
    img.src = url;
  });
  const wrap = mapWrapRef.value!;
  const scale = 2;
  const canvas = document.createElement("canvas");
  canvas.width = (wrap.clientWidth || 800) * scale;
  canvas.height = (wrap.clientHeight || 600) * scale;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
  URL.revokeObjectURL(url);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
  if (blob) downloadBlob(blob, "mindmap.png");
  ElMessage.success("PNG 已导出");
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
  ElMessage.success("已加载示例");
}

/** 简单的滚轮缩放 + 拖拽平移 */
const viewState = reactive({ scale: 1, tx: 0, ty: 0 });
let dragStart: { x: number; y: number; tx: number; ty: number } | null = null;

function applyTransform() {
  const g = svgRef.value?.querySelector("#mm-root") as SVGElement | null;
  if (g) g.setAttribute("transform", `translate(${viewState.tx},${viewState.ty}) scale(${viewState.scale})`);
}

function onWheel(e: WheelEvent) {
  e.preventDefault();
  const delta = -e.deltaY * 0.001;
  viewState.scale = Math.min(3, Math.max(0.3, viewState.scale * (1 + delta)));
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
  const ro = new ResizeObserver(() => applyTransform());
  if (mapWrapRef.value) ro.observe(mapWrapRef.value);
});

onBeforeUnmount(() => {
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
}

.map-wrap:active {
  cursor: grabbing;
}

.map-svg {
  display: block;
  width: 100%;
  height: 100%;
  user-select: none;
}

html.dark {
  .map-wrap {
    background: var(--el-bg-color);
  }
}
</style>
