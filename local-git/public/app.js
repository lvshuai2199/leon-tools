"use strict";

const app = document.querySelector("#app");
const modal = document.querySelector("#modal");
const toast = document.querySelector("#toast");

const state = {
  home: null,
  repo: localStorage.getItem("local-git-repo") || "",
  ws: null,
  log: null,
  diff: null,
  tab: "log",
  file: "",
  logFile: "",
  sha: "",
  target: "",
  picks: {},
  fileSide: "",
  message: "",
  busy: "",
  selectedBranch: "",
  graphRef: "",
  branchFilter: "",
  stashId: "",
  stashDiff: null,
  stashError: "",
  preferStash: false,
};

const LETTER = { M: "修改", A: "新增", D: "删除", R: "重命名", C: "复制", U: "冲突", "?": "未跟踪" };
const LANE_COLORS = ["#0969da", "#1a7f37", "#9a6700", "#8250df", "#cf222e", "#0e7490", "#bf3989", "#6e7781"];

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
}

function isConflict(change) {
  return Boolean(change) && (change.index === "U" || change.worktree === "U");
}

function isStaged(change) {
  return Boolean(change) && !isConflict(change) && "MADRC".includes(change.index || "");
}

function isUnstaged(change) {
  if (!change) return false;
  if (isConflict(change)) return true;
  return change.worktree === "?" || "MADRC".includes(change.worktree || "");
}

function letterText(letter) {
  return LETTER[letter] || letter || "";
}

function rowStatus(change, side) {
  if (isConflict(change)) return "冲突";
  return letterText(side === "staged" ? change.index : change.worktree);
}

function pathBits(filePath) {
  const text = String(filePath || "");
  const slash = text.lastIndexOf("/");
  if (slash === -1) return { name: text, parent: "" };
  return { name: text.slice(slash + 1), parent: text.slice(0, slash) };
}

function stagedCount() {
  return (state.ws?.changes || []).filter(isStaged).length;
}

function pickKey(file, index) {
  return `${index}\n${file}`;
}

function hintTone(hints) {
  const text = (hints || []).join("\n");
  return /冲突|二进制|未提交|过大|对不上|空白|合并提交|已经包含/.test(text) ? "warn" : "info";
}

function showToast(message) {
  toast.hidden = false;
  toast.textContent = message;
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => {
    toast.hidden = true;
  }, 2400);
}

function confirmBox(title, content, okText) {
  return new Promise((resolve) => {
    modal.hidden = false;
    modal.innerHTML = `<div class="modal__card"><h2>${esc(title)}</h2><p>${esc(content || "")}</p><div class="modal__actions"><button type="button" data-no>取消</button><button type="button" class="primary" data-yes>${esc(okText || "确定")}</button></div></div>`;
    modal.onclick = (event) => {
      const yes = event.target.closest("[data-yes]");
      const no = event.target.closest("[data-no]");
      if (!yes && !no && event.target !== modal) return;
      modal.hidden = true;
      modal.onclick = null;
      resolve(Boolean(yes));
    };
  });
}

function formBox(title, html) {
  return new Promise((resolve) => {
    modal.hidden = false;
    modal.innerHTML = `<form class="modal__card"><h2>${esc(title)}</h2>${html}<div class="modal__actions"><button type="button" data-no>取消</button><button class="primary" type="submit">保存</button></div></form>`;
    modal.querySelector("[data-no]").onclick = () => {
      modal.hidden = true;
      resolve(null);
    };
    modal.querySelector("form").onsubmit = (event) => {
      event.preventDefault();
      const data = Object.fromEntries(new FormData(event.target).entries());
      modal.hidden = true;
      resolve(data);
    };
  });
}

