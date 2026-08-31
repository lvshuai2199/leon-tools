<template>
  <div class="trace-page" @dragover.prevent @drop.prevent="onDrop">
    <header class="trace-header">
      <div class="header-left">
        <el-button text @click="goHome">
          <el-icon class="mr-1"><Back /></el-icon>
          返回工作台
        </el-button>
        <span class="header-title">轨迹分析</span>
        <el-tag size="small" type="info">独立页面 · 无需权限</el-tag>
        <el-tag v-if="resolvedPluginLabel" size="small" type="success">{{ resolvedPluginLabel }}</el-tag>
      </div>
      <div class="header-right">
        <el-select v-model="workspace.plugin" class="plugin-select" @change="reparseAndDraw">
          <el-option label="自动识别插件包" value="auto" />
          <el-option label="WeldingTools 焊接工具" value="welding-tools" />
          <el-option label="FullFunctionWelding 全功能焊接" value="full-function" />
        </el-select>
        <el-dropdown @command="loadExample">
          <el-button>
            加载示例工程
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="welding-liner">WeldingTools · 直线焊接</el-dropdown-item>
              <el-dropdown-item command="welding-multipass">WeldingTools · 多层多道</el-dropdown-item>
              <el-dropdown-item command="full-liner">FullFunctionWelding · 直线+圆弧</el-dropdown-item>
              <el-dropdown-item command="full-multi">FullFunctionWelding · 多层多道</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="danger" plain @click="clearAll">清空数据</el-button>
      </div>
    </header>

    <section class="trace-toolbar">
      <div class="upload-row">
        <input
          ref="fileInputRef"
          type="file"
          multiple
          accept=".task,.script,.txt,.xml,.csv,.eli,.py,.jbi"
          class="file-input"
          @change="onFilesPicked"
        />
        <el-button type="primary" @click="fileInputRef?.click()">导入工程文件</el-button>
        <span class="file-hint">
          可同时选择 XML(.task)、脚本(.task.script) 与树结构(.txt)。当前：
          {{ fileSummary }}
        </span>
      </div>
      <div class="controls">
        <label class="checkbox-item">
          <input v-model="workspace.showLine" type="checkbox" @change="drawCharts" /> 显示轨迹连线
        </label>
        <label class="checkbox-item">
          <input v-model="workspace.showPoints" type="checkbox" @change="drawCharts" /> 显示轨迹点位
        </label>
        <label class="checkbox-item checkbox-divider">
          <input v-model="workspace.showMarkers" type="checkbox" @change="drawCharts" /> 显示参考点
        </label>
        <label class="checkbox-item checkbox-divider checkbox-warn">
          <input v-model="workspace.showArrows" type="checkbox" @change="drawCharts" /> 显示姿态箭头
        </label>
        <div v-if="statusMsg" class="status-msg">{{ statusMsg }}</div>
      </div>
    </section>

    <section class="trace-body">
      <aside class="tree-pane">
        <div class="pane-title">任务树</div>
        <el-tree
          v-if="treeData.length"
          :data="treeData"
          node-key="id"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="onTreeNodeClick"
        >
          <template #default="{ data }">
            <span class="tree-node" :class="`kind-${data.kind}`">
              <span class="tree-dot" />
              <span class="tree-label">{{ data.label }}</span>
              <span v-if="data.pose" class="tree-flag">点</span>
              <span v-if="data.kind === 'ref' || data.refs.length" class="tree-flag ref">参考</span>
            </span>
          </template>
        </el-tree>
        <el-empty v-else description="导入工程后显示任务树" :image-size="64" />
      </aside>

      <div class="chart-pane">
        <div ref="chart3D" class="chart-box" />
        <div ref="chart2D" class="chart-box chart-box-2d" />
      </div>

      <aside class="inspector-pane">
        <div class="pane-title">点位数据查看</div>
        <div class="data-label">拾取对象:</div>
        <div class="pick-type">{{ selected.type || "未选择点位" }}</div>

        <div class="data-label">坐标数组 [X, Y, Z]:</div>
        <div class="data-display">{{ selected.coord || "点击树节点或图中轨迹" }}</div>

        <div class="data-label mt-3">姿态 [Rx, Ry, Rz]:</div>
        <div class="data-display">{{ selected.orientation || "未选择点位" }}</div>

        <div class="data-label mt-3">枪轴与 XY 平面夹角:</div>
        <div class="data-display">{{ selected.angleXY || "未选择点位" }}</div>

        <div class="data-label mt-3">枪轴与 YZ 平面夹角:</div>
        <div class="data-display">{{ selected.angleYZ || "未选择点位" }}</div>

        <div class="data-label mt-3">枪轴与 XZ 平面夹角:</div>
        <div class="data-display">{{ selected.angleXZ || "未选择点位" }}</div>

        <div class="data-label mt-3">前进角:</div>
        <div class="data-display">{{ selected.angleForward || "未选择点位" }}</div>

        <div class="data-label mt-3">工作角 (X旋转):</div>
        <div class="data-display">{{ selected.angleWork || "未选择点位" }}</div>

        <el-button type="warning" class="mt-3 w-full" @click="copyData">复制坐标</el-button>

        <div class="tips">
          <strong>说明：</strong><br />
          • WeldingTools 脚本多为直接 pose 数组；FullFunctionWelding 使用 full_apply_touch_offset。<br />
          • 红色菱形为参考点 / 跟踪坐标系，可通过「显示参考点」开关。<br />
          • 橙色虚线按程序顺序连接 movej 接近/离开段，不跨焊道。<br />
          • 青色曲线为 movec 圆弧。经过点/结束点标记仅在打开「显示轨迹点位」时出现，摆动密集时会自动缩小。<br />
          • 数据缓存在本机，刷新不会丢失，点「清空数据」后才消失。
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Back } from "@element-plus/icons-vue";
import {
  classifyProjectFile,
  parseProject,
  buildDisplayPath,
  buildMovejPath,
  type PluginKind,
  type TaskTreeNode,
  type TrajData,
  type TrajPoint,
} from "./parse";
import { clearWorkspace, emptyWorkspace, hasWorkspaceContent, loadWorkspace, saveWorkspace } from "./persist";

