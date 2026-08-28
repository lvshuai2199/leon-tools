<template>
  <div class="trace-container">
    <el-card shadow="never" class="mb-4">
      <template #header>
        <div class="flex-x-between">
          <span class="font-bold">机器人轨迹交互分析工具</span>
          <el-tag type="info" size="small">EliScript / CSV · 本地解析</el-tag>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="12" :xs="24">
          <el-text tag="b" size="small" class="mb-1 block">粘贴 EliScript 脚本内容 (movej/movel/movep/movec)</el-text>
          <el-input
            v-model="scriptInput"
            type="textarea"
            :rows="5"
            placeholder="在此粘贴 Elite/A9 脚本内容..."
          />
          <el-button type="primary" class="mt-2 w-full" @click="processScript">
            解析文本
          </el-button>
        </el-col>
        <el-col :span="12" :xs="24">
          <el-text tag="b" size="small" class="mb-1 block">导入文件</el-text>
          <input
            ref="fileInputRef"
            type="file"
            accept=".csv,.txt,.jbi,.eli,.py"
            class="file-input"
          />
          <el-button type="success" class="mt-1 mb-2 w-full" @click="processFile">
            读取外部文件
          </el-button>
          <div class="hint">
            支持 movej/movel/movep/movec，Z 突变 &gt; 5mm 自动断开连线
          </div>
        </el-col>
      </el-row>

      <!-- 控制面板 -->
      <div class="controls">
        <label class="checkbox-item">
          <input v-model="showLine" type="checkbox" @change="drawCharts" /> 显示轨迹连线
        </label>
        <label class="checkbox-item">
          <input v-model="showPoints" type="checkbox" @change="drawCharts" /> 显示轨迹点位
        </label>
        <label class="checkbox-item checkbox-divider">
          <input v-model="showMarkers" type="checkbox" @change="drawCharts" /> 显示基准标记点 (Frame)
        </label>
        <label class="checkbox-item checkbox-divider checkbox-warn">
          <input v-model="showArrows" type="checkbox" @change="drawCharts" /> 显示姿态箭头 (Orientation)
        </label>
        <div v-if="statusMsg" class="status-msg">{{ statusMsg }}</div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="17" :xs="24">
        <div ref="chart3D" class="chart-box" />
        <div ref="chart2D" class="chart-box" />
      </el-col>

      <!-- 右侧详情面板 -->
      <el-col :span="7" :xs="24">
        <el-card shadow="never" class="inspector-panel">
          <template #header>
            <span class="font-bold">🎯 点位数据查看</span>
          </template>

          <div class="data-label">拾取对象:</div>
          <div class="pick-type">{{ selected.type || "未选择点位" }}</div>

          <div class="data-label">坐标数组 [X, Y, Z]:</div>
          <div class="data-display">{{ selected.coord || "在图中点击点位或轨迹" }}</div>

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
            <strong>操作技巧：</strong><br />
            • 红色箭头代表工具 Z 轴方向（姿态）。<br />
            • 橙色虚线代表 movej 关节接近移动。<br />
            • 绿色圆点代表焊接路径 movep/movel/movec。<br />
            • 红色菱形代表 full_tracking_frame 基准。<br />
            • 连线断开处表示机器人发生了大幅度抬升。<br />
            • 点击点位可在右侧查看姿态、水平面夹角及前进角。
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import Papa from "papaparse";

defineOptions({
  name: "Trace",
  inheritAttrs: false,
});

type PlotlyApi = {
  newPlot: (el: HTMLElement, data: unknown, layout: unknown) => Promise<unknown> | void;
  purge: (el: HTMLElement) => void;
};

type PlotlyEl = HTMLElement & {
  on?: (event: string, cb: (ev: any) => void) => void;
};

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

interface TrajPoint {
  x: number;
  y: number;
  z: number;
  rx: number;
  ry: number;
  rz: number;
  type: string;
  beadId?: number;
}

interface TrajData {
  main: TrajPoint[];
  markers: TrajPoint[];
}

const scriptInput = ref("");
const fileInputRef = ref<HTMLInputElement>();
const chart3D = ref<HTMLElement>();
const chart2D = ref<HTMLElement>();

const showLine = ref(true);
const showPoints = ref(false);
const showMarkers = ref(false);
const showArrows = ref(false);

const statusMsg = ref("");

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