async function api(url, options) {
  const res = await fetch(url, {
    method: options?.body ? "POST" : "GET",
    headers: options?.body ? { "Content-Type": "application/json" } : undefined,
    body: options?.body ? JSON.stringify(options.body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.error) throw new Error(data.error || "请求失败");
  return data;
}

async function loadHome() {
  state.home = await api("/api/home");
  if (state.repo && !(state.home.repos || []).some((repo) => repo.id === state.repo)) state.repo = "";
  if (!state.repo && state.home.repos?.length) state.repo = state.home.repos[0].id;
}

let graphSeq = 0;

function logUrl(repo, ref) {
  const base = `/api/log?repo=${encodeURIComponent(repo)}`;
  return ref ? `${base}&ref=${encodeURIComponent(ref)}` : base;
}

async function loadRepo() {
  graphSeq += 1;
  state.ws = null;
  state.log = null;
  state.diff = null;
  if (!state.repo) return;
  localStorage.setItem("local-git-repo", state.repo);
  state.ws = await api(`/api/workspace?repo=${encodeURIComponent(state.repo)}`);
  if (state.graphRef && !findLeaf(state.ws.branchTree, state.graphRef)) state.graphRef = "";
  state.log = await api(logUrl(state.repo, state.graphRef));
  const locals = (state.ws.branches || []).filter((branch) => !branch.remote);
  const other = locals.find((branch) => !branch.current);
  if (!state.target || !locals.some((branch) => branch.name === state.target)) state.target = other?.name || state.ws.branch || "";
  const current = (state.ws.changes || []).find((change) => change.path === state.file);
  if (!current) {
    state.file = "";
    state.fileSide = "";
  } else if (state.fileSide === "staged" && !isStaged(current)) {
    state.fileSide = isUnstaged(current) ? "unstaged" : "";
    if (!state.fileSide) state.file = "";
  } else if (state.fileSide === "unstaged" && !isUnstaged(current)) {
    state.fileSide = isStaged(current) ? "staged" : "";
    if (!state.fileSide) state.file = "";
  } else if (!state.fileSide) {
    state.fileSide = isUnstaged(current) ? "unstaged" : "staged";
  }
  if (state.stashId && !(state.ws.stashes || []).some((stash) => stash.id === state.stashId)) {
    state.stashId = "";
    state.stashDiff = null;
    state.stashError = "";
    state.preferStash = false;
  }
  if (state.selectedBranch && !findLeaf(state.ws.branchTree, state.selectedBranch)) state.selectedBranch = "";
  await loadDiff();
}

let diffSeq = 0;

async function loadDiff() {
  if (!state.repo) return;
  const seq = ++diffSeq;
  if (state.tab === "log" && state.sha) {
    const target = encodeURIComponent(state.target || "");
    const file = state.logFile ? `&file=${encodeURIComponent(state.logFile)}` : "";
    const data = await api(`/api/diff?repo=${encodeURIComponent(state.repo)}&rev=${encodeURIComponent(state.sha)}&target=${target}${file}`);
    if (seq !== diffSeq) return;
    state.diff = data;
    if (state.logFile && !(state.diff.files || []).some((file) => file.path === state.logFile)) state.logFile = "";
    if (!state.logFile && (state.diff.files || []).length) state.logFile = state.diff.files[0].path;
    return;
  }
  if (state.tab === "changes") {
    if (!state.file) {
      if (seq !== diffSeq) return;
      state.diff = null;
      return;
    }
    const scope = state.fileSide === "staged" ? "staged" : "unstaged";
    const data = await api(`/api/diff?repo=${encodeURIComponent(state.repo)}&scope=${scope}&file=${encodeURIComponent(state.file)}`);
    if (seq !== diffSeq) return;
    state.diff = data;
  }
}

async function loadStash(id) {
  state.stashId = id;
  state.selectedBranch = "";
  state.preferStash = true;
  state.stashDiff = null;
  state.stashError = "";
  render();
  try {
    const page = await api(`/api/stash/show?repo=${encodeURIComponent(state.repo)}&id=${encodeURIComponent(id)}`);
    if (state.stashId !== id) return;
    state.stashDiff = page;
    state.stashError = "";
    state.logFile = (page.files || [])[0]?.path || "";
  } catch (error) {
    if (state.stashId !== id) return;
    state.stashDiff = null;
    state.stashError = error.message || "打不开这条贮藏的差异";
    showToast(state.stashError);
  }
  render();
}

async function loadGraph() {
  if (!state.repo) return;
  const seq = ++graphSeq;
  const ref = state.graphRef;
  const repo = state.repo;
  try {
    const data = await api(logUrl(repo, ref));
    if (seq !== graphSeq || state.graphRef !== ref || state.repo !== repo) return;
    state.log = data;
    render();
  } catch (error) {
    if (seq !== graphSeq || state.graphRef !== ref || state.repo !== repo) return;
    showToast(error.message || "失败");
  }
}

function syncRepoBranch() {
  const repos = state.home && state.home.repos;
  if (!repos || !state.repo || !state.ws) return;
  const item = repos.find((repo) => repo.id === state.repo);
  if (!item) return;
  item.branch = state.ws.detached ? "" : state.ws.branch || "";
}

async function refresh(notice, options) {
  const scan = !options || options.home !== false;
  if (scan) await loadHome();
  if (state.repo) await loadRepo();
  syncRepoBranch();
  render();
  if (notice) showToast(notice);
}

async function run(label, fn) {
  if (state.busy) return;
  state.busy = label;
  render();
  try {
    const result = await fn();
    if (result?.repoId) state.repo = result.repoId;
    const scan = label === "repo" || label === "clone" || label === "settings";
    await refresh(result?.notice, { home: scan });
  } catch (error) {
    showToast(error.message || "失败");
    render();
  } finally {
    state.busy = "";
    render();
  }
}

function render() {
  hideMenu();
  const home = state.home;
  if (!home) {
    app.innerHTML = `<p class="empty">正在读取这台电脑上的仓库…</p>`;
    return;
  }
  if (!home.cloneRoot || !home.ready) {
    app.innerHTML = `<section class="pane"><p>选择已有仓库所在的目录，例如 <code>D:\\GitFiles</code>。里面的 Git 仓库会列出来。</p><p class="meta">${esc(home.warning || "")}</p><button type="button" id="setup" class="primary">选择目录</button></section>`;
    document.querySelector("#setup").onclick = openSettings;
    return;
  }
  const ws = state.ws;
  const options = (home.repos || [])
    .map((repo) => `<option value="${esc(repo.id)}" ${repo.id === state.repo ? "selected" : ""}>${esc(repo.name)} · ${esc(repo.branch || "无分支")}</option>`)
    .join("");
  const track = !ws ? "" : ws.upstream ? `上游 ${ws.upstream}，超前 ${ws.ahead}，落后 ${ws.behind}` : "没有上游";
  const meta = ws ? `${ws.detached ? "游离 HEAD" : ws.branch} · ${track}${ws.dirty ? " · 有未提交改动" : ""}` : "";
  const changeCount = ws ? (ws.changes || []).length : null;
  const changeTab = changeCount == null ? "变更" : `变更 (${changeCount})`;
  const screen = state.tab === "log" ? logScreen() : changeScreen();
  app.innerHTML = `
    <div class="toolbar">
      <select id="repo-select" class="repo-select" ${dis()}>${options || `<option value="">没有仓库</option>`}</select>
      <button type="button" data-act="fetch" title="只下载远程的新提交，不改你现在的分支。" ${dis()}>获取</button>
      <button type="button" data-act="pull" ${dis()}>拉取</button>
      <button type="button" data-act="push" ${dis()}>推送</button>
      ${commitButton("提交")}
      <button type="button" data-act="branch" ${dis()}>新建分支</button>
      <p class="meta toolbar-meta">${esc(meta)}</p>
    </div>
    <div class="tabs">
      <button type="button" data-tab="log" class="${state.tab === "log" ? "is-on" : ""}">历史</button>
      <button type="button" data-tab="changes" class="${state.tab === "changes" ? "is-on" : ""}">${changeTab}</button>
    </div>
    ${screen}`;
  bind();
}

function dis() {
  return state.busy ? "disabled" : "";
}

function commitButton(label, className) {
  const count = stagedCount();
  const disabled = state.busy || count === 0;
  const title = count === 0 ? "没有已暂存的文件" : "";
  const cls = className ? ` class="${className}"` : "";
  return `<button type="button"${cls} data-act="commit" ${disabled ? "disabled" : ""} title="${esc(title)}">${esc(label)}</button>`;
}

function changeScreen() {
  return `<div class="screen cols-2"><section class="pane col"><div class="list grow">${changeList()}</div>${commitBox()}</section><section class="pane diff-pane">${changesDiff()}</section></div>`;
}

function logScreen() {
  return `<div class="screen history"><section class="pane col history-branches">${treePane()}</section><section class="pane graph-pane history-graph">${renderGraph()}</section><section class="pane col history-files">${historyFiles()}</section><section class="pane diff-pane history-diff">${historyDiff()}</section></div>`;
}

function changeRow(change, side) {
  const on = state.file === change.path && state.fileSide === side ? "is-on" : "";
  const bits = pathBits(change.path);
  const parent = bits.parent ? `<span class="change-dir">${esc(bits.parent)}</span>` : "";
  const checked = side === "staged" ? "checked" : "";
  return `<div class="change-row"><input type="checkbox" data-check="${esc(change.path)}" ${checked} ${dis()} /><button type="button" class="item ${on}" data-file="${esc(change.path)}" data-side="${side}" title="${esc(change.path)}"><span class="change-path"><b>${esc(bits.name)}</b>${parent}</span><small class="change-status">${esc(rowStatus(change, side))}</small></button></div>`;
}

function changeGroups(changes, side) {
  const groups = new Map();
  for (const change of changes) {
    const slash = change.path.indexOf("/");
    const key = slash === -1 ? "" : change.path.slice(0, slash);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(change);
  }
  return [...groups.keys()]
    .sort((a, b) => a.localeCompare(b, "zh"))
    .map((key) => {
      const rows = groups.get(key).map((change) => changeRow(change, side)).join("");
      return `<div class="group"><div class="tree-label">${esc(key || "根目录")}</div>${rows}</div>`;
    })
    .join("");
}

function changeSection(title, changes, side) {
  const body = changes.length ? changeGroups(changes, side) : `<p class="empty">没有文件</p>`;
  return `<section class="change-section"><div class="tree-label change-section-title">${esc(title)} (${changes.length})</div>${body}</section>`;
}

function changeList() {
  if (!state.ws) return `<p class="empty">先选择仓库</p>`;
  const changes = state.ws.changes || [];
  return `${changeSection("未暂存", changes.filter(isUnstaged), "unstaged")}${changeSection("已暂存", changes.filter(isStaged), "staged")}`;
}

function commitBox() {
  return `<div class="commit-box"><textarea id="message" placeholder="提交说明">${esc(state.message)}</textarea>${commitButton("提交已暂存", "primary")}</div>`;
}

function changesDiff() {
  if (!state.file) return `<p class="empty">点一个文件查看差异</p>`;
  if (!state.diff) return `<p class="empty">正在读取差异…</p>`;
  const hints = state.diff.hints || [];
  const hint = hints.length ? `<div class="hint ${hintTone(hints) === "warn" ? "is-warn" : ""}">${esc(hints.join("\n"))}</div>` : "";
  const file = (state.diff.files || []).find((item) => item.path === state.file) || (state.diff.files || [])[0];
  if (!file) return `${hint}<p class="empty">没有差异</p>`;
  const current = (state.ws?.changes || []).find((change) => change.path === state.file);
  const allowHunk = state.fileSide !== "staged" && !isConflict(current);
  return hint + renderSide(file, allowHunk);
}

function fileButtons(files) {
  if (!files.length) return `<p class="empty">没有文件差异</p>`;
  return `<div class="list file-list">${files
    .map((file) => `<button type="button" class="item ${state.logFile === file.path ? "is-on" : ""}" data-log-file="${esc(file.path)}" title="${esc(file.path)}"><span class="grow"><b>${esc(file.path)}</b><small>${esc((file.hints || []).join(" · "))}</small></span></button>`)
    .join("")}</div>`;
}

function pickControls() {
  const locals = (state.ws?.branches || []).filter((branch) => !branch.remote);
  const options = locals
    .map((branch) => `<option value="${esc(branch.name)}" ${branch.name === state.target ? "selected" : ""}>${esc(branch.name)}${branch.current ? "（当前）" : ""}</option>`)
    .join("");
  return `<label>拣选到</label><select id="target">${options}</select><button type="button" data-act="pick-all" ${dis()}>整次拿到所选分支</button><button type="button" class="primary" data-act="pick-part" ${dis()}>只拿勾选的片段</button>`;
}

function historyFiles() {
  if (state.preferStash && state.stashId) {
    if (state.stashError) return `<p class="empty">${esc(state.stashError)}</p>`;
    if (!state.stashDiff) return `<p class="empty">正在读取贮藏…</p>`;
    return `<p class="meta">${esc(state.stashDiff.message || "贮藏")}</p>${fileButtons(state.stashDiff.files || [])}`;
  }
  if (!state.sha) return `<p class="empty">点上面一次提交，这里列出改动的文件</p>`;
  const hints = commitHints();
  const hint = hints.length ? `<span class="history-hint ${hintTone(hints) === "warn" ? "is-warn" : ""}" title="${esc(hints.join("\n"))}">${esc(hints.join(" · "))}</span>` : "";
  const files = state.diff ? fileButtons(state.diff.files || []) : `<p class="empty">正在读取这次提交…</p>`;
  return `<div class="history-bar">${hint}${pickControls()}</div>${files}`;
}

function commitHints() {
  const hints = [...(state.diff?.hints || [])];
  if (state.ws?.dirty && !hints.some((text) => String(text).includes("未提交"))) hints.push("工作区还有未提交改动");
  return hints;
}

function historyDiff() {
  const stashView = state.preferStash && state.stashId;
  const files = stashView ? state.stashDiff?.files || [] : state.diff?.files || [];
  const selected = files.find((file) => file.path === state.logFile);
  if (!selected) return `<p class="empty">点左边的文件查看差异</p>`;
  if (!stashView && state.sha && !selected.binary && !(selected.hunks || []).length && state.diff?.file !== selected.path) {
    return `<p class="empty">正在读取差异…</p>`;
  }
  return `<div class="diff-scroll">${renderFile(selected)}</div>`;
}

function renderSide(file, allowHunk) {
  const side = file.side || { binary: file.binary, rows: [] };
  const head = `<div class="file-head"><b>${esc(file.path)}</b><small>${esc((file.hints || []).join(" · "))}</small></div>`;
  if (side.binary || file.binary) return `<section class="hunk">${head}<p class="empty">二进制文件</p></section>`;
  const rows = (side.rows || [])
    .map((row) => {
      if (row.kind === "hunk") {
        const restore = allowHunk ? `<button type="button" data-hunk-restore="${esc(file.path)}" data-index="${row.index}" data-hint="${esc(row.header || row.hint || "")}">回滚这段</button>` : "";
        return `<div class="sbs-hunk"><span>${esc(row.header || "")} · ${esc(row.hint || "")}</span>${restore}</div>`;
      }
      const leftClass = row.kind === "del" || row.kind === "replace" ? "del" : "";
      const rightClass = row.kind === "add" || row.kind === "replace" ? "add" : "";
      return `<div class="sbs-row"><div class="sbs-cell ${leftClass}">${esc(row.left == null ? "" : row.left)}</div><div class="sbs-cell ${rightClass}">${esc(row.right == null ? "" : row.right)}</div></div>`;
    })
    .join("");
  return `<section class="file-block">${head}<div class="sbs"><div class="sbs-row sbs-head"><div>${esc(side.leftNote || "HEAD")}</div><div>${esc(side.rightNote || "工作区")}</div></div>${rows}</div></section>`;
}

function renderFile(file) {
  const picking = state.tab === "log" && !state.preferStash;
  const head = `<div class="file-head"><b>${esc(file.path)}</b><small>${esc((file.hints || []).join(" · "))}</small></div>`;
  if (file.binary) {
    const key = pickKey(file.path, -1);
    const box = picking ? `<input type="checkbox" data-hunk="${esc(key)}" ${state.picks[key] ? "checked" : ""} />` : "";
    return `<section class="hunk">${head}<header>${box}<span>整文件 · 二进制</span></header></section>`;
  }
  const hunks = (file.hunks || [])
    .map((hunk) => {
      const key = pickKey(file.path, hunk.index);
      const box = picking ? `<input type="checkbox" data-hunk="${esc(key)}" data-hint="${esc(hunk.hint)}" ${state.picks[key] ? "checked" : ""} />` : "";
      const lines = hunk.lines.map((line) => `<div class="${esc(line.kind)}">${esc(`${line.kind === "add" ? "+" : line.kind === "del" ? "-" : " "}${line.text}`)}</div>`).join("");
      return `<section class="hunk"><header>${box}<span>${esc(hunk.header)} · ${esc(hunk.hint)}</span></header><pre class="diff">${lines}</pre></section>`;
    })
    .join("");
  return head + hunks;
}

function shortWhen(iso) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function graphBranchNames() {
  const names = [];
  const seen = new Set();
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.children) walk(node.children);
      if (!node.checkout || node.detached || seen.has(node.checkout)) continue;
      seen.add(node.checkout);
      names.push(node.checkout);
    }
  };
  for (const group of state.ws?.branchTree?.groups || []) walk(group.nodes);
  return names;
}