defineOptions({
  name: "TraceAnalysis",
  inheritAttrs: false,
});

type PlotlyApi = {
  newPlot: (el: HTMLElement, data: unknown, layout: unknown) => Promise<unknown> | void;
  purge: (el: HTMLElement) => void;
};

type PlotlyEl = HTMLElement & {
  on?: (event: string, cb: (ev: any) => void) => void;
};

const EXAMPLES: Record<string, { plugin: PluginKind; files: string[] }> = {
  "welding-liner": {
    plugin: "welding-tools",
    files: [
      "/elite-task/WeldingTools/weldingtools-liner.task",
      "/elite-task/WeldingTools/weldingtools-liner.task.script",
      "/elite-task/WeldingTools/weldingtools-liner.txt",
    ],
  },
  "welding-multipass": {
    plugin: "welding-tools",
    files: [
      "/elite-task/WeldingTools/weldingtools-multipass.task",
      "/elite-task/WeldingTools/weldingtools-multipass.task.script",
      "/elite-task/WeldingTools/weldingtools-multipass.txt",
    ],
  },
  "full-liner": {
    plugin: "full-function",
    files: [
      "/elite-task/FullFunctionWelding/fullFunctionweld-liner.task",
      "/elite-task/FullFunctionWelding/fullFunctionweld-liner.task.script",
      "/elite-task/FullFunctionWelding/fullFunctionweld-liner.txt",
    ],
  },
  "full-multi": {
    plugin: "full-function",
    files: [
      "/elite-task/FullFunctionWelding/fullFunctionweld-multi.task",
      "/elite-task/FullFunctionWelding/fullFunctionweld-multi.task.script",
      "/elite-task/FullFunctionWelding/fullFunctionweld-multi.txt",
    ],
  },
};

const router = useRouter();
const route = useRoute();
const fileInputRef = ref<HTMLInputElement>();
const chart3D = ref<HTMLElement>();
const chart2D = ref<HTMLElement>();

const workspace = reactive(loadWorkspace() || emptyWorkspace());
const treeData = ref<TaskTreeNode[]>([]);
const statusMsg = ref("");
const resolvedPlugin = ref("");
const pickedTreePose = ref<TrajPoint | null>(null);

const selected = reactive({
  type: "",
  coord: "",
  orientation: "",
  angleXY: "",
  angleYZ: "",
  angleXZ: "",
  angleForward: "",
  angleWork: "",
});

let lastSelectedValue = "";
let globalData: TrajData = { main: [], markers: [] };

const resolvedPluginLabel = computed(() => {
  if (resolvedPlugin.value === "welding-tools") return "WeldingTools";
  if (resolvedPlugin.value === "full-function") return "FullFunctionWelding";
  return "";
});

const fileSummary = computed(() => {
  const parts = [workspace.xmlName, workspace.scriptName, workspace.treeName].filter(Boolean);
  return parts.length ? parts.join(" / ") : "尚未导入";
});

async function getPlotly(): Promise<PlotlyApi> {
  const w = window as Window & { Plotly?: PlotlyApi };
  if (w.Plotly) return w.Plotly;
  await new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "/lib/plotly.min.js";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Plotly 加载失败"));
    document.head.appendChild(script);
  });
  if (!w.Plotly) throw new Error("Plotly 未挂载");
  return w.Plotly;
}

