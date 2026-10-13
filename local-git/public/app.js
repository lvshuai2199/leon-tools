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
  tab: "changes",
  file: "",
  logFile: "",
  sha: "",
  target: "",
  picks: {},
  fileSide: "",
  message: "",
  busy: "",
  selectedBranch: "",
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

async function loadRepo() {
  state.ws = null;
  state.log = null;
  state.diff = null;
  if (!state.repo) return;
  localStorage.setItem("local-git-repo", state.repo);
  state.ws = await api(`/api/workspace?repo=${encodeURIComponent(state.repo)}`);
  state.log = await api(`/api/log?repo=${encodeURIComponent(state.repo)}`);
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
    const data = await api(`/api/diff?repo=${encodeURIComponent(state.repo)}&rev=${encodeURIComponent(state.sha)}&target=${target}`);
    if (seq !== diffSeq) return;
    state.diff = data;
    if (state.logFile && !(state.diff.files || []).some((file) => file.path === state.logFile)) state.logFile = "";
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
  } catch (error) {
    if (state.stashId !== id) return;
    state.stashDiff = null;
    state.stashError = error.message || "打不开这条贮藏的差异";
    showToast(state.stashError);
  }
  render();
}

async function refresh(notice) {
  await loadHome();
  if (state.repo) await loadRepo();
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
    await refresh(result?.notice);
  } catch (error) {
    showToast(error.message || "失败");
    render();
  } finally {
    state.busy = "";
    render();
  }
}