function graphHead() {
  const watching = state.graphRef || "全部";
  const label = `正在看：${watching}`;
  const options = [`<option value="" ${state.graphRef ? "" : "selected"}>全部</option>`]
    .concat(graphBranchNames().map((name) => `<option value="${esc(name)}" ${name === state.graphRef ? "selected" : ""}>${esc(name)}</option>`))
    .join("");
  return `<div class="graph-head"><b>分支图</b><span title="${esc(label)}">${esc(label)}</span><button type="button" data-graph-all class="${state.graphRef ? "" : "is-on"}" ${dis()}>全部</button><select id="graph-ref" ${dis()}>${options}</select></div>`;
}

function renderGraph() {
  const head = graphHead();
  const commits = state.log?.commits || [];
  const rows = state.log?.graph || [];
  if (!commits.length) return `${head}<p class="empty">还没有提交</p>`;
  const focusColors = new Set();
  if (state.graphRef) {
    commits.forEach((commit, index) => {
      const hit = (commit.refs || []).some((ref) => ref && ref.name === state.graphRef && ref.kind !== "tag");
      if (hit && rows[index]) focusColors.add(rows[index].color || 0);
    });
  }
  const band = (color) => focusColors.has(color || 0);
  const rowHeight = 32;
  const colWidth = 22;
  const pad = 12;
  let lanes = 1;
  rows.forEach((row) => {
    lanes = Math.max(lanes, (row.lane || 0) + 1);
    (row.links || []).forEach((link) => {
      lanes = Math.max(lanes, (link.from || 0) + 1, (link.to || 0) + 1);
    });
  });
  const width = pad * 2 + lanes * colWidth;
  const height = commits.length * rowHeight;
  const x = (col) => pad + col * colWidth + colWidth / 2;
  const y = (index) => index * rowHeight + rowHeight / 2;
  let marks = "";
  rows.forEach((row, index) => {
    const color = LANE_COLORS[(row.color || 0) % LANE_COLORS.length];
    for (const link of row.links || []) {
      const stroke = LANE_COLORS[(link.color || 0) % LANE_COLORS.length];
      const width = band(link.color) ? 4.5 : link.kind === "bottom" ? 2 : 2.5;
      const x1 = x(link.from || 0);
      const y1 = y(index);
      if (link.kind === "bottom") {
        marks += `<line x1="${x1}" y1="${y1}" x2="${x1}" y2="${y1 + rowHeight / 2 - 2}" stroke="${stroke}" stroke-width="${width}" />`;
        continue;
      }
      const x2 = x(link.to || 0);
      const y2 = y(index + 1);
      if (link.from === link.to) marks += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round" />`;
      else {
        const mid = (y1 + y2) / 2;
        marks += `<path d="M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}" fill="none" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round" />`;
      }
    }
    const cx = x(row.lane || 0);
    const cy = y(index);
    marks += `<circle cx="${cx}" cy="${cy}" r="4.5" fill="#fff" stroke="${color}" stroke-width="${band(row.color) ? 4 : 2.5}" />`;
  });
  const lines = commits
    .map((commit) => {
      const tags = (commit.refs || [])
        .map((ref) => {
          const kind = ref && ref.kind ? ref.kind : "local";
          const label = ref && ref.name ? ref.name : ref;
          const on = state.graphRef && label === state.graphRef ? " is-on" : "";
          const click = kind === "local" || kind === "remote" ? ` data-graph-ref="${esc(label)}"` : "";
          return `<span class="tag tag-${esc(kind)}${on}"${click}>${esc(label)}</span>`;
        })
        .join("");
      const when = shortWhen(commit.date);
      const meta = `<small class="graph-meta"><span class="graph-sha">${esc(commit.shortSha)}</span><span class="graph-author">${esc(commit.author)}</span>${when ? `<span class="graph-when">${esc(when)}</span>` : ""}</small>`;
      return `<button type="button" class="graph-line ${state.sha === commit.sha ? "is-on" : ""}" data-sha="${esc(commit.sha)}" style="height:${rowHeight}px" title="${esc(commit.message || "")}"><span class="graph-msg"><span class="graph-subject"><b>${esc(commit.message || "（没有说明）")}</b> ${tags}</span>${meta}</span></button>`;
    })
    .join("");
  const note = state.log?.truncated ? `<p class="meta">只显示最近 ${commits.length} 条</p>` : "";
  return `${head}<div class="graph-scroll"><div class="graph-body" style="height:${height}px"><svg class="graph-svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">${marks}</svg><div class="graph-rows">${lines}</div></div>${note}</div>`;
}