function persistNow() {
  saveWorkspace({ ...workspace });
}

function goHome() {
  router.push("/");
}

function applyParse() {
  const result = parseProject({
    xmlText: workspace.xmlText,
    scriptText: workspace.scriptText,
    treeText: workspace.treeText,
    plugin: workspace.plugin,
  });
  treeData.value = result.tree;
  globalData = result.traj;
  resolvedPlugin.value = result.plugin;
  return result;
}

function reparseAndDraw() {
  applyParse();
  persistNow();
  drawCharts();
}

async function ingestFiles(files: File[]) {
  if (!files.length) return;
  for (const file of files) {
    const content = await file.text();
    const kind = classifyProjectFile(file.name, content);
    if (kind === "xml") {
      workspace.xmlText = content;
      workspace.xmlName = file.name;
    } else if (kind === "script") {
      workspace.scriptText = content;
      workspace.scriptName = file.name;
    } else if (kind === "tree") {
      workspace.treeText = content;
      workspace.treeName = file.name;
    } else if (/\.(csv|eli|py|jbi)$/i.test(file.name) || /\b(movej|movel|movep|movec)\s*\(/.test(content)) {
      workspace.scriptText = content;
      workspace.scriptName = file.name;
    }
  }
  pickedTreePose.value = null;
  persistNow();
  const result = applyParse();
  if (!result.traj.main.length && !result.traj.markers.length) {
    ElMessage.warning("未能从工程中解析出轨迹，请确认插件包选择与文件是否匹配。");
    return;
  }
  await drawCharts();
  ElMessage.success("工程已解析，刷新页面不会丢失。");
}

function onFilesPicked(ev: Event) {
  const input = ev.target as HTMLInputElement;
  ingestFiles(Array.from(input.files || [])).finally(() => {
    input.value = "";
  });
}

function onDrop(ev: DragEvent) {
  ingestFiles(Array.from(ev.dataTransfer?.files || []));
}

async function loadExample(key: string) {
  const example = EXAMPLES[key];
  if (!example) return;
  try {
    const texts = await Promise.all(
      example.files.map(async (url) => {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`无法加载 ${url}`);
        return { url, content: await res.text() };
      })
    );
    workspace.plugin = example.plugin;
    workspace.xmlText = "";
    workspace.scriptText = "";
    workspace.treeText = "";
    workspace.xmlName = "";
    workspace.scriptName = "";
    workspace.treeName = "";
    for (const item of texts) {
      const name = item.url.split("/").pop() || item.url;
      const kind = classifyProjectFile(name, item.content);
      if (kind === "xml") {
        workspace.xmlText = item.content;
        workspace.xmlName = name;
      } else if (kind === "script") {
        workspace.scriptText = item.content;
        workspace.scriptName = name;
      } else if (kind === "tree") {
        workspace.treeText = item.content;
        workspace.treeName = name;
      }
    }
    pickedTreePose.value = null;
    persistNow();
    applyParse();
    await drawCharts();
    ElMessage.success("示例工程已加载");
  } catch {
    ElMessage.error("示例工程加载失败，请确认 elite-task 文件可用，或直接导入本地工程。");
  }
}

function clearAll() {
  Object.assign(workspace, emptyWorkspace());
  treeData.value = [];
  globalData = { main: [], markers: [] };
  resolvedPlugin.value = "";
  pickedTreePose.value = null;
  statusMsg.value = "";
  selected.type = "";
  selected.coord = "";
  selected.orientation = "";
  selected.angleXY = "";
  selected.angleYZ = "";
  selected.angleXZ = "";
  selected.angleForward = "";
  selected.angleWork = "";
  lastSelectedValue = "";
  clearWorkspace();
  const Plotly = (window as Window & { Plotly?: PlotlyApi }).Plotly;
  if (Plotly && chart3D.value) Plotly.purge(chart3D.value);
  if (Plotly && chart2D.value) Plotly.purge(chart2D.value);
  ElMessage.success("已清空本地缓存");
}

function preparePath(points: TrajPoint[]) {
  return buildDisplayPath(points);
}

function eulerToToolZ(rx: number, ry: number, rz: number) {
  const crx = Math.cos(rx),
    srx = Math.sin(rx);
  const cry = Math.cos(ry),
    sry = Math.sin(ry);
  const crz = Math.cos(rz),
    srz = Math.sin(rz);
  return {
    u: crz * sry * crx + srz * srx,
    v: srz * sry * crx - crz * srx,
    w: cry * crx,
  };
}

