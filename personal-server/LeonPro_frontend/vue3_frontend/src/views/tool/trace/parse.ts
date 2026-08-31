import Papa from "papaparse";

export type PluginKind = "auto" | "welding-tools" | "full-function";
export type ResolvedPlugin = Exclude<PluginKind, "auto">;

export interface TrajPoint {
  x: number;
  y: number;
  z: number;
  rx: number;
  ry: number;
  rz: number;
  type: string;
  name?: string;
  beadId?: number;
}

export interface TrajData {
  main: TrajPoint[];
  markers: TrajPoint[];
}

export interface TaskTreeNode {
  id: string;
  label: string;
  typeName: string;
  tag: string;
  kind: "ref" | "move" | "weld" | "node";
  pose?: TrajPoint;
  refs: TrajPoint[];
  children: TaskTreeNode[];
}

export interface ParseResult {
  plugin: ResolvedPlugin;
  tree: TaskTreeNode[];
  traj: TrajData;
  taskName: string;
}

const STRUCTURAL_SKIP = new Set([
  "dataModel",
  "Primitive",
  "Pose",
  "Position",
  "Rotation",
  "Length",
  "Angle",
  "JointPositions",
  "JointPosition",
  "children",
  "siUnit",
  "valueInSi",
  "frameReference",
  "jointSpeed",
  "jointAcceleration",
  "cartesianSpeed",
  "cartesianAcceleration",
  "nextMotionTime",
  "transitionRadius",
  "internalPosition",
  "fromPosition",
  "jointPositions",
  "toolPose",
  "flangePose",
]);

const PRIMARY_POSE_KEYS = [
  "Pose_move_pose_key",
  "Pose_linear_move_pose_key",
  "Pose_circular_pass_move_pose_key",
  "Pose_circular_end_move_pose_key",
];

let nodeSeq = 0;

function nextId() {
  nodeSeq += 1;
  return `n-${nodeSeq}`;
}

function isFinitePose(p: TrajPoint) {
  return [p.x, p.y, p.z, p.rx, p.ry, p.rz].every(Number.isFinite);
}

export function isZeroPose(p: TrajPoint, eps = 1e-9) {
  return Math.abs(p.x) < eps && Math.abs(p.y) < eps && Math.abs(p.z) < eps;
}

type Vec3 = { x: number; y: number; z: number };

function vSub(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
function vAdd(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z };
}
function vScale(a: Vec3, s: number): Vec3 {
  return { x: a.x * s, y: a.y * s, z: a.z * s };
}
function vDot(a: Vec3, b: Vec3) {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}
function vCross(a: Vec3, b: Vec3): Vec3 {
  return { x: a.y * b.z - a.z * b.y, y: a.z * b.x - a.x * b.z, z: a.x * b.y - a.y * b.x };
}
function vNorm(a: Vec3) {
  return Math.hypot(a.x, a.y, a.z);
}
function vNormalize(a: Vec3): Vec3 {
  const n = vNorm(a) || 1;
  return vScale(a, 1 / n);
}

function wrap2pi(a: number) {
  let x = a;
  const twoPi = Math.PI * 2;
  while (x < 0) x += twoPi;
  while (x >= twoPi) x -= twoPi;
  return x;
}

/** movec：起点 A（圆弧前一点）+ 经过点 B + 结束点 C，沿过 B 的劣/优弧插值 */
export function interpolateCircleArc(a: Vec3, b: Vec3, c: Vec3, steps = 40): Vec3[] {
  const u = vSub(b, a);
  const v = vSub(c, a);
  const n = vCross(u, v);
  const n2 = vDot(n, n);
  if (n2 < 1e-18) return [a, b, c];

  const u2 = vDot(u, u);
  const v2 = vDot(v, v);
  const offset = vScale(vSub(vScale(vCross(v, n), u2), vScale(vCross(u, n), v2)), 1 / (2 * n2));
  const origin = vAdd(a, offset);
  const radius = vNorm(vSub(a, origin));
  if (radius < 1e-9) return [a, b, c];

  const e1 = vNormalize(vSub(a, origin));
  const e2 = vNormalize(vCross(n, e1));
  const angleOf = (p: Vec3) => {
    const d = vSub(p, origin);
    return Math.atan2(vDot(d, e2), vDot(d, e1));
  };
  const angB = wrap2pi(angleOf(b));
  const angC = wrap2pi(angleOf(c));
  let end = angC;
  if (!(angB <= angC + 1e-8)) end = angC - Math.PI * 2;
  if (Math.abs(end) < 1e-6) end = angB > Math.PI ? -Math.PI * 2 : Math.PI * 2;

  const count = Math.max(24, Math.min(80, Math.round((Math.abs(end) / Math.PI) * 48) || steps));
  const pts: Vec3[] = [];
  for (let i = 0; i <= count; i++) {
    const th = (end * i) / count;
    pts.push(vAdd(origin, vAdd(vScale(e1, radius * Math.cos(th)), vScale(e2, radius * Math.sin(th)))));
  }
  return pts;
}