function treePane() {
  const tree = state.ws?.branchTree;
  if (!tree) return `<p class="empty">先选择仓库</p>`;
  const groups = (tree.groups || []).map(renderGroup).join("");
  const visible = groups.trim();
  return `<input id="branch-filter" placeholder="过滤分支" value="${esc(state.branchFilter)}" /><div class="list grow">${visible || `<p class="empty">${state.branchFilter.trim() ? "没有匹配的分支" : "没有分支"}</p>`}</div>${stashBlock()}`;
}

function renderGroup(group) {
  const nodes = filterNodes(group.nodes, state.branchFilter, false);
  if (state.branchFilter.trim() && !nodes.length) return "";
  return `<div class="tree-group"><div class="tree-label">${esc(group.label)}</div>${renderNodes(nodes, 0)}</div>`;
}

function renderNodes(nodes, depth) {
  return (nodes || [])
    .map((node) => {
      if (node.type === "folder") {
        return `<div class="tree-folder ${node.remote ? "is-remote" : ""}" style="--depth:${depth}">${esc(node.name)}</div>${renderNodes(node.children, depth + 1)}`;
      }
      const on = node.checkout && state.selectedBranch === node.checkout ? "is-on" : "";
      const track = formatTrack(node);
      return `<button type="button" class="tree-leaf ${on} ${node.current ? "is-current" : ""} ${node.remote ? "is-remote" : "is-local"}" style="--depth:${depth}" data-leaf="${esc(node.checkout || "")}" title="${esc(node.checkout || node.name)}" ${node.detached ? "disabled" : ""}><span class="grow"><b>${esc(node.name)}</b>${node.current ? `<small>当前</small>` : ""}${track ? `<small>${esc(track)}</small>` : ""}</span></button>`;
    })
    .join("");
}

function filterNodes(nodes, query, parentMatched) {
  const text = String(query || "").trim().toLowerCase();
  if (!text) return nodes || [];
  const out = [];
  for (const node of nodes || []) {
    if (node.type === "folder") {
      const self = node.name.toLowerCase().includes(text) || parentMatched;
      const children = filterNodes(node.children, query, self);
      if (self || children.length) out.push({ ...node, children: self ? node.children || [] : children });
    } else {
      const hay = `${node.name || ""} ${node.checkout || ""}`.toLowerCase();
      if (parentMatched || hay.includes(text)) out.push(node);
    }
  }
  return out;
}

function formatTrack(node) {
  if (!node || node.remote || node.ahead == null || node.behind == null) return "";
  const parts = [];
  if (node.ahead) parts.push(`超前 ${node.ahead}`);
  if (node.behind) parts.push(`落后 ${node.behind}`);
  return parts.join(" ");
}

function findLeaf(tree, checkout) {
  if (!tree || !checkout) return null;
  for (const group of tree.groups || []) {
    const found = walk(group.nodes);
    if (found) return found;
  }
  return null;
  function walk(nodes) {
    for (const node of nodes || []) {
      if (node.type !== "folder" && node.checkout === checkout) return node;
      const child = walk(node.children);
      if (child) return child;
    }
    return null;
  }
}

function branchMenu(leaf) {
  if (!leaf || leaf.detached || !leaf.checkout) return [];
  if (leaf.current && !leaf.remote) {
    if (state.ws?.detached) return [];
    return [
      { label: "改名", act: "rename", name: leaf.checkout },
      { label: "合成最近几次", act: "squash" },
    ];
  }
  const items = [
    { label: "切过去", act: "checkout", name: leaf.checkout },
    { label: "合进来", act: "merge", name: leaf.checkout },
    { label: "接到后面", act: "replay", name: leaf.checkout },
  ];
  if (!leaf.remote) {
    items.push({ label: "改名", act: "rename", name: leaf.checkout });
    items.push({ label: "删除", act: "delete", name: leaf.checkout, danger: true });
  }
  return items;
}