function computeSpan(points: TrajPoint[]) {
  if (!points.length) return 0.1;
  const xs = points.map((p) => p.x),
    ys = points.map((p) => p.y),
    zs = points.map((p) => p.z);
  const dx = Math.max(...xs) - Math.min(...xs);
  const dy = Math.max(...ys) - Math.min(...ys);
  const dz = Math.max(...zs) - Math.min(...zs);
  return Math.sqrt(dx * dx + dy * dy + dz * dz) || 0.1;
}

function angleWithPlane(rx: number, ry: number, rz: number, normalAxis: "x" | "y" | "z") {
  const dir = eulerToToolZ(rx, ry, rz);
  const len = Math.sqrt(dir.u * dir.u + dir.v * dir.v + dir.w * dir.w) || 1;
  const normal = normalAxis === "x" ? dir.u : normalAxis === "y" ? dir.v : dir.w;
  const sinAngle = Math.abs(normal) / len;
  return (Math.asin(Math.min(1, sinAngle)) * 180) / Math.PI;
}

function forwardAngle(rx: number, ry: number, rz: number, tangent: number[]) {
  const dir = eulerToToolZ(rx, ry, rz);
  const lenDir = Math.sqrt(dir.u * dir.u + dir.v * dir.v + dir.w * dir.w) || 1;
  const hLen = Math.hypot(tangent[0], tangent[1]);
  if (hLen < 1e-9) {
    return (Math.asin(Math.min(1, Math.abs(dir.w) / lenDir)) * 180) / Math.PI;
  }
  const dotAbs = Math.abs(dir.u * (tangent[0] / hLen) + dir.v * (tangent[1] / hLen));
  return (Math.asin(Math.min(1, dotAbs / lenDir)) * 180) / Math.PI;
}

function workAngle(rx: number, ry: number, rz: number, tangent: number[]) {
  const dir = eulerToToolZ(rx, ry, rz);
  const lenDir = Math.sqrt(dir.u * dir.u + dir.v * dir.v + dir.w * dir.w) || 1;
  const hLen = Math.hypot(tangent[0], tangent[1]);
  if (hLen < 1e-9) {
    return (Math.asin(Math.min(1, Math.abs(dir.w) / lenDir)) * 180) / Math.PI;
  }
  const lx = -tangent[1] / hLen;
  const ly = tangent[0] / hLen;
  const lateral = dir.u * lx + dir.v * ly;
  const vertical = dir.w;
  return (Math.atan2(Math.abs(vertical), Math.abs(lateral)) * 180) / Math.PI;
}

function forwardDirectionAt(idx: number): number[] | null {
  const pts = globalData.main;
  if (!Number.isFinite(idx) || idx < 0 || idx >= pts.length) return null;

  const kind = pts[idx].type;
  const isWeld = ["movep", "movel", "movec", "movec_end"].includes(kind);

  let prevIdx: number, nextIdx: number;
  if (isWeld) {
    prevIdx = idx - 1;
    while (prevIdx >= 0 && (pts[prevIdx].type !== kind || pts[prevIdx].beadId !== pts[idx].beadId)) prevIdx--;
    nextIdx = idx + 1;
    while (nextIdx < pts.length && (pts[nextIdx].type !== kind || pts[nextIdx].beadId !== pts[idx].beadId)) nextIdx++;
  } else {
    prevIdx = idx - 1;
    nextIdx = idx + 1;
  }

  const prev = prevIdx >= 0 ? pts[prevIdx] : null;
  const next = nextIdx < pts.length ? pts[nextIdx] : null;

  if (prev && next) {
    return [next.x - prev.x, next.y - prev.y, next.z - prev.z];
  } else if (next) {
    return [next.x - pts[idx].x, next.y - pts[idx].y, next.z - pts[idx].z];
  } else if (prev) {
    return [pts[idx].x - prev.x, pts[idx].y - prev.y, pts[idx].z - prev.z];
  }
  return null;
}

function findSelectedPoint(pts: any): TrajPoint | undefined {
  if (pts && Number.isFinite(pts.customdata)) {
    const idx = pts.customdata;
    if (idx >= 0 && idx < globalData.main.length) return globalData.main[idx];
  }
  const eps = 1e-9;
  const match = (p: TrajPoint) =>
    Math.abs(p.x - pts.x) <= eps &&
    Math.abs(p.y - pts.y) <= eps &&
    (pts.z === undefined || Math.abs(p.z - pts.z) <= eps);
  return globalData.main.find(match) || globalData.markers.find(match);
}