export interface DisplayPath {
  x: (number | null)[];
  y: (number | null)[];
  z: (number | null)[];
  idx: (number | null)[];
  types: (string | null)[];
  arcX: (number | null)[];
  arcY: (number | null)[];
  arcZ: (number | null)[];
}

/** 将 movec 段展开为圆弧采样；起点为该圆弧前一个运动点 */
export function buildDisplayPath(points: TrajPoint[], zBreak = 0.005): DisplayPath {
  const res: DisplayPath = { x: [], y: [], z: [], idx: [], types: [], arcX: [], arcY: [], arcZ: [] };
  const push = (x: number | null, y: number | null, z: number | null, idx: number | null, type: string | null) => {
    res.x.push(x);
    res.y.push(y);
    res.z.push(z);
    res.idx.push(idx);
    res.types.push(type);
  };
  const pushArc = (x: number | null, y: number | null, z: number | null) => {
    res.arcX.push(x);
    res.arcY.push(y);
    res.arcZ.push(z);
  };

  for (let i = 0; i < points.length; i++) {
    const prev = i > 0 ? points[i - 1] : null;
    const isVia = points[i].type === "movec";
    const hasEnd = i + 1 < points.length && points[i + 1].type === "movec_end";
    if (prev && isVia && hasEnd) {
      const arc = interpolateCircleArc(prev, points[i], points[i + 1]);
      arc.forEach((p) => pushArc(p.x, p.y, p.z));
      pushArc(null, null, null);
      push(null, null, null, null, null);
      push(points[i + 1].x, points[i + 1].y, points[i + 1].z, i + 1, "movec_end");
      i += 1;
      continue;
    }
    if (i > 0 && Math.abs(points[i].z - points[i - 1].z) > zBreak) {
      push(null, null, null, null, null);
    }
    push(points[i].x, points[i].y, points[i].z, i, points[i].type || "?");
  }
  return res;
}

/** movej 虚线只连接程序中相邻的接近段，不把各焊道的接近点串成一条 */
export function buildMovejPath(points: TrajPoint[]): Pick<DisplayPath, "x" | "y" | "z" | "idx"> {
  const x: (number | null)[] = [];
  const y: (number | null)[] = [];
  const z: (number | null)[] = [];
  const idx: (number | null)[] = [];
  const pushPt = (i: number) => {
    x.push(points[i].x);
    y.push(points[i].y);
    z.push(points[i].z);
    idx.push(i);
  };
  const gap = () => {
    if (x.length && x[x.length - 1] !== null) {
      x.push(null);
      y.push(null);
      z.push(null);
      idx.push(null);
    }
  };

  for (let i = 0; i < points.length; i++) {
    if (points[i].type !== "movej") continue;
    if (i === 0 || points[i - 1].type !== "movej") gap();
    pushPt(i);
    if (i + 1 < points.length && points[i + 1].type !== "movej") {
      pushPt(i + 1);
      gap();
    }
  }
  return { x, y, z, idx };
}

function parseNums(raw: string): number[] | null {
  const parts = raw.split(",").map((s) => parseFloat(s.trim()));
  if (parts.length < 6 || parts.slice(0, 6).some((n) => !Number.isFinite(n))) return null;
  return parts.slice(0, 6);
}