/** 解析 EliScript / CSV 文本为轨迹数据 */
function parseTrajectoryData(text: string): TrajData {
  const data: TrajData = { main: [], markers: [] };

  const motionReg =
    /(movej|movel|movep|movec)\s*\([\s\S]{0,500}?full_apply_touch_offset\s*\(\s*\[\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)/g;

  const markerReg =
    /full_tracking_frame\s*=\s*\[\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)/g;

  let m: RegExpExecArray | null;
  while ((m = motionReg.exec(text)) !== null) {
    data.main.push({
      x: parseFloat(m[2]),
      y: parseFloat(m[3]),
      z: parseFloat(m[4]),
      rx: parseFloat(m[5]),
      ry: parseFloat(m[6]),
      rz: parseFloat(m[7]),
      type: m[1],
    });
  }

  const movecEndReg =
    /movec\s*\([\s\S]{0,500}?full_apply_touch_offset\s*\(\s*\[[^\]]+\]\s*\)[\s\S]{0,200}?full_apply_touch_offset\s*\(\s*\[\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)\s*,\s*([^,\]]+)/g;
  while ((m = movecEndReg.exec(text)) !== null) {
    data.main.push({
      x: parseFloat(m[1]),
      y: parseFloat(m[2]),
      z: parseFloat(m[3]),
      rx: parseFloat(m[4]),
      ry: parseFloat(m[5]),
      rz: parseFloat(m[6]),
      type: "movec_end",
    });
  }

  while ((m = markerReg.exec(text)) !== null) {
    const pt = {
      x: parseFloat(m[1]),
      y: parseFloat(m[2]),
      z: parseFloat(m[3]),
      rx: parseFloat(m[4]),
      ry: parseFloat(m[5]),
      rz: parseFloat(m[6]),
      type: "marker",
    };
    if (pt.x === 0 && pt.y === 0 && pt.z === 0 && pt.rx === 0 && pt.ry === 0 && pt.rz === 0)
      continue;
    data.markers.push(pt);
  }

  // 按道分组
  let beadId = 0;
  let prevType: string | null = null;
  data.main.forEach((p, i) => {
    if (p.type === "movej" && i > 0 && prevType !== "movej") beadId++;
    p.beadId = beadId;
    prevType = p.type;
  });

  // CSV 兜底
  if (data.main.length === 0 && data.markers.length === 0) {
    const csv = Papa.parse(text.trim(), { dynamicTyping: true, skipEmptyLines: true });
    (csv.data as any[]).forEach((row) => {
      if (row?.length >= 3 && typeof row[0] === "number") {
        data.main.push({
          x: row[0],
          y: row[1],
          z: row[2],
          rx: row[3] || 0,
          ry: row[4] || 0,
          rz: row[5] || 0,
          type: "csv",
        });
      }
    });
  }

  return data;
}

/** 路径预处理（Z 突变 > 5mm 断线） */
function preparePath(points: TrajPoint[]) {
  const res: { x: (number | null)[]; y: (number | null)[]; z: (number | null)[]; idx: (number | null)[]; types: (string | null)[] } = {
    x: [],
    y: [],
    z: [],
    idx: [],
    types: [],
  };
  for (let i = 0; i < points.length; i++) {
    if (i > 0 && Math.abs(points[i].z - points[i - 1].z) > 0.005) {
      res.x.push(null);
      res.y.push(null);
      res.z.push(null);
      res.idx.push(null);
      res.types.push(null);
    }
    res.x.push(points[i].x);
    res.y.push(points[i].y);
    res.z.push(points[i].z);
    res.idx.push(i);
    res.types.push(points[i].type || "?");
  }
  return res;
}

/** Euler 角 → 工具 Z 轴方向向量 */
function eulerToToolZ(rx: number, ry: number, rz: number) {
  const crx = Math.cos(rx), srx = Math.sin(rx);
  const cry = Math.cos(ry), sry = Math.sin(ry);
  const crz = Math.cos(rz), srz = Math.sin(rz);
  return {
    u: crz * sry * crx + srz * srx,
    v: srz * sry * crx - crz * srx,
    w: cry * crx,
  };
}

function computeSpan(points: TrajPoint[]) {
  if (!points.length) return 0.1;
  const xs = points.map((p) => p.x), ys = points.map((p) => p.y), zs = points.map((p) => p.z);
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

/** 更新右侧面板 */
function updateInspector(pts: any) {
  const traceName = pts.fullData?.name || "";
  const x = pts.x.toFixed(6);
  const y = pts.y.toFixed(6);
  const z = pts.z !== undefined ? pts.z.toFixed(6) : "N/A";
  const idx = pts.customdata ?? "-";
  const motionType = pts.text || "";

  selected.type = `${traceName}  [Idx: ${idx}]  ${motionType}`;
  const valStr = `[${x}, ${y}, ${z}]`;
  selected.coord = valStr;
  lastSelectedValue = valStr;

  const point = findSelectedPoint(pts);
  if (point && Number.isFinite(point.rx)) {
    selected.orientation = `[${point.rx.toFixed(6)}, ${point.ry.toFixed(6)}, ${point.rz.toFixed(6)}]`;

    selected.angleXY = `${angleWithPlane(point.rx, point.ry, point.rz, "z").toFixed(2)}°`;
    selected.angleYZ = `${angleWithPlane(point.rx, point.ry, point.rz, "x").toFixed(2)}°`;
    selected.angleXZ = `${angleWithPlane(point.rx, point.ry, point.rz, "y").toFixed(2)}°`;

    const mainIdx = findMainIndex(pts);
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

/** 绘图 */
async function drawCharts() {
  const data = globalData;
  if (!data.main.length && !data.markers.length) {
    ElMessage.warning("未能识别到有效的轨迹坐标数据（movej/movel/movep/movec），请检查格式。");
    return;
  }

  let mode = "none";
  if (showLine.value && showPoints.value) mode = "lines+markers";
  else if (showLine.value) mode = "lines";
  else if (showPoints.value) mode = "markers";

  const traces3D: any[] = [];
  const traces2D: any[] = [];

  if (data.main.length > 0) {
    const path = preparePath(data.main);
    const hoverText = path.types.map((t) => (t ? `Type: ${t}` : ""));

    traces3D.push({
      x: path.x, y: path.y, z: path.z,
      name: "完整轨迹 (all)", mode, type: "scatter3d",
      line: { color: "#3498db", width: 4 },
      marker: { size: 2, color: "#2980b9" },
      customdata: path.idx,
      text: hoverText,
      hovertemplate: "Idx: %{customdata}<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>Z: %{z:.6f}<br>%{text}<extra></extra>",
    });
    traces2D.push({
      x: path.x, y: path.y,
      name: "平面投影 (all)", mode, type: "scatter",
      line: { color: "#3498db", width: 2 },
      marker: { size: 4 },
      customdata: path.idx,
      text: hoverText,
      hovertemplate: "Idx: %{customdata}<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>%{text}<extra></extra>",
    });

    const movejPts = data.main.filter((p) => p.type === "movej");
    const movejIdx = data.main.map((_, i) => i).filter((i) => data.main[i].type === "movej");
    if (movejPts.length > 0) {
      traces3D.push({
        x: movejPts.map((p) => p.x), y: movejPts.map((p) => p.y), z: movejPts.map((p) => p.z),
        name: "movej 接近点", mode: "markers+lines", type: "scatter3d",
        line: { color: "#e67e22", width: 2, dash: "dash" },
        marker: { size: 4, color: "#e67e22", symbol: "circle-open" },
        customdata: movejIdx,
        hovertemplate: "Idx: %{customdata}<br>movej<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>Z: %{z:.6f}<extra></extra>",
      });
      traces2D.push({
        x: movejPts.map((p) => p.x), y: movejPts.map((p) => p.y),
        name: "movej 接近点", mode: "markers+lines", type: "scatter",
        line: { color: "#e67e22", width: 2, dash: "dash" },
        marker: { size: 6, color: "#e67e22", symbol: "circle-open" },
        customdata: movejIdx,
        hovertemplate: "Idx: %{customdata}<br>movej<br>X: %{x:.6f}<br>Y: %{y:.6f}<extra></extra>",
      });
    }

    const weldPts = data.main.filter((p) => ["movep", "movel", "movec", "movec_end"].includes(p.type));
    const weldIdx = data.main.map((_, i) => i).filter((i) =>
      ["movep", "movel", "movec", "movec_end"].includes(data.main[i].type)
    );
    if (weldPts.length > 0) {
      traces3D.push({
        x: weldPts.map((p) => p.x), y: weldPts.map((p) => p.y), z: weldPts.map((p) => p.z),
        name: "焊道路径 (movep/movel/movec)", mode: "markers", type: "scatter3d",
        marker: { size: 3, color: "#27ae60", symbol: "circle" },
        customdata: weldIdx,
        text: weldPts.map((p) => p.type),
        hovertemplate: "Idx: %{customdata}<br>%{text}<br>X: %{x:.6f}<br>Y: %{y:.6f}<br>Z: %{z:.6f}<extra></extra>",
      });
      traces2D.push({
        x: weldPts.map((p) => p.x), y: weldPts.map((p) => p.y),
        name: "焊接路径", mode: "markers", type: "scatter",
        marker: { size: 6, color: "#27ae60", symbol: "circle" },
        customdata: weldIdx,
        hovertemplate: "Idx: %{customdata}<br>X: %{x:.6f}<br>Y: %{y:.6f}<extra></extra>",
      });
    }
  }

  if (showMarkers.value && data.markers.length > 0) {
    const mX = data.markers.map((p) => p.x);
    const mY = data.markers.map((p) => p.y);
    const mZ = data.markers.map((p) => p.z);
    traces3D.push({
      x: mX, y: mY, z: mZ,
      name: "基准标记点 (frame)", mode: "markers", type: "scatter3d",
      marker: { color: "#e74c3c", size: 8, symbol: "diamond" },
    });
    traces2D.push({
      x: mX, y: mY,
      name: "基准标记点 (frame)", mode: "markers", type: "scatter",
      marker: { color: "#e74c3c", size: 10, symbol: "diamond" },
    });
  }

  if (showArrows.value && data.main.length > 0) {
    const span = computeSpan(data.main);
    const arrowLen = span * 0.06;
    const step = Math.max(1, Math.floor(data.main.length / 80));
    const arrowPts: { x: number; y: number; z: number; u: number; v: number; w: number }[] = [];
    for (let i = 0; i < data.main.length; i += step) {
      const p = data.main[i];
      const dir = eulerToToolZ(p.rx || 0, p.ry || 0, p.rz || 0);
      arrowPts.push({ x: p.x, y: p.y, z: p.z, u: dir.u, v: dir.v, w: dir.w });
    }

    const arrowX: (number | null)[] = [], arrowY: (number | null)[] = [], arrowZ: (number | null)[] = [];
    for (const a of arrowPts) {
      arrowX.push(a.x, a.x + a.u * arrowLen, null);
      arrowY.push(a.y, a.y + a.v * arrowLen, null);
      arrowZ.push(a.z, a.z + a.w * arrowLen, null);
    }

    traces3D.push({
      x: arrowX, y: arrowY, z: arrowZ,
      name: "姿态箭头 (Tool Z)", mode: "lines", type: "scatter3d",
      line: { color: "#e74c3c", width: 2 },
      hoverinfo: "skip",
      showlegend: true,
    });
    traces3D.push({
      x: arrowPts.map((a) => a.x + a.u * arrowLen),
      y: arrowPts.map((a) => a.y + a.v * arrowLen),
      z: arrowPts.map((a) => a.z + a.w * arrowLen),
      name: "箭头尖端", mode: "markers", type: "scatter3d",
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
  };

  const Plotly = await getPlotly();
  if (chart3D.value) Plotly.newPlot(chart3D.value, traces3D, layout3D);
  if (chart2D.value) Plotly.newPlot(chart2D.value, traces2D, layout2D);

  [chart3D, chart2D].forEach((chart) => {
    (chart.value as PlotlyEl | undefined)?.on?.("plotly_click", (ev: any) => {
      if (ev.points?.length > 0) updateInspector(ev.points[0]);
    });
  });

  // 状态栏
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

  statusMsg.value = `✅ 点位: ${data.main.length} (${parts.join(", ")})${
    data.markers.length ? ` | 标记: ${data.markers.length}` : ""
  }`;
}

function processScript() {
  globalData = parseTrajectoryData(scriptInput.value);
  drawCharts();
}

function processFile() {
  const file = fileInputRef.value?.files?.[0];
  if (!file) {
    ElMessage.warning("请先选择文件");
    return;
  }
  const reader = new FileReader();
  reader.onload = (e) => {
    globalData = parseTrajectoryData(e.target?.result as string);
    drawCharts();
  };
  reader.readAsText(file);
}

function copyData() {
  if (!lastSelectedValue) return;
  navigator.clipboard.writeText(lastSelectedValue).then(() => {
    ElMessage.success("复制成功");
  });
}

onBeforeUnmount(() => {
  const Plotly = (window as Window & { Plotly?: PlotlyApi }).Plotly;
  if (Plotly && chart3D.value) Plotly.purge(chart3D.value);
  if (Plotly && chart2D.value) Plotly.purge(chart2D.value);
});
</script>

<style lang="scss" scoped>
.trace-container {
  padding: 16px;
}

.file-input {
  display: block;
  width: 100%;
  margin-bottom: 8px;
  font-size: 12px;
}

.hint {
  font-size: 12px;
  color: #7f8c8d;
  line-height: 1.6;
}

.controls {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 12px;
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
  padding: 8px 12px;
  font-size: 12px;
  color: #155724;
  background: #d4edda;
  border-radius: 4px;
}

.chart-box {
  height: 480px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.inspector-panel {
  position: sticky;
  top: 80px;
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
}
</style>