function findMainIndex(pts: any): number {
  if (pts && Number.isFinite(pts.customdata)) {
    const idx = pts.customdata;
    if (idx >= 0 && idx < globalData.main.length) return idx;
  }
  const eps = 1e-9;
  return globalData.main.findIndex(
    (p) =>
      Math.abs(p.x - pts.x) <= eps &&
      Math.abs(p.y - pts.y) <= eps &&
      (pts.z === undefined || Math.abs(p.z - pts.z) <= eps)
  );
}

function fillInspector(point: TrajPoint, title: string, idx: number | string) {
  selected.type = `${title}  [Idx: ${idx}]  ${point.type || ""}`;
  const valStr = `[${point.x.toFixed(6)}, ${point.y.toFixed(6)}, ${point.z.toFixed(6)}]`;
  selected.coord = valStr;
  lastSelectedValue = valStr;

  if (Number.isFinite(point.rx)) {
    selected.orientation = `[${point.rx.toFixed(6)}, ${point.ry.toFixed(6)}, ${point.rz.toFixed(6)}]`;
    selected.angleXY = `${angleWithPlane(point.rx, point.ry, point.rz, "z").toFixed(2)}°`;
    selected.angleYZ = `${angleWithPlane(point.rx, point.ry, point.rz, "x").toFixed(2)}°`;
    selected.angleXZ = `${angleWithPlane(point.rx, point.ry, point.rz, "y").toFixed(2)}°`;

    const mainIdx = typeof idx === "number" ? idx : findMainIndex(point);
    const tangent = forwardDirectionAt(mainIdx);
    selected.angleForward = tangent
      ? `${forwardAngle(point.rx, point.ry, point.rz, tangent).toFixed(2)}°`
      : "无法计算前进方向";
    selected.angleWork = tangent
      ? `${workAngle(point.rx, point.ry, point.rz, tangent).toFixed(2)}°`
      : "无法计算前进方向";
  } else {
    selected.orientation = "该点位无姿态数据";
    selected.angleXY = "该点位无姿态数据";
    selected.angleYZ = "该点位无姿态数据";
    selected.angleXZ = "该点位无姿态数据";
    selected.angleForward = "该点位无姿态数据";
    selected.angleWork = "该点位无姿态数据";
  }
}

function updateInspector(pts: any) {
  const traceName = pts.fullData?.name || "";
  const idx = pts.customdata ?? "-";
  const motionType = pts.text || "";
  const point = findSelectedPoint(pts);
  if (point) {
    fillInspector(point, traceName, idx);
    return;
  }
  const x = pts.x.toFixed(6);
  const y = pts.y.toFixed(6);
  const z = pts.z !== undefined ? pts.z.toFixed(6) : "N/A";
  selected.type = `${traceName}  [Idx: ${idx}]  ${motionType}`;
  selected.coord = `[${x}, ${y}, ${z}]`;
  lastSelectedValue = selected.coord;
}

function onTreeNodeClick(node: TaskTreeNode) {
  const point = node.pose || node.refs[0];
  if (!point) {
    ElMessage.info("该节点没有坐标数据");
    return;
  }
  pickedTreePose.value = point;
  fillInspector(point, node.label, node.id);
  drawCharts();
}