function stashBlock() {
  const stashes = state.ws?.stashes || [];
  const items = stashes.length
    ? stashes
        .map((stash) => `<button type="button" class="tree-leaf ${state.stashId === stash.id ? "is-on" : ""}" data-stash="${esc(stash.id)}"><span class="grow"><b>${esc(stash.selector || stash.id.slice(0, 7))}</b><small>${esc(stash.message || "（没有说明）")}</small></span></button>`)
        .join("")
    : `<p class="empty">没有贮藏</p>`;
  return `<div class="stash-block"><div class="tree-label">贮藏</div>${items}</div>`;
}

function hideMenu() {
  const menu = document.querySelector("#context-menu");
  if (menu) menu.hidden = true;
}

function ensureMenu() {
  let menu = document.querySelector("#context-menu");
  if (menu) return menu;
  menu = document.createElement("div");
  menu.id = "context-menu";
  menu.hidden = true;
  document.body.appendChild(menu);
  document.addEventListener("click", (event) => {
    if (!menu.hidden && !menu.contains(event.target)) hideMenu();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") hideMenu();
  });
  window.addEventListener("resize", hideMenu);
  return menu;
}

function showMenu(x, y, items) {
  if (state.busy || !items.length) return;
  const menu = ensureMenu();
  menu.innerHTML = items
    .map(
      (item) =>
        `<button type="button" data-act="${esc(item.act)}" data-name="${esc(item.name || "")}" data-sha="${esc(item.sha || "")}" data-stash="${esc(item.stash || "")}" class="${item.danger ? "is-danger" : ""}">${esc(item.label)}</button>`
    )
    .join("");
  menu.hidden = false;
  menu.style.left = "0px";
  menu.style.top = "0px";
  const rect = menu.getBoundingClientRect();
  menu.style.left = `${Math.max(8, Math.min(x, window.innerWidth - rect.width - 8))}px`;
  menu.style.top = `${Math.max(8, Math.min(y, window.innerHeight - rect.height - 8))}px`;
  bindActs(menu);
}

function bind() {
  const repoSelect = app.querySelector("#repo-select");
  if (repoSelect) {
    repoSelect.onchange = () => {
      state.repo = repoSelect.value;
      state.sha = "";
      state.file = "";
      state.fileSide = "";
      state.logFile = "";
      state.picks = {};
      state.selectedBranch = "";
      state.graphRef = "";
      state.stashId = "";
      state.stashDiff = null;
      state.preferStash = false;
      run("repo", async () => null);
    };
  }
  const filter = app.querySelector("#branch-filter");
  if (filter) {
    filter.oninput = () => {
      state.branchFilter = filter.value;
      const pos = filter.selectionStart;
      render();
      const again = app.querySelector("#branch-filter");
      if (again) {
        again.focus();
        again.setSelectionRange(pos, pos);
      }
    };
  }
  app.querySelectorAll("[data-tab]").forEach((node) => {
    node.onclick = async () => {
      state.tab = node.dataset.tab;
      if (state.tab !== "log") state.picks = {};
      try {
        await loadDiff();
      } catch (error) {
        showToast(error.message);
      }
      render();
    };
  });
  app.querySelectorAll("[data-file]").forEach((node) => {
    node.onclick = async () => {
      state.file = node.dataset.file;
      state.fileSide = node.dataset.side === "staged" ? "staged" : "unstaged";
      state.preferStash = false;
      state.diff = null;
      render();
      try {
        await loadDiff();
      } catch (error) {
        showToast(error.message);
      }
      render();
    };
    node.oncontextmenu = (event) => {
      event.preventDefault();
      const file = node.dataset.file || "";
      if (!file) return;
      showMenu(event.clientX, event.clientY, [{ label: "回滚这个文件", act: "restore-file", name: file }]);
    };
  });
  app.querySelectorAll("[data-check]").forEach((node) => {
    node.onchange = () => {
      const file = node.dataset.check;
      if (!file || state.busy) return;
      if (node.checked) run("stage", () => api("/api/stage", { body: { repo: state.repo, files: [file] } }));
      else run("unstage", () => api("/api/unstage", { body: { repo: state.repo, files: [file] } }));
    };
  });
  app.querySelectorAll("[data-sha]").forEach((node) => {
    node.onclick = async () => {
      if (state.busy) return;
      state.sha = node.dataset.sha;
      state.logFile = "";
      state.picks = {};
      state.preferStash = false;
      state.diff = null;
      render();
      try {
        await loadDiff();
      } catch (error) {
        showToast(error.message || "失败");
      }
      render();
    };
    node.oncontextmenu = (event) => {
      event.preventDefault();
      const sha = node.dataset.sha || "";
      if (!sha) return;
      showMenu(event.clientX, event.clientY, [
        { label: "拣选到当前分支", act: "pick-here", sha },
        { label: "从这里开始变基", act: "rebase-from", sha },
        { label: "从这次新建", act: "branch-at", sha },
      ]);
    };
  });
  app.querySelectorAll("[data-log-file]").forEach((node) => {
    node.onclick = async () => {
      const file = node.dataset.logFile;
      const fromStash = state.preferStash && state.stashId;
      state.logFile = file;
      state.preferStash = false;
      const current = !fromStash && (state.diff?.files || []).find((item) => item.path === file);
      const need = Boolean(state.sha && current && !current.binary && !(current.hunks || []).length && state.diff?.file !== file);
      if (need) {
        render();
        try {
          await loadDiff();
        } catch (error) {
          showToast(error.message || "失败");
        }
      }
      render();
    };
  });
  app.querySelectorAll("[data-hunk]").forEach((node) => {
    node.onchange = () => {
      const key = node.dataset.hunk;
      if (node.checked) {
        state.picks[key] = true;
        if (/空白|二进制|对不上/.test(node.dataset.hint || "")) showToast(node.dataset.hint);
      } else delete state.picks[key];
    };
  });
  app.querySelectorAll("[data-hunk-restore]").forEach((node) => {
    node.onclick = () => action("restore-hunk", { file: node.dataset.hunkRestore, index: Number(node.dataset.index), hint: node.dataset.hint });
  });
  app.querySelectorAll("[data-leaf]").forEach((node) => {
    node.onclick = () => {
      if (state.busy || !node.dataset.leaf) return;
      const name = node.dataset.leaf;
      const same = state.selectedBranch === name && state.graphRef === name && !state.stashId && !state.preferStash;
      state.selectedBranch = name;
      state.stashId = "";
      state.preferStash = false;
      if (state.graphRef !== name) {
        state.graphRef = name;
        render();
        loadGraph();
        return;
      }
      if (!same) render();
    };
    node.oncontextmenu = (event) => {
      event.preventDefault();
      const leaf = findLeaf(state.ws?.branchTree, node.dataset.leaf || "");
      const items = branchMenu(leaf);
      if (!items.length) return;
      state.selectedBranch = leaf.checkout;
      showMenu(event.clientX, event.clientY, items);
    };
  });
  app.querySelectorAll("[data-graph-ref]").forEach((node) => {
    node.onclick = (event) => {
      event.stopPropagation();
      if (state.busy) return;
      const name = node.dataset.graphRef || "";
      if (!name || name === state.graphRef) return;
      state.graphRef = name;
      render();
      loadGraph();
    };
  });
  app.querySelectorAll("[data-graph-all]").forEach((node) => {
    node.onclick = () => {
      if (state.busy || !state.graphRef) return;
      state.graphRef = "";
      render();
      loadGraph();
    };
  });
  const graphSelect = app.querySelector("#graph-ref");
  if (graphSelect) {
    graphSelect.onchange = () => {
      if (state.busy) return;
      const name = graphSelect.value;
      if (name === state.graphRef) return;
      state.graphRef = name;
      render();
      loadGraph();
    };
  }
  app.querySelectorAll("[data-stash]").forEach((node) => {
    node.onclick = () => loadStash(node.dataset.stash);
    node.oncontextmenu = (event) => {
      event.preventDefault();
      const id = node.dataset.stash || "";
      if (!id) return;
      showMenu(event.clientX, event.clientY, [
        { label: "还原", act: "stash-apply", stash: id },
        { label: "丢弃", act: "stash-drop", stash: id, danger: true },
      ]);
    };
  });
  const target = app.querySelector("#target");
  if (target) {
    target.onchange = async () => {
      state.target = target.value;
      if (!state.sha || state.busy) return;
      state.diff = null;
      render();
      try {
        await loadDiff();
      } catch (error) {
        showToast(error.message || "失败");
      }
      render();
    };
  }
  const message = app.querySelector("#message");
  if (message) {
    message.oninput = () => {
      state.message = message.value;
    };
  }
  bindActs(app);
}