function toPoint(nums: number[], type: string, name?: string): TrajPoint {
  return { x: nums[0], y: nums[1], z: nums[2], rx: nums[3], ry: nums[4], rz: nums[5], type, name };
}

function siNumber(el: Element | null): number | null {
  if (!el) return null;
  const si = el.querySelector("valueInSi")?.textContent?.trim();
  const val = el.getAttribute("value");
  const n = parseFloat(si || val || "");
  return Number.isFinite(n) ? n : null;
}

function parsePoseElement(poseEl: Element, type: string, name?: string): TrajPoint | null {
  const x = siNumber(poseEl.querySelector('Length[key="X"]'));
  const y = siNumber(poseEl.querySelector('Length[key="Y"]'));
  const z = siNumber(poseEl.querySelector('Length[key="Z"]'));
  const rx = siNumber(poseEl.querySelector('Angle[key="RX"]'));
  const ry = siNumber(poseEl.querySelector('Angle[key="RY"]'));
  const rz = siNumber(poseEl.querySelector('Angle[key="RZ"]'));
  if ([x, y, z, rx, ry, rz].some((n) => n == null)) return null;
  const p = toPoint([x!, y!, z!, rx!, ry!, rz!], type, name);
  return isFinitePose(p) ? p : null;
}

function parseToolPose(el: Element, type: string, name?: string): TrajPoint | null {
  const x = parseFloat(el.getAttribute("X") || "");
  const y = parseFloat(el.getAttribute("Y") || "");
  const z = parseFloat(el.getAttribute("Z") || "");
  const rx = parseFloat(el.getAttribute("RX") || "");
  const ry = parseFloat(el.getAttribute("RY") || "");
  const rz = parseFloat(el.getAttribute("RZ") || "");
  if (![x, y, z, rx, ry, rz].every(Number.isFinite)) return null;
  const scale = Math.max(Math.abs(x), Math.abs(y), Math.abs(z)) > 2 ? 0.001 : 1;
  return toPoint([x * scale, y * scale, z * scale, rx, ry, rz], type, name);
}

function isRefLabel(text: string) {
  return /参考/.test(text) || /Reference/i.test(text);
}

function isRefPoseKey(key: string) {
  return /ref/i.test(key) || /multi_pass/i.test(key) || /tracking/i.test(key);
}

function nodeKind(typeName: string, tag: string, label: string): TaskTreeNode["kind"] {
  const text = `${typeName} ${label}`;
  if (isRefLabel(text)) return "ref";
  if (/Move/i.test(tag) || /路点|接近|离开|开始点|经过点|结束点|过渡点|圆弧/.test(text)) return "move";
  if (/焊/.test(text)) return "weld";
  return "node";
}

function pickPrimaryPose(
  label: string,
  typeName: string,
  poses: { key: string; pose: TrajPoint }[]
): TrajPoint | undefined {
  const preferred: string[] = [];
  if (/经过/.test(label)) preferred.push("Pose_circular_pass_move_pose_key");
  if (/结束/.test(label)) preferred.push("Pose_circular_end_move_pose_key");
  if (/直线/.test(label) || /直线/.test(typeName)) preferred.push("Pose_linear_move_pose_key");
  preferred.push(...PRIMARY_POSE_KEYS);
  for (const key of preferred) {
    const hit = poses.find((item) => item.key === key);
    if (hit) return hit.pose;
  }
  return (
    poses.find((item) => !isRefPoseKey(item.key))?.pose ||
    (isRefLabel(label) || isRefLabel(typeName) ? poses[0]?.pose : undefined)
  );
}

function extractDataModelPoses(el: Element, nodeName: string) {
  const dm = el.querySelector(":scope > dataModel");
  const listed: { key: string; pose: TrajPoint }[] = [];
  if (dm) {
    dm.querySelectorAll(":scope > Pose").forEach((poseEl) => {
      const key = poseEl.getAttribute("key") || "";
      const pose = parsePoseElement(poseEl, key || "xml", nodeName);
      if (pose && !isZeroPose(pose)) listed.push({ key, pose });
    });
  }
  const toolPoseEl = el.querySelector(":scope > internalPosition > toolPose");
  if (toolPoseEl && listed.length === 0) {
    const pose = parseToolPose(toolPoseEl, "waypoint", nodeName);
    if (pose && !isZeroPose(pose)) listed.push({ key: "toolPose", pose });
  }
  return listed;
}

