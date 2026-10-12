"use strict";

const $ = (id) => document.getElementById(id);
const buttons = ["sync", "push"];
let pollTimer = null;

function val(id) {
  return $(id).value.trim();
}

function provider() {
  return document.querySelector("#provider button.active")?.dataset.value || "gitee";
}

function collect() {
  return {
    provider: provider(),
    repoPath: val("repoPath"),
    remote: "origin",
    branch: val("branch") || "master",
    imageDir: val("imageDir") || "imgs",
    watchText: $("watchText").value,
    docText: $("docText").value,
    clearMissingLinks: $("clearMissing").checked,
    schedule: {
      enabled: $("scheduleOn").checked,
      intervalMinutes: Number(val("interval")) || 30,
    },
    push: {
      branch: val("pushBranch"),
      directory: val("pushDir"),
      message: "docs: 更新图床链接",
    },
  };
}

function setProvider(name) {
  for (const button of document.querySelectorAll("#provider button")) {
    button.classList.toggle("active", button.dataset.value === name);
  }
}

function fill(state) {
  const config = state.config;
  setProvider(config.provider || "gitee");
  $("repoPath").value = config.repoPath || "";
  $("branch").value = config.branch || "master";
  $("imageDir").value = config.imageDir || "imgs";
  $("watchText").value = (config.watchFolders || []).map((item) => item.path).join("\n");
  $("docText").value = (config.docFolders || []).map((item) => item.path).join("\n");
  $("clearMissing").checked = config.clearMissingLinks !== false;
  $("scheduleOn").checked = Boolean(config.schedule?.enabled);
  $("interval").value = config.schedule?.intervalMinutes || 30;
  $("pushBranch").value = config.push?.branch || "";
  $("pushDir").value = config.push?.directory || "";
}

function renderStatus(state) {
  if (state.hostError) {
    $("identity").textContent = state.hostError;
    $("identity").className = "status bad";
  } else if (state.identity) {
    $("identity").textContent = state.imageNote
      ? `${state.identity.owner}/${state.identity.repo} · ${state.imageNote}`
      : `${state.identity.owner}/${state.identity.repo}`;
    $("identity").className = "status ok";
  } else {
    $("identity").textContent = "还没有识别到 origin";
    $("identity").className = "status";
  }
  const run = state.run || {};
  const next = run.nextRunAt ? new Date(run.nextRunAt).toLocaleString() : "未排程";
  const last = run.lastRunAt ? new Date(run.lastRunAt).toLocaleString() : "还没有";
  $("runInfo").textContent = run.lastError
    ? `上次 ${last} 失败：${run.lastError}`
    : `上次 ${last}，下次 ${next}`;
}

function formatSize(size) {
  const value = Number(size) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function renderImages(state) {
  const images = state.images || [];
  $("imageCount").textContent = images.length ? `${images.length}` : "";
  const body = $("images");
  body.replaceChildren();
  if (!images.length) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 3;
    cell.className = "empty";
    cell.textContent = "还没有图片";
    row.append(cell);
    body.append(row);
    return;
  }
  for (const image of images) {
    const row = document.createElement("tr");
    const name = document.createElement("td");
    name.textContent = image.name || image.path;
    const info = document.createElement("td");
    info.textContent = `${formatSize(image.size)} ${image.width || 0}x${image.height || 0}`;
    const link = document.createElement("td");
    if (image.url) {
      const wrap = document.createElement("div");
      wrap.className = "link";
      const code = document.createElement("code");
      code.textContent = image.url;
      const copy = document.createElement("button");
      copy.type = "button";
      copy.className = "copy";
      copy.textContent = "复制";
      copy.addEventListener("click", () => navigator.clipboard.writeText(image.url));
      wrap.append(code, copy);
      link.append(wrap);
    }
    row.append(name, info, link);
    body.append(row);
  }
}

function renderJob(current) {
  const lines = current?.logs || [];
  $("log").textContent = lines.length ? lines.join("\n") : "等待操作";
  $("log").scrollTop = $("log").scrollHeight;
  const running = current?.status === "running";
  for (const id of buttons) $(id).disabled = running;
  for (const button of document.querySelectorAll("#provider button")) button.disabled = running;
}

async function api(url, options) {
  const response = await fetch(url, {
    method: options?.method || "GET",
    headers: { "Content-Type": "application/json" },
    body: options?.body ? JSON.stringify(options.body) : undefined,
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || response.statusText);
  return data;
}

async function load(options = {}) {
  const [state, current] = await Promise.all([api("/api/state"), api("/api/job")]);
  if (options.form !== false) fill(state);
  renderStatus(state);
  renderImages(state);
  renderJob(current);
  if (current.status === "running") poll();
}

function poll() {
  clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    try {
      const current = await api("/api/job");
      renderJob(current);
      if (current.status !== "running") {
        clearInterval(pollTimer);
        await load({ form: false });
      }
    } catch (err) {
      $("log").textContent = err.message;
    }
  }, 700);
}

async function save() {
  await api("/api/config", { method: "PUT", body: collect() });
}

async function run(path, body) {
  await save();
  const current = await api(path, { method: "POST", body: body || {} });
  renderJob(current);
  poll();
}

function showError(err) {
  $("log").textContent = err.message || String(err);
  for (const id of buttons) $(id).disabled = false;
}

for (const button of document.querySelectorAll("#provider button")) {
  button.addEventListener("click", () => setProvider(button.dataset.value));
}
$("sync").addEventListener("click", () => run("/api/sync", { dryRun: false }).catch(showError));
$("push").addEventListener("click", () => run("/api/push").catch(showError));

load().catch(showError);