function bindActs(root) {
  root.querySelectorAll("[data-act]").forEach((node) => {
    node.onclick = (event) => {
      if (node.closest("#context-menu")) event.stopPropagation();
      hideMenu();
      action(node.dataset.act, node.dataset.name, { sha: node.dataset.sha || "", stash: node.dataset.stash || "" });
    };
  });
}

const REMOTE_WARN = "这里面有的提交已经推送到远程。合成会改写它们，之后普通推送会被拒绝。这个工具不会强制推送。";

function hereName() {
  if (!state.ws || state.ws.detached || !state.ws.branch) return "游离 HEAD";
  return state.ws.branch;
}

function dirtyBlocked() {
  if (!state.ws?.dirty) return false;
  showToast("工作区有未提交改动，请先提交");
  return true;
}

function rangeBox(options) {
  const commits = options.commits || [];
  const plain = options.plainText ? `<button type="button" data-plain>${esc(options.plainText)}</button>` : "";
  const rows = commits
    .map((commit, index) => {
      const when = shortWhen(commit.date);
      const checked = index < (options.defaultCount || 0) ? "checked" : "";
      const subject = commit.message || "（没有说明）";
      return `<label class="range-row"><input type="checkbox" data-range="${esc(commit.sha)}" ${checked} /><span class="range-subject" title="${esc(subject)}">${esc(subject)}</span><span class="range-sha">${esc(commit.shortSha || "")}</span><span class="range-author" title="${esc(commit.author || "")}">${esc(commit.author || "")}</span><span class="range-when">${esc(when)}</span></label>`;
    })
    .join("");
  return new Promise((resolve) => {
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      modal.hidden = true;
      modal.onclick = null;
      resolve(value);
    };
    modal.hidden = false;
    modal.onclick = null;
    modal.innerHTML = `<div class="modal__card is-commits"><h2>${esc(options.title || "")}</h2><p class="range-note" title="${esc(options.intro || "")}">${esc(options.intro || "")}</p><p id="range-warn" class="range-warn" hidden>${esc(REMOTE_WARN)}</p><div class="range-list">${rows}</div><label class="field">提交说明<textarea id="range-message">${esc(commits[0]?.message || "")}</textarea></label><div class="modal__actions"><span class="range-hint">只能从最新一笔往下连续选，至少两笔</span><button type="button" data-no>取消</button>${plain}<button type="button" class="primary" data-squash>${esc(options.squashText || "合成一个")}</button></div></div>`;
    const boxes = [...modal.querySelectorAll("[data-range]")];
    const warn = modal.querySelector("#range-warn");
    const squashBtn = modal.querySelector("[data-squash]");
    const message = modal.querySelector("#range-message");
    const list = modal.querySelector(".range-list");
    if (list) list.onclick = (event) => event.stopPropagation();
    const update = () => {
      const count = boxes.filter((box) => box.checked).length;
      const contiguous = boxes.every((box, index) => box.checked === index < count);
      if (squashBtn) squashBtn.disabled = count < 2 || !contiguous || Boolean(options.truncated);
      const remote = options.mode === "replay" ? commits.some((commit) => commit.onRemote) : boxes.some((box, index) => box.checked && commits[index]?.onRemote);
      if (warn) warn.hidden = !remote;
    };
    boxes.forEach((box, index) => {
      box.onchange = () => {
        if (box.checked) {
          for (let i = 0; i <= index; i++) boxes[i].checked = true;
        } else {
          for (let i = index; i < boxes.length; i++) boxes[i].checked = false;
        }
        update();
      };
    });
    update();
    modal.onclick = (event) => {
      const squash = event.target.closest("[data-squash]");
      const plainBtn = event.target.closest("[data-plain]");
      const no = event.target.closest("[data-no]");
      if (squash) {
        if (squash.disabled) return;
        const text = message ? message.value : "";
        if (!String(text).trim()) return showToast("先写提交说明");
        finish({
          kind: "squash",
          shas: boxes.filter((box) => box.checked).map((box) => box.dataset.range),
          message: text,
        });
        return;
      }
      if (plainBtn) {
        finish({ kind: "plain" });
        return;
      }
      if (no || event.target === modal) finish(null);
    };
  });
}

function fromBox(options) {
  const rows = (options.commits || [])
    .map((commit) => {
      const when = shortWhen(commit.date);
      const subject = commit.message || "（没有说明）";
      return `<div class="range-row"><span class="range-subject" title="${esc(subject)}">${esc(subject)}</span><span class="range-sha">${esc(commit.shortSha || "")}</span><span class="range-author" title="${esc(commit.author || "")}">${esc(commit.author || "")}</span><span class="range-when">${esc(when)}</span></div>`;
    })
    .join("");
  const choices = (options.branches || [])
    .map((name) => `<option value="${esc(name)}">${esc(name)}</option>`)
    .join("");
  return new Promise((resolve) => {
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      modal.hidden = true;
      modal.onclick = null;
      resolve(value);
    };
    modal.hidden = false;
    modal.onclick = null;
    modal.innerHTML = `<div class="modal__card is-commits"><h2>从这次提交开始变基</h2><p class="range-note" title="${esc(options.intro || "")}">${esc(options.intro || "")}</p><div class="range-list">${rows}</div><label class="field">接到<select name="onto">${choices}</select></label><div class="modal__actions"><button type="button" data-no>取消</button><button type="button" class="primary" data-yes>开始变基</button></div></div>`;
    const list = modal.querySelector(".range-list");
    if (list) list.onclick = (event) => event.stopPropagation();
    modal.onclick = (event) => {
      if (event.target.closest("[data-yes]")) {
        const onto = modal.querySelector("select")?.value || "";
        if (!onto) return;
        finish({ onto });
        return;
      }
      if (event.target.closest("[data-no]") || event.target === modal) finish(null);
    };
  });
}