async function drawCharts() {
  persistNow();
  const data = globalData;
  if (!data.main.length && !data.markers.length) {
    if (hasWorkspaceContent(workspace)) {
      ElMessage.warning("未能识别到有效的轨迹坐标数据，请检查插件包与文件格式。");
    }
    return;
  }

  let mode = "none";
  if (workspace.showLine && workspace.showPoints) mode = "lines+markers";
  else if (workspace.showLine) mode = "lines";
  else if (workspace.showPoints) mode = "markers";

  const traces3D: any[] = [];
  const traces2D: any[] = [];

  if (data.main.length > 0) {
    const path = preparePath(data.main);
    const hoverText = path.types.map((t, i) => {
      const name = typeof path.idx[i] === "number" ? data.main[path.idx[i] as number]?.name : "";
      return [t ? `Type: ${t}` : "", name ? `Node: ${name}` : ""].filter(Boolean).join(" · ");
    });
    const weldCount = data.main.filter((p) => ["movep", "movel", "movec", "movec_end"].includes(p.type)).length;
    const denseWeld = weldCount > 24 || data.main.filter((p) => p.type === "movec").length > 6;

    traces3D.push({
      x: path.x,
      y: path.y,
      z: path.z,
      name: "完整轨迹 (all)",
      mode,
      type: "scatter3d",
      line: { color: "#3498db", width: denseWeld ? 2 : 4 },
      marker: { size: denseWeld ? 1.5 : 2, color: "#2980b9" },
      customdata: path.idx,
      text: hoverText,
      hovertemplate: "Idx: %{customdata}<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>Z: %{z:.6f}<br>%{text}<extra></extra>",
    });
    traces2D.push({
      x: path.x,
      y: path.y,
      name: "平面投影 (all)",
      mode,
      type: "scatter",
      line: { color: "#3498db", width: denseWeld ? 1.5 : 2 },
      marker: { size: denseWeld ? 3 : 4 },
      customdata: path.idx,
      text: hoverText,
      hovertemplate: "Idx: %{customdata}<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>%{text}<extra></extra>",
    });

    const movejPts = data.main.filter((p) => p.type === "movej");
    const movejIdx = data.main.map((_, i) => i).filter((i) => data.main[i].type === "movej");
    if (movejPts.length > 0) {
      traces3D.push({
        x: movejPts.map((p) => p.x),
        y: movejPts.map((p) => p.y),
        z: movejPts.map((p) => p.z),
        name: "movej 接近点",
        mode: "markers",
        type: "scatter3d",
        marker: { size: 4, color: "#e67e22", symbol: "circle-open" },
        customdata: movejIdx,
        hovertemplate: "Idx: %{customdata}<br>movej<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>Z: %{z:.6f}<extra></extra>",
      });
      traces2D.push({
        x: movejPts.map((p) => p.x),
        y: movejPts.map((p) => p.y),
        name: "movej 接近点",
        mode: "markers",
        type: "scatter",
        marker: { size: 6, color: "#e67e22", symbol: "circle-open" },
        customdata: movejIdx,
        hovertemplate: "Idx: %{customdata}<br>movej<br>X: %{x:.6f}<br>Y: %{y:.6f}<extra></extra>",
      });

      if (workspace.showLine) {
        const movejPath = buildMovejPath(data.main);
        traces3D.push({
          x: movejPath.x,
          y: movejPath.y,
          z: movejPath.z,
          name: "movej 接近路径",
          mode: "lines",
          type: "scatter3d",
          line: { color: "#e67e22", width: 2, dash: "dash" },
          hoverinfo: "skip",
        });
        traces2D.push({
          x: movejPath.x,
          y: movejPath.y,
          name: "movej 接近路径",
          mode: "lines",
          type: "scatter",
          line: { color: "#e67e22", width: 2, dash: "dash" },
          hoverinfo: "skip",
        });
      }
    }

    const weldLinear = data.main
      .map((p, i) => ({ p, i }))
      .filter(({ p }) => p.type === "movep" || p.type === "movel");
    const viaPts = data.main.map((p, i) => ({ p, i })).filter(({ p }) => p.type === "movec");
    const endPts = data.main.map((p, i) => ({ p, i })).filter(({ p }) => p.type === "movec_end");

    if (path.arcX.length > 0 && workspace.showLine) {
      const arcW3 = denseWeld ? 3 : 5;
      const arcW2 = denseWeld ? 2 : 3;
      traces3D.push({
        x: path.arcX,
        y: path.arcY,
        z: path.arcZ,
        name: "圆弧 (movec)",
        mode: "lines",
        type: "scatter3d",
        line: { color: "#16a085", width: arcW3 },
        hoverinfo: "skip",
      });
      traces2D.push({
        x: path.arcX,
        y: path.arcY,
        name: "圆弧 (movec)",
        mode: "lines",
        type: "scatter",
        line: { color: "#16a085", width: arcW2 },
        hoverinfo: "skip",
      });
    }

    const pushMarkers = (
      list: { p: TrajPoint; i: number }[],
      name: string,
      color: string,
      symbol: string,
      size3d: number,
      size2d: number
    ) => {
      if (!list.length) return;
      traces3D.push({
        x: list.map(({ p }) => p.x),
        y: list.map(({ p }) => p.y),
        z: list.map(({ p }) => p.z),
        name,
        mode: "markers",
        type: "scatter3d",
        marker: { size: size3d, color, symbol },
        customdata: list.map(({ i }) => i),
        text: list.map(({ p }) => p.type),
        hovertemplate: "Idx: %{customdata}<br>%{text}<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>Z: %{z:.6f}<extra></extra>",
      });
      traces2D.push({
        x: list.map(({ p }) => p.x),
        y: list.map(({ p }) => p.y),
        name,
        mode: "markers",
        type: "scatter",
        marker: { size: size2d, color, symbol: symbol === "diamond" ? "diamond" : symbol === "square" ? "square" : "circle" },
        customdata: list.map(({ i }) => i),
        hovertemplate: "Idx: %{customdata}<br>X: %{x:.6f}<br>Y: %{y:.6f}<extra></extra>",
      });
    };

    if (workspace.showPoints) {
      const weld3 = denseWeld ? 2 : 3;
      const weld2 = denseWeld ? 3 : 5;
      const via3 = denseWeld ? 2 : 4;
      const via2 = denseWeld ? 4 : 7;
      pushMarkers(weldLinear, "焊道路径 (movep/movel)", "#27ae60", "circle", weld3, weld2);
      pushMarkers(viaPts, "圆弧经过点", "#16a085", "diamond", via3, via2);
      pushMarkers(endPts, "圆弧结束点", "#1abc9c", "square", via3, via2);
    }
  }

  if (workspace.showMarkers && data.markers.length > 0) {
    const mX = data.markers.map((p) => p.x);
    const mY = data.markers.map((p) => p.y);
    const mZ = data.markers.map((p) => p.z);
    traces3D.push({
      x: mX,
      y: mY,
      z: mZ,
      name: "参考点",
      mode: "markers",
      type: "scatter3d",
      marker: { color: "#e74c3c", size: 8, symbol: "diamond" },
      text: data.markers.map((p) => p.name || "参考点"),
      hovertemplate: "%{text}<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>Z: %{z:.6f}<extra></extra>",
    });
    traces2D.push({
      x: mX,
      y: mY,
      name: "参考点",
      mode: "markers",
      type: "scatter",
      marker: { color: "#e74c3c", size: 10, symbol: "diamond" },
      text: data.markers.map((p) => p.name || "参考点"),
      hovertemplate: "%{text}<br>X: %{x:.6f}<br>Y: %{y:.6f}<extra></extra>",
    });
  }

  if (pickedTreePose.value) {
    const p = pickedTreePose.value;
    traces3D.push({
      x: [p.x],
      y: [p.y],
      z: [p.z],
      name: "树节点选中",
      mode: "markers",
      type: "scatter3d",
      marker: { color: "#9b59b6", size: 10, symbol: "x" },
    });
    traces2D.push({
      x: [p.x],
      y: [p.y],
      name: "树节点选中",
      mode: "markers",
      type: "scatter",
      marker: { color: "#9b59b6", size: 12, symbol: "x" },
    });
  }

  if (workspace.showArrows && data.main.length > 0) {
    const span = computeSpan(data.main);
    const arrowLen = span * 0.06;
    const step = Math.max(1, Math.floor(data.main.length / 80));
    const arrowPts: { x: number; y: number; z: number; u: number; v: number; w: number }[] = [];
    for (let i = 0; i < data.main.length; i += step) {
      const p = data.main[i];
      const dir = eulerToToolZ(p.rx || 0, p.ry || 0, p.rz || 0);
      arrowPts.push({ x: p.x, y: p.y, z: p.z, u: dir.u, v: dir.v, w: dir.w });
    }

    const arrowX: (number | null)[] = [],
      arrowY: (number | null)[] = [],
      arrowZ: (number | null)[] = [];
    for (const a of arrowPts) {
      arrowX.push(a.x, a.x + a.u * arrowLen, null);
      arrowY.push(a.y, a.y + a.v * arrowLen, null);
      arrowZ.push(a.z, a.z + a.w * arrowLen, null);
    }

    traces3D.push({
      x: arrowX,
      y: arrowY,
      z: arrowZ,
      name: "姿态箭头 (Tool Z)",
      mode: "lines",
      type: "scatter3d",
      line: { color: "#e74c3c", width: 2 },
      hoverinfo: "skip",
      showlegend: true,
    });
    traces3D.push({
      x: arrowPts.map((a) => a.x + a.u * arrowLen),
      y: arrowPts.map((a) => a.y + a.v * arrowLen),
      z: arrowPts.map((a) => a.z + a.w * arrowLen),
      name: "箭头尖端",
      mode: "markers",
      type: "scatter3d",
      marker: { color: "#c0392b", size: 3, symbol: "triangle-up" },
      hoverinfo: "skip",
      showlegend: false,
    });
  }

  const layout3D = {
    scene: { aspectmode: "data", xaxis: { title: "X (m)" }, yaxis: { title: "Y (m)" }, zaxis: { title: "Z (m)" } },
    margin: { l: 0, r: 0, b: 0, t: 30 },
    hovermode: "closest",
    legend: { x: 0.01, y: 1, font: { size: 10 } },
  };
  const layout2D = {
    xaxis: { title: "X (m)", scaleanchor: "y" },
    yaxis: { title: "Y (m)" },
    hovermode: "closest",
    legend: { x: 0.01, y: 1, font: { size: 10 } },
    margin: { t: 20 },
  };

  const Plotly = await getPlotly();
  if (chart3D.value) Plotly.newPlot(chart3D.value, traces3D, layout3D);
  if (chart2D.value) Plotly.newPlot(chart2D.value, traces2D, layout2D);

  [chart3D, chart2D].forEach((chart) => {
    (chart.value as PlotlyEl | undefined)?.on?.("plotly_click", (ev: any) => {
      if (ev.points?.length > 0) updateInspector(ev.points[0]);
    });
  });

  const cnt: Record<string, number> = {};
  data.main.forEach((p) => {
    cnt[p.type] = (cnt[p.type] || 0) + 1;
  });
  const parts: string[] = [];
  if (cnt.movej) parts.push(`${cnt.movej} movej`);
  if (cnt.movel) parts.push(`${cnt.movel} movel`);
  if (cnt.movep) parts.push(`${cnt.movep} movep`);
  if (cnt.movec) parts.push(`${cnt.movec} movec`);
  if (cnt.movec_end) parts.push(`${cnt.movec_end} movec_end`);
  if (cnt.csv) parts.push(`${cnt.csv} csv`);

  statusMsg.value = `点位: ${data.main.length} (${parts.join(", ") || "无"})${
    data.markers.length ? ` | 参考点: ${data.markers.length}` : ""
  }`;
}