function render() {
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
  const rollback = state.tab === "changes" ? `<button type="button" data-act="restore-file" ${dis()}>回滚</button>` : "";
  const changeCount = ws ? (ws.changes || []).length : null;
  const changeTab = changeCount == null ? "变更" : `变更 (${changeCount})`;
  const screen = state.tab === "log" ? logScreen() : state.tab === "branches" ? branchScreen() : changeScreen();
  app.innerHTML = `
    <div class="toolbar">
      <select id="repo-select" class="repo-select" ${dis()}>${options || `<option value="">没有仓库</option>`}</select>
      <button type="button" data-act="fetch" ${dis()}>获取</button>
      <button type="button" data-act="pull" ${dis()}>拉取</button>
      <button type="button" data-act="push" ${dis()}>推送</button>
      ${commitButton("提交")}
      <button type="button" data-act="branch" ${dis()}>新建分支</button>
      <button type="button" data-act="compare" ${dis()}>对比</button>
      ${rollback}
      <p class="meta toolbar-meta">${esc(meta)}</p>
    </div>
    <div class="tabs">
      <button type="button" data-tab="changes" class="${state.tab === "changes" ? "is-on" : ""}">${changeTab}</button>
      <button type="button" data-tab="log" class="${state.tab === "log" ? "is-on" : ""}">日志</button>
      <button type="button" data-tab="branches" class="${state.tab === "branches" ? "is-on" : ""}">分支</button>
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
  return `<div class="screen-scroll"><div class="screen cols-3"><section class="pane col">${treePane()}</section><section class="pane graph-pane">${renderGraph()}</section><section class="pane diff-pane log-pane">${logRight()}</section></div></div>`;
}

function branchScreen() {
  return `<div class="screen cols-2"><section class="pane col">${treePane()}</section><section class="pane"><p class="meta">检出、合并、变基和删除都作用在当前仓库。合并和变基如果冲突会直接取消，工作区保持原样。</p>${stashDiffHtml()}</section></div>`;
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

function logRight() {
  if (state.preferStash && state.stashId) return `<div class="diff-scroll">${stashDiffHtml()}</div>`;
  if (!state.sha) return `<p class="empty">点一次提交查看文件</p>`;
  const hints = state.diff?.hints || [];
  const locals = (state.ws?.branches || []).filter((branch) => !branch.remote);
  const options = locals
    .map((branch) => `<option value="${esc(branch.name)}" ${branch.name === state.target ? "selected" : ""}>${esc(branch.name)}${branch.current ? "（当前）" : ""}</option>`)
    .join("");
  const pickBar = `<div class="field"><label>拣选到</label><select id="target">${options}</select></div><div class="row"><button type="button" data-act="pick-all" ${dis()}>拣选整次提交</button><button type="button" class="primary" data-act="pick-part" ${dis()}>拣选勾选片段</button></div>`;
  const files = state.diff?.files || [];
  const list = files.length
    ? files
        .map((file) => `<button type="button" class="item ${state.logFile === file.path ? "is-on" : ""}" data-log-file="${esc(file.path)}"><span class="grow"><b>${esc(file.path)}</b><small>${esc((file.hints || []).join(" · "))}</small></span></button>`)
        .join("")
    : `<p class="empty">这次提交没有文件差异</p>`;
  const selected = files.find((file) => file.path === state.logFile);
  const body = selected ? renderFile(selected) : `<p class="empty">点一个文件查看差异</p>`;
  return `<div class="hint ${hintTone(hints) === "warn" ? "is-warn" : ""}">${esc(hints.join("\n"))}</div>${pickBar}<div class="list file-list">${list}</div><div class="diff-scroll">${body}</div>`;
}

function stashDiffHtml() {
  if (!state.stashId) return `<p class="empty">选一条贮藏查看差异</p>`;
  if (state.stashError) return `<p class="empty">${esc(state.stashError)}</p>`;
  if (!state.stashDiff) return `<p class="empty">正在读取贮藏差异…</p>`;
  const page = state.stashDiff;
  const files = (page.files || []).map((file) => renderFile(file)).join("") || `<p class="empty">没有差异</p>`;
  return `<div class="hint">${esc((page.hints || []).join("\n"))}</div><p class="meta">${esc(page.message || "")}</p>${files}`;
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

function renderGraph() {
  const commits = state.log?.commits || [];
  const rows = state.log?.graph || [];
  if (!commits.length) return `<p class="empty">还没有提交</p>`;
  const rowHeight = 56;
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
      const x1 = x(link.from || 0);
      const y1 = y(index);
      if (link.kind === "bottom") {
        marks += `<line x1="${x1}" y1="${y1}" x2="${x1}" y2="${y1 + rowHeight / 2 - 2}" stroke="${stroke}" stroke-width="2" />`;
        continue;
      }
      const x2 = x(link.to || 0);
      const y2 = y(index + 1);
      if (link.from === link.to) marks += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${stroke}" stroke-width="2.5" stroke-linecap="round" />`;
      else {
        const mid = (y1 + y2) / 2;
        marks += `<path d="M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}" fill="none" stroke="${stroke}" stroke-width="2.5" stroke-linecap="round" />`;
      }
    }
    const cx = x(row.lane || 0);
    const cy = y(index);
    marks += `<circle cx="${cx}" cy="${cy}" r="6" fill="#fff" stroke="${color}" stroke-width="3" />`;
  });
  const lines = commits
    .map((commit) => {
      const tags = (commit.refs || [])
        .map((ref) => {
          const kind = ref && ref.kind ? ref.kind : "local";
          const label = ref && ref.name ? ref.name : ref;
          return `<span class="tag tag-${esc(kind)}">${esc(label)}</span>`;
        })
        .join("");
      const when = shortWhen(commit.date);
      return `<button type="button" class="graph-line ${state.sha === commit.sha ? "is-on" : ""}" data-sha="${esc(commit.sha)}" style="height:${rowHeight}px" title="${esc(commit.message || "")}"><span class="graph-msg"><span class="graph-subject"><b>${esc(commit.message || "（没有说明）")}</b> ${tags}</span><small>${esc(commit.shortSha)} · ${esc(commit.author)}${when ? ` · ${esc(when)}` : ""}</small></span></button>`;
    })
    .join("");
  const note = state.log?.truncated ? `<p class="meta">只显示最近 ${commits.length} 条</p>` : "";
  return `<div class="graph-head"><b>分支图</b><span>本地和远程提交在同一棵树上</span></div><div class="graph-scroll"><div class="graph-body" style="height:${height}px"><svg class="graph-svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">${marks}</svg><div class="graph-rows">${lines}</div></div>${note}</div>`;
}