function structuralChildren(el: Element): Element[] {
  const out: Element[] = [];
  Array.from(el.children).forEach((child) => {
    if (child.tagName === "children") {
      out.push(...structuralChildren(child));
      return;
    }
    if (STRUCTURAL_SKIP.has(child.tagName)) return;
    out.push(child);
  });
  return out;
}

function walkXmlNode(el: Element): TaskTreeNode {
  const typeName = el.getAttribute("typeName") || el.tagName;
  const name = el.getAttribute("name") || "";
  const label = name || typeName;
  const poses = extractDataModelPoses(el, label);
  const refs = poses
    .filter((item) => isRefPoseKey(item.key) || isRefLabel(label) || isRefLabel(typeName))
    .map((item) => ({
      ...item.pose,
      type: "marker",
      name: item.pose.name || label,
    }));

  const primary = pickPrimaryPose(label, typeName, poses);

  const node: TaskTreeNode = {
    id: nextId(),
    label,
    typeName,
    tag: el.tagName,
    kind: nodeKind(typeName, el.tagName, label),
    pose: primary,
    refs,
    children: structuralChildren(el).map(walkXmlNode),
  };
  return node;
}

function parseXmlTree(xmlText: string): { tree: TaskTreeNode[]; taskName: string } {
  if (!xmlText.trim()) return { tree: [], taskName: "" };
  const doc = new DOMParser().parseFromString(xmlText, "text/xml");
  if (doc.querySelector("parsererror")) return { tree: [], taskName: "" };
  const root = doc.documentElement;
  if (!root) return { tree: [], taskName: "" };
  nodeSeq = 0;
  const tree = [walkXmlNode(root)];
  const taskName = root.getAttribute("name") || tree[0]?.label || "";
  return { tree, taskName };
}

function parseIndentTree(text: string): TaskTreeNode[] {
  const lines = text.split(/\r?\n/).map((line) => line.replace(/\t/g, "    "));
  const root: TaskTreeNode = {
    id: "root",
    label: "任务树",
    typeName: "root",
    tag: "root",
    kind: "node",
    refs: [],
    children: [],
  };
  const stack: { indent: number; node: TaskTreeNode }[] = [{ indent: -1, node: root }];
  nodeSeq = 0;

  for (const raw of lines) {
    if (!raw.trim()) continue;
    const indent = raw.length - raw.trimStart().length;
    const label = raw.trim().replace(/^当前任务：/, "");
    const node: TaskTreeNode = {
      id: nextId(),
      label,
      typeName: label,
      tag: "txt",
      kind: nodeKind(label, "txt", label),
      refs: [],
      children: [],
    };
    while (stack.length && stack[stack.length - 1].indent >= indent) stack.pop();
    stack[stack.length - 1].node.children.push(node);
    stack.push({ indent, node });
  }
  return root.children;
}

function collectTreeMarkers(nodes: TaskTreeNode[], out: TrajPoint[] = []) {
  for (const node of nodes) {
    if (node.kind === "ref" && node.pose && !isZeroPose(node.pose)) {
      out.push({ ...node.pose, type: "marker", name: node.label });
    }
    node.refs.forEach((ref) => {
      if (!isZeroPose(ref)) out.push({ ...ref, type: "marker", name: ref.name || node.label });
    });
    collectTreeMarkers(node.children, out);
  }
  return out;
}

function collectTreePath(nodes: TaskTreeNode[], out: TrajPoint[] = []) {
  for (const node of nodes) {
    if (node.kind !== "ref" && node.pose && !isZeroPose(node.pose)) {
      out.push({ ...node.pose, type: node.typeName || "xml", name: node.label });
    }
    collectTreePath(node.children, out);
  }
  return out;
}

export function collectTreePoses(nodes: TaskTreeNode[]): TrajPoint[] {
  return collectTreePath(nodes);
}