async function openRebaseFrom(rev) {
  if (state.busy) return;
  if (!rev) return showToast("先点一次提交");
  if (!state.ws?.branch || state.ws.detached) return showToast("先检出一个分支");
  if (dirtyBlocked()) return;
  const branches = graphBranchNames().filter((name) => name !== state.ws.branch);
  if (!branches.length) return showToast("没有可以接过去的分支");
  let data;
  try {
    data = await api(`/api/history-range?repo=${encodeURIComponent(state.repo)}&mode=from&rev=${encodeURIComponent(rev)}`);
  } catch (error) {
    return showToast(error.message || "失败");
  }
  if (!data.commits?.length) return showToast(data.reason || "没有需要变基的提交");
  let intro = `从 ${(data.rev || rev).slice(0, 7)} 到当前分支最新的提交，会接到所选分支后面。这次之前的提交不会跟着过去。其他分支不动。有冲突会取消，仓库回到现在这样。`;
  if (data.root) intro += "这是根提交，整段历史都会接过去。";
  if (data.hasMerges) intro += "其中有合并提交。变基会跳过合并点。";
  if (data.truncated) intro += "列表只显示最近 30 笔，更早的也会一起接过去。";
  if (data.commits.some((commit) => commit.onRemote)) intro += "这里面有的提交已经推送到远程。变基会改写它们，之后普通推送会被拒绝。这个工具不会强制推送。";
  const picked = await fromBox({ intro, commits: data.commits, branches });
  if (!picked?.onto) return;
  return run("rebase-from", () => api("/api/rebase-from", { body: { repo: state.repo, sha: rev, onto: picked.onto } }));
}

async function openSquash() {
  if (state.busy) return;
  if (!state.ws?.branch || state.ws.detached) return showToast("先检出一个分支");
  if (dirtyBlocked()) return;
  let data;
  try {
    data = await api(`/api/history-range?repo=${encodeURIComponent(state.repo)}&mode=squash`);
  } catch (error) {
    return showToast(error.message || "失败");
  }
  if (!data.commits?.length) return showToast(data.reason || "当前分支上至少要有两笔自己的提交才能合成");
  const picked = await rangeBox({
    mode: "squash",
    title: "合成最近几次提交",
    intro: "把当前分支最新的连续几笔收成一个新提交。没勾的更旧提交留在原地。其他分支不动。有冲突或失败会回到现在这样。勾较旧的一笔就自动勾上比它新的全部；取消较新的一笔就自动取消比它旧的全部。",
    commits: data.commits,
    defaultCount: 2,
    squashText: "合成一个",
  });
  if (!picked || picked.kind !== "squash") return;
  return run("squash", () => api("/api/squash", { body: { repo: state.repo, shas: picked.shas, message: picked.message } }));
}

async function openReplay(onto) {
  if (state.busy) return;
  if (!onto) return showToast("先选择分支");
  if (dirtyBlocked()) return;
  let data;
  try {
    data = await api(`/api/history-range?repo=${encodeURIComponent(state.repo)}&mode=replay&onto=${encodeURIComponent(onto)}`);
  } catch (error) {
    return showToast(error.message || "失败");
  }
  if (!data.commits?.length) return showToast(data.reason || "没有可以接过去的提交。当前分支的改动目标里都有了。");
  let intro = `这些是当前分支上、「${onto}」还没有的提交。不勾选就原样接过去。从最新一笔往下连续勾选，可以把那几笔收成一个再接过去；没勾的更旧提交会一笔笔照原样接上，不丢掉。其他分支不动。有冲突会取消，仓库回到现在这样。`;
  if (data.hasMerges) intro += "\n其中有合并提交。原样接过去时会跳过合并点。要合成的话，请只勾没有合并点的最新连续几笔；勾到合并提交会被拒绝。";
  if (data.truncated) intro += "\n超过 30 笔，这次只能原样接过去。";
  const picked = await rangeBox({
    mode: "replay",
    title: `接到「${onto}」后面`,
    intro,
    commits: data.commits,
    defaultCount: 0,
    truncated: Boolean(data.truncated),
    squashText: "合成勾选的再接过去",
    plainText: "原样接过去",
  });
  if (!picked) return;
  if (picked.kind === "plain") return run("rebase", () => api("/api/rebase", { body: { repo: state.repo, onto } }));
  return run("rebase-squash", () => api("/api/rebase-squash", { body: { repo: state.repo, onto, shas: picked.shas, message: picked.message } }));
}