function copyData() {
  if (!lastSelectedValue) return;
  navigator.clipboard.writeText(lastSelectedValue).then(() => {
    ElMessage.success("复制成功");
  });
}

watch(
  () => ({
    plugin: workspace.plugin,
    showLine: workspace.showLine,
    showPoints: workspace.showPoints,
    showMarkers: workspace.showMarkers,
    showArrows: workspace.showArrows,
  }),
  () => persistNow()
);

onMounted(async () => {
  if (route.path !== "/trace") {
    router.replace("/trace");
    return;
  }
  document.title = "轨迹分析";
  if (!hasWorkspaceContent(workspace)) return;
  applyParse();
  await nextTick();
  if (globalData.main.length || globalData.markers.length) {
    await drawCharts();
  }
});

onBeforeUnmount(() => {
  const Plotly = (window as Window & { Plotly?: PlotlyApi }).Plotly;
  if (Plotly && chart3D.value) Plotly.purge(chart3D.value);
  if (Plotly && chart2D.value) Plotly.purge(chart2D.value);
});
</script>

<style lang="scss" scoped>
.trace-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 100vh;
  overflow: hidden;
  background: var(--el-bg-color-page);
}

.trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  height: 56px;
  padding: 0 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);

  .header-left,
  .header-right {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }

  .header-title {
    font-size: 16px;
    font-weight: 700;
  }

  .plugin-select {
    width: 240px;
  }
}