function treePane() {
  const tree = state.ws?.branchTree;
  if (!tree) return `<p class="empty">先选择仓库</p>`;
  const groups = (tree.groups || []).map(renderGroup).join("");
  const visible = groups.trim();
  return `<input id="branch-filter" placeholder="过滤分支" value="${esc(state.branchFilter)}" />${branchActions(findLeaf(tree, state.selectedBranch))}<div class="list grow">${visible || `<p class="empty">${state.branchFilter.trim() ? "没有匹配的分支" : "没有分支"}</p>`}</div>${stashBlock()}`;
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

function branchActions(leaf) {
  if (!leaf || leaf.detached || !leaf.checkout) return "";
  if (leaf.current && !leaf.remote) return `<p class="meta">当前分支 ${esc(leaf.checkout)}</p>`;
  const name = esc(leaf.checkout);
  const buttons = [
    `<button type="button" data-act="checkout" data-name="${name}" ${dis()}>检出</button>`,
    `<button type="button" data-act="merge" data-name="${name}" ${dis()}>合并</button>`,
    `<button type="button" data-act="rebase" data-name="${name}" ${dis()}>变基</button>`,
  ];
  if (!leaf.remote) buttons.push(`<button type="button" data-act="delete" data-name="${name}" ${dis()}>删除</button>`);
  return `<div class="branch-actions">${buttons.join("")}</div>`;
}

function stashBlock() {
  const stashes = state.ws?.stashes || [];
  const items = stashes.length
    ? stashes
        .map((stash) => `<button type="button" class="tree-leaf ${state.stashId === stash.id ? "is-on" : ""}" data-stash="${esc(stash.id)}"><span class="grow"><b>${esc(stash.selector || stash.id.slice(0, 7))}</b><small>${esc(stash.message || "（没有说明）")}</small></span></button>`)
        .join("")
    : `<p class="empty">没有贮藏</p>`;
  const selected = stashes.find((stash) => stash.id === state.stashId);
  const actions = selected
    ? `<div class="branch-actions"><button type="button" data-act="stash-apply" ${dis()}>还原</button><button type="button" data-act="stash-drop" ${dis()}>丢弃</button></div>`
    : "";
  return `<div class="stash-block"><div class="tree-label">贮藏</div>${items}${actions}</div>`;
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
      state.stashId = "";
      state.stashDiff = null;
      state.preferStash = false;
      run("repo", async () => {
        await loadRepo();
        return null;
      });
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
    node.onclick = () => {
      state.sha = node.dataset.sha;
      state.logFile = "";
      state.picks = {};
      state.preferStash = false;
      run("diff", async () => {
        await loadDiff();
        return null;
      });
    };
  });
  app.querySelectorAll("[data-log-file]").forEach((node) => {
    node.onclick = () => {
      state.logFile = node.dataset.logFile;
      state.preferStash = false;
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
      if (!node.dataset.leaf) return;
      state.selectedBranch = node.dataset.leaf;
      state.stashId = "";
      state.preferStash = false;
      render();
    };
  });
  app.querySelectorAll("[data-stash]").forEach((node) => {
    node.onclick = () => loadStash(node.dataset.stash);
  });
  const target = app.querySelector("#target");
  if (target) {
    target.onchange = () => {
      state.target = target.value;
      if (state.sha) {
        run("diff", async () => {
          await loadDiff();
          return null;
        });
      }
    };
  }
  const message = app.querySelector("#message");
  if (message) {
    message.oninput = () => {
      state.message = message.value;
    };
  }
  app.querySelectorAll("[data-act]").forEach((node) => {
    node.onclick = () => action(node.dataset.act, node.dataset.name);
  });
}