function extractMainTask(script: string) {
  const marker = script.lastIndexOf("# Main Task Script.");
  if (marker >= 0) return script.slice(marker);
  const loop = script.lastIndexOf("while (True):");
  if (loop >= 0) return script.slice(loop);
  return script;
}

function extractCalls(text: string, cmd: string): string[] {
  const calls: string[] = [];
  const needle = `${cmd}(`;
  let from = 0;
  while (from < text.length) {
    const i = text.indexOf(needle, from);
    if (i < 0) break;
    if (i > 0 && /\w/.test(text[i - 1])) {
      from = i + 1;
      continue;
    }
    let depth = 0;
    let end = -1;
    for (let j = i + cmd.length; j < text.length; j++) {
      const ch = text[j];
      if (ch === "(") depth += 1;
      else if (ch === ")") {
        depth -= 1;
        if (depth === 0) {
          end = j;
          break;
        }
      }
    }
    if (end < 0) break;
    calls.push(text.slice(i, end + 1));
    from = end + 1;
  }
  return calls;
}

function collectAssignments(text: string) {
  const vars = new Map<string, number[]>();
  const re = /([A-Za-z_]\w*)\s*=\s*\[\s*([^[\]]+)\]/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    const nums = parseNums(m[2]);
    if (nums) vars.set(m[1], nums);
  }
  return vars;
}

function tuplesFromCall(call: string, plugin: ResolvedPlugin, vars: Map<string, number[]>): number[][] {
  const tuples: number[][] = [];
  if (plugin === "full-function") {
    const offsetRe = /full_apply_touch_offset\s*\(\s*\[([^\]]+)\]/g;
    let m: RegExpExecArray | null;
    while ((m = offsetRe.exec(call)) !== null) {
      const nums = parseNums(m[1]);
      if (nums) tuples.push(nums);
    }
  }
  if (tuples.length === 0) {
    const bareRe = /\[([^[\]]+)\]/g;
    let m: RegExpExecArray | null;
    while ((m = bareRe.exec(call)) !== null) {
      const prefix = call.slice(Math.max(0, m.index - 8), m.index);
      if (/qnear\s*=$/i.test(prefix.trimEnd()) || /qnear\s*=/.test(prefix)) continue;
      const nums = parseNums(m[1]);
      if (nums) tuples.push(nums);
    }
  }
  if (tuples.length === 0) {
    const idm = /get_inverse_kin\s*\(\s*([A-Za-z_]\w*)/.exec(call);
    if (idm) {
      const nums = vars.get(idm[1]);
      if (nums) tuples.push(nums);
    }
  }
  return tuples;
}

function lineNamesBefore(text: string) {
  const map: { index: number; name: string }[] = [];
  const re = /\$ LINE:\s*\([^,]+,\s*"([^"]+)"/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    map.push({ index: m.index, name: m[1] });
  }
  return map;
}

function nameAt(index: number, names: { index: number; name: string }[]) {
  let label = "";
  for (const item of names) {
    if (item.index > index) break;
    label = item.name;
  }
  return label;
}