.trace-toolbar {
  flex-shrink: 0;
  padding: 10px 16px 8px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-input {
  display: none;
}

.file-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.controls {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.checkbox-item {
  display: flex;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: #444;
  cursor: pointer;

  input {
    margin-right: 6px;
    width: 15px;
    height: 15px;
  }
}

.checkbox-divider {
  padding-left: 15px;
  border-left: 2px solid var(--el-border-color);
}

.checkbox-warn {
  color: #e67e22;
}

.status-msg {
  margin-left: auto;
  padding: 6px 12px;
  font-size: 12px;
  color: #155724;
  background: #d4edda;
  border-radius: 4px;
}

.trace-body {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 300px;
  gap: 12px;
  flex: 1;
  min-height: 0;
  padding: 12px 16px 16px;
}

.tree-pane,
.inspector-pane {
  min-height: 0;
  overflow: auto;
  padding: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.pane-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.tree-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
}

.kind-ref .tree-dot {
  background: #e74c3c;
}

.kind-move .tree-dot {
  background: #e67e22;
}

.kind-weld .tree-dot {
  background: #27ae60;
}

.tree-flag {
  padding: 0 4px;
  font-size: 10px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 3px;

  &.ref {
    color: #e74c3c;
    background: #fdecea;
  }
}

.chart-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  gap: 12px;
}

.chart-box {
  flex: 1;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.chart-box-2d {
  flex: 0.7;
}

.data-label {
  margin: 8px 0 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.pick-type {
  font-weight: bold;
  font-size: 14px;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.data-display {
  min-height: 36px;
  padding: 8px 12px;
  font-family: monospace;
  font-size: 13px;
  color: #74b9ff;
  background: #2d3436;
  border: 1px solid #000;
  border-radius: 6px;
  word-break: break-all;
  line-height: 1.5;
}

.tips {
  margin-top: 16px;
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: #666;
  background: #f1f3f5;
  border-radius: 6px;
}

html.dark {
  .tips {
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color);
  }

  .checkbox-item {
    color: var(--el-text-color-regular);
  }

  .chart-box {
    background: var(--el-bg-color);
  }
}

@media (max-width: 1100px) {
  .trace-body {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .chart-box {
    height: 360px;
    flex: none;
  }
}
</style>