async function action(name, arg) {
  if (!state.repo) return showToast("先选择仓库");
  if (name === "fetch") return run("fetch", () => api("/api/fetch", { body: { repo: state.repo } }));
  if (name === "pull") {
    if (state.ws?.dirty) return showToast("工作区有未提交改动，请先提交");
    const text = state.ws?.upstream ? `当前分支落后 ${state.ws.behind} 个提交` : "从 origin 拉取当前分支";
    if (!(await confirmBox("拉取", text))) return;
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
  if (name === "compare") {
    const known = (state.ws?.changes || []).find((change) => change.path === state.file);
    if (!known) return showToast("先点一个文件");
    if (state.fileSide !== "staged" && state.fileSide !== "unstaged") state.fileSide = isUnstaged(known) ? "unstaged" : "staged";
    state.tab = "changes";
    state.preferStash = false;
    try {
      await loadDiff();
    } catch (error) {
      return showToast(error.message);
    }
    render();
    return;
  }
  if (name === "restore-file") {
    if (!state.file || !(state.ws?.changes || []).some((change) => change.path === state.file)) return showToast("先点一个文件");
    if (!(await confirmBox("回滚", state.file, "回滚"))) return;
    return run("restore", () => api("/api/restore", { body: { repo: state.repo, files: [state.file] } }));
  }
  if (name === "restore-hunk") {
    if (!arg?.file) return showToast("先点一个文件");
    if (!(await confirmBox("回滚这段", `${arg.file}\nhunk ${arg.hint || arg.index}`, "回滚"))) return;
    return run("hunk", () => api("/api/restore-hunk", { body: { repo: state.repo, file: arg.file, index: arg.index } }));
  }
  if (name === "checkout") {
    if (!arg) return showToast("先选择分支");
    if (state.ws?.dirty) {
      if (!(await confirmBox("检出", `${arg}\n工作区有未提交改动。`, "贮藏并切换"))) return;
      return run("checkout", () => api("/api/checkout", { body: { repo: state.repo, name: arg, stash: true } }));
    }
    if (!(await confirmBox("检出", arg))) return;
    return run("checkout", () => api("/api/checkout", { body: { repo: state.repo, name: arg } }));
  }
  if (name === "merge") {
    if (!(await confirmBox("合并", `把 ${arg} 合并进当前分支。有冲突会取消。`))) return;
    return run("merge", () => api("/api/merge", { body: { repo: state.repo, branch: arg } }));
  }
  if (name === "rebase") {
    if (!(await confirmBox("变基", `把当前分支变基到 ${arg}。有冲突会取消。`))) return;
    return run("rebase", () => api("/api/rebase", { body: { repo: state.repo, onto: arg } }));
  }
  if (name === "delete") {
    if (!(await confirmBox("删除分支", arg, "删除"))) return;
    return run("delete", () => api("/api/delete-branch", { body: { repo: state.repo, name: arg } }));
  }
  if (name === "stash-apply" || name === "stash-drop") {
    const stash = (state.ws?.stashes || []).find((item) => item.id === state.stashId);
    if (!stash) return showToast("先选择贮藏");
    if (name === "stash-drop") {
      if (!(await confirmBox("丢弃贮藏", `${stash.selector || ""}\n${stash.message || ""}`, "丢弃"))) return;
      return run("stash", () => api("/api/stash/drop", { body: { repo: state.repo, stash: stash.id } }));
    }
    if (!(await confirmBox("还原贮藏", `${stash.selector || ""}\n${stash.message || ""}`, "还原"))) return;
    return run("stash", () => api("/api/stash/apply", { body: { repo: state.repo, stash: stash.id } }));
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
    const hints = (state.diff?.hints || []).join("\n");
    const title = name === "pick-all" ? "拣选整次提交" : "拣选片段";
    if (!(await confirmBox(title, `${hints}\n\n目标分支：${state.target}`))) return;
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