async function action(name, arg, extra = {}) {
  if (!state.repo) return showToast("先选择仓库");
  if (name === "fetch") return run("fetch", () => api("/api/fetch", { body: { repo: state.repo } }));
  if (name === "pull") {
    if (state.ws?.dirty) return showToast("工作区有未提交改动，请先提交");
    const text = state.ws?.upstream ? `当前分支落后 ${state.ws.behind} 个提交` : "从 origin 拉取当前分支";
    if (!(await confirmBox("拉取", `${text}\n有冲突会取消。`))) return;
    return run("pull", () => api("/api/pull", { body: { repo: state.repo } }));
  }
  if (name === "push") {
    const text = state.ws?.upstream ? `将推送当前分支，超前 ${state.ws.ahead} 个提交` : "当前分支还没有上游，推送后会设置上游";
    if (state.ws?.upstream && state.ws.ahead === 0) return showToast("没有需要推送的提交");
    if (!(await confirmBox("推送", text))) return;
    return run("push", () => api("/api/push", { body: { repo: state.repo } }));
  }
  if (name === "commit") {
    if (!stagedCount()) return showToast("没有已暂存的文件");
    if (!state.message.trim()) return showToast("先写提交说明");
    return run("commit", () => api("/api/commit", { body: { repo: state.repo, message: state.message } }));
  }
  if (name === "branch") {
    const data = await formBox("新建分支", `<label class="field">分支名<input name="name" required /></label>`);
    if (!data?.name) return;
    return run("branch", () => api("/api/branch", { body: { repo: state.repo, name: data.name } }));
  }
  if (name === "branch-at") {
    const rev = extra.sha || state.sha;
    if (!rev) return showToast("先点一次提交");
    const short = rev.slice(0, 7);
    const data = await formBox("从这次提交新建分支", `<p class="meta">${esc(short)}。新建后仍停在当前分支。</p><label class="field">分支名<input name="name" required /></label>`);
    if (!data?.name) return;
    return run("branch-at", () => api("/api/branch-at", { body: { repo: state.repo, name: data.name, sha: rev } }));
  }
  if (name === "restore-file") {
    const file = arg || state.file;
    if (!file || !(state.ws?.changes || []).some((change) => change.path === file)) return showToast("先点一个文件");
    if (!(await confirmBox("回滚", file, "回滚"))) return;
    return run("restore", () => api("/api/restore", { body: { repo: state.repo, files: [file] } }));
  }
  if (name === "restore-hunk") {
    if (!arg?.file) return showToast("先点一个文件");
    if (!(await confirmBox("回滚这段", `${arg.file}\nhunk ${arg.hint || arg.index}`, "回滚"))) return;
    return run("hunk", () => api("/api/restore-hunk", { body: { repo: state.repo, file: arg.file, index: arg.index } }));
  }
  if (name === "checkout") {
    if (!arg) return showToast("先选择分支");
    const text = `转到「${arg}」。你现在在「${hereName()}」。`;
    if (state.ws?.dirty) {
      if (!(await confirmBox("切过去", text, "先藏起改动再切换"))) return;
      return run("checkout", () => api("/api/checkout", { body: { repo: state.repo, name: arg, stash: true } }));
    }
    if (!(await confirmBox("切过去", text))) return;
    return run("checkout", () => api("/api/checkout", { body: { repo: state.repo, name: arg } }));
  }
  if (name === "merge") {
    if (!(await confirmBox("合进来", `把「${arg}」合并进当前分支「${hereName()}」。有冲突会取消，仓库回到现在这样。`))) return;
    return run("merge", () => api("/api/merge", { body: { repo: state.repo, branch: arg } }));
  }
  if (name === "squash") return openSquash();
  if (name === "replay") return openReplay(arg);
  if (name === "rename") {
    if (!arg) return showToast("先选择分支");
    const data = await formBox("修改分支名", `<p class="meta">现在叫「${esc(arg)}」。只改这台电脑上的名字，远程不会跟着改。</p><label class="field">新名字<input name="name" value="${esc(arg)}" required /></label>`);
    if (!data?.name) return;
    const next = String(data.name).trim();
    if (!next || next === arg) return showToast("名字没有变");
    return run("rename", async () => {
      const result = await api("/api/rename-branch", { body: { repo: state.repo, from: arg, to: next } });
      if (state.selectedBranch === arg) state.selectedBranch = next;
      if (state.graphRef === arg) state.graphRef = next;
      return result;
    });
  }
  if (name === "delete") {
    if (!arg) return showToast("先选择分支");
    if (!(await confirmBox("删除", `删除本地分支「${arg}」。当前分支不会动。远程不会删。`, "删除"))) return;
    const call = (force) => api("/api/delete-branch", { body: { repo: state.repo, name: arg, force: force === true } });
    try {
      await call(false);
    } catch (error) {
      const message = error.message || "失败";
      if (!message.includes("还没合并")) return showToast(message);
      if (!(await confirmBox("仍要删除", `「${arg}」还没合并进当前分支。别的分支如果也指着这些提交，提交还在。否则删除后不容易找回来。远程不会删。`, "仍要删除"))) return;
      return run("delete", () => call(true));
    }
    return run("delete", async () => ({ notice: `已删除 ${arg}` }));
  }
  if (name === "stash-apply" || name === "stash-drop") {
    const id = extra.stash || state.stashId;
    const stash = (state.ws?.stashes || []).find((item) => item.id === id);
    if (!stash) return showToast("先选择贮藏");
    if (name === "stash-drop") {
      if (!(await confirmBox("丢弃贮藏", `${stash.selector || ""}\n${stash.message || ""}`, "丢弃"))) return;
      return run("stash", () => api("/api/stash/drop", { body: { repo: state.repo, stash: stash.id } }));
    }
    if (!(await confirmBox("还原贮藏", `${stash.selector || ""}\n${stash.message || ""}`, "还原"))) return;
    return run("stash", () => api("/api/stash/apply", { body: { repo: state.repo, stash: stash.id } }));
  }
  if (name === "rebase-from") return openRebaseFrom(extra.sha || state.sha);
  if (name === "pick-here") {
    const rev = extra.sha || state.sha;
    if (!rev) return showToast("先点一次提交");
    if (state.ws?.detached || !state.ws?.branch) return showToast("游离 HEAD 不能拣选");
    if (state.ws?.dirty) return showToast("工作区有未提交改动，请先提交");
    const branch = state.ws.branch;
    if (!(await confirmBox("拣选到当前分支", `把 ${rev.slice(0, 7)} 拣选到当前分支「${branch}」。有冲突会取消，仓库回到现在这样。`))) return;
    return run("pick", () => api("/api/cherry-pick", { body: { repo: state.repo, sha: rev, target: branch } }));
  }
  if (name === "pick-all" || name === "pick-part") {
    if (!state.sha) return showToast("先选择提交");
    if (!state.target) return showToast("先选择目标分支");
    const hunks = Object.keys(state.picks)
      .filter((key) => state.picks[key])
      .map((key) => {
        const nl = key.indexOf("\n");
        return { index: Number(key.slice(0, nl)), file: key.slice(nl + 1) };
      });
    if (name === "pick-part" && !hunks.length) return showToast("先勾选要拣选的片段");
    const hints = (state.diff?.hints || []).filter(Boolean).join("\n");
    const title = name === "pick-all" ? "整次拿到所选分支" : "只拿勾选的片段";
    const doing = name === "pick-all" ? `把这次提交整次拿到「${state.target}」。` : `只把勾选的片段拿到「${state.target}」。`;
    if (!(await confirmBox(title, `${doing}\n做完仍回到当前分支「${hereName()}」。${hints ? `\n${hints}` : ""}`))) return;
    const path = name === "pick-all" ? "/api/cherry-pick" : "/api/cherry-pick/partial";
    const body = { repo: state.repo, sha: state.sha, target: state.target, hunks, message: `拣选 ${state.sha.slice(0, 7)} 的部分改动` };
    return run("pick", async () => {
      const result = await api(path, { body });
      state.picks = {};
      return result;
    });
  }
}

async function openSettings() {
  const home = state.home || { cloneRoot: "", github: {}, gitee: {} };
  const data = await formBox(
    "目录和账号",
    `
    <label class="field">仓库目录<input name="cloneRoot" value="${esc(home.cloneRoot || "")}" placeholder="D:\\GitFiles" /></label>
    <p class="meta">列出这个文件夹里已经有的 Git 仓库。新克隆也放在这里。</p>
    <label class="field">GitHub 令牌<input name="githubToken" type="password" placeholder="${home.github?.configured ? `已保存末四位 ${esc(home.github.hint)}，留空不改` : "ghp_ 或 github_pat"}" /></label>
    <label class="field"><input name="clearGithub" type="checkbox" /> 清除 GitHub 令牌</label>
    <label class="field">Gitee 令牌<input name="giteeToken" type="password" placeholder="${home.gitee?.configured ? `已保存末四位 ${esc(home.gitee.hint)}，留空不改` : "私人令牌"}" /></label>
    <label class="field"><input name="clearGitee" type="checkbox" /> 清除 Gitee 令牌</label>`
  );
  if (!data) return;
  await run("settings", () =>
    api("/api/settings", {
      body: {
        cloneRoot: data.cloneRoot,
        githubToken: data.githubToken,
        giteeToken: data.giteeToken,
        clearGithub: data.clearGithub === "on",
        clearGitee: data.clearGitee === "on",
      },
    })
  );
}

async function openClone() {
  if (!state.home?.ready) return openSettings();
  const data = await formBox("克隆", `<label class="field">仓库地址<input name="url" placeholder="https://github.com/owner/name" required /></label><p class="meta">克隆到 ${esc(state.home.cloneRoot)}</p>`);
  if (!data?.url) return;
  await run("clone", () => api("/api/clone", { body: { url: data.url } }));
}

document.querySelector("#open-settings").onclick = openSettings;
document.querySelector("#open-clone").onclick = openClone;

loadHome()
  .then(() => (state.repo && state.home.ready ? loadRepo() : null))
  .then(render)
  .catch((error) => {
    app.innerHTML = `<p class="empty">${esc(error.message)}</p>`;
  });