function parseScriptMotions(script: string, plugin: ResolvedPlugin): TrajData {
  const data: TrajData = { main: [], markers: [] };
  if (!script.trim()) return data;

  const vars = collectAssignments(script);
  const body = extractMainTask(script);
  const names = lineNamesBefore(body);

  const markerRe =
    plugin === "welding-tools"
      ? /trackingFrame\s*=\s*\[([^\]]+)\]/g
      : /full_tracking_frame\s*=\s*\[([^\]]+)\]/g;
  const scriptMarkers: TrajPoint[] = [];
  let mm: RegExpExecArray | null;
  while ((mm = markerRe.exec(body)) !== null) {
    const nums = parseNums(mm[1]);
    if (!nums) continue;
    const pt = toPoint(nums, "marker", "跟踪坐标系");
    if (!isZeroPose(pt)) scriptMarkers.push(pt);
  }
  // WeldingTools 摆焊会给每个插补点写 trackingFrame，数量过大时不当作参考点
  if (scriptMarkers.length && (plugin === "full-function" || scriptMarkers.length <= 24)) {
    data.markers.push(...scriptMarkers);
  }

  const cmds = ["movej", "movel", "movep", "movec"] as const;
  const found: { index: number; cmd: string; call: string }[] = [];
  for (const cmd of cmds) {
    const needle = `${cmd}(`;
    let from = 0;
    while (from < body.length) {
      const i = body.indexOf(needle, from);
      if (i < 0) break;
      if (i > 0 && /\w/.test(body[i - 1])) {
        from = i + 1;
        continue;
      }
      const calls = extractCalls(body.slice(i), cmd);
      if (!calls.length) break;
      found.push({ index: i, cmd, call: calls[0] });
      from = i + calls[0].length;
    }
  }
  found.sort((a, b) => a.index - b.index);

  for (const item of found) {
    const tuples = tuplesFromCall(item.call, plugin, vars);
    const label = nameAt(item.index, names);
    tuples.forEach((nums, idx) => {
      const type = item.cmd === "movec" && idx > 0 ? "movec_end" : item.cmd;
      data.main.push(toPoint(nums, type, label));
    });
  }

  let beadId = 0;
  let prevType: string | null = null;
  data.main.forEach((p, i) => {
    if (p.type === "movej" && i > 0 && prevType !== "movej") beadId += 1;
    p.beadId = beadId;
    prevType = p.type;
  });

  return data;
}

function parseCsvFallback(text: string): TrajData {
  const data: TrajData = { main: [], markers: [] };
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
  return data;
}

export function detectPlugin(xmlText: string, scriptText: string): ResolvedPlugin {
  // 两套插件脚本 preamble 可能互相包含辅助函数，优先看 XML 的 extension
  if (/WeldingTools/.test(xmlText) && !/FullFunctionWelding/.test(xmlText)) return "welding-tools";
  if (/FullFunctionWelding/.test(xmlText)) return "full-function";
  const body = extractMainTask(scriptText);
  if (/full_apply_touch_offset\s*\(\s*\[/.test(body)) return "full-function";
  if (/#\s*Source:.*WeldingTools|ELITECO: Plugin WeldingTools/.test(scriptText)) return "welding-tools";
  if (/#\s*Source:\s*FullFunctionWelding/.test(body)) return "full-function";
  return "welding-tools";
}

function uniqueMarkers(points: TrajPoint[]) {
  const seen = new Set<string>();
  const out: TrajPoint[] = [];
  for (const p of points) {
    const key = [p.x, p.y, p.z].map((n) => n.toFixed(5)).join(",");
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(p);
  }
  return out;
}

export function parseProject(input: {
  xmlText: string;
  scriptText: string;
  treeText: string;
  plugin: PluginKind;
}): ParseResult {
  const plugin = input.plugin === "auto" ? detectPlugin(input.xmlText, input.scriptText) : input.plugin;
  const xmlParsed = parseXmlTree(input.xmlText);
  const tree = xmlParsed.tree.length ? xmlParsed.tree : parseIndentTree(input.treeText);
  const scriptTraj = parseScriptMotions(input.scriptText, plugin);
  const xmlPath = collectTreePath(tree);
  const xmlMarkers = collectTreeMarkers(tree);

  const main = scriptTraj.main.length ? scriptTraj.main : xmlPath;
  const markers = uniqueMarkers([...xmlMarkers, ...scriptTraj.markers]);

  let traj: TrajData = { main, markers };
  if (!traj.main.length && !traj.markers.length && input.scriptText.trim()) {
    traj = parseCsvFallback(input.scriptText);
  }

  return {
    plugin,
    tree,
    traj,
    taskName: xmlParsed.taskName,
  };
}

export function classifyProjectFile(name: string, content: string): "xml" | "script" | "tree" | "unknown" {
  const lower = name.toLowerCase();
  if (lower.endsWith(".task.script") || lower.endsWith(".script")) return "script";
  if (lower.endsWith(".task") || content.trimStart().startsWith("<EliTask") || content.trimStart().startsWith("<?xml")) {
    return "xml";
  }
  if (lower.endsWith(".txt") && /当前任务/.test(content)) return "tree";
  if (/\b(movej|movel|movep|movec)\s*\(/.test(content)) return "script";
  if (/当前任务/.test(content)) return "tree";
  return "unknown";
}
