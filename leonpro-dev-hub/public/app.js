const names = {
  spring: "Spring Boot",
  vue: "Vue",
  uni: "手机 H5",
};

const authNames = {
  password: "密码",
  key: "密钥",
  missing: "未配置",
};

const viewSubs = {
  local: "本机启停 Spring / Vue / 手机 H5",
  remote: "SSH 隧道，以及按各项目 deploy.env 部署到服务器",
};

const logsEl = document.getElementById("logs");
const servicesEl = document.getElementById("services");
const apiHint = document.getElementById("apiHint");
const skipBuildEl = document.getElementById("skipBuild");
const stageTrack = document.getElementById("stageTrack");
const viewSub = document.getElementById("viewSub");

let current = null;
let currentView = localStorage.getItem("hubView") === "remote" ? "remote" : "local";

async function post(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body || {}),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || res.statusText);
  }
  return res.json();
}

function setView(view) {
  currentView = view === "remote" ? "remote" : "local";
  localStorage.setItem("hubView", currentView);
  stageTrack.dataset.view = currentView;
  viewSub.textContent = viewSubs[currentView];
  document.querySelectorAll("#viewSwitch [data-view]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.view === currentView);
  });
  document.querySelectorAll(".view-only").forEach((el) => {
    el.classList.toggle("hidden", el.dataset.for !== currentView);
  });
}

function render(state) {
  current = state;
  document.querySelectorAll("[data-group]").forEach((group) => {
    const key = group.dataset.group;
    group.querySelectorAll("button").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.value === state.settings[key]);
    });
  });
  skipBuildEl.checked = !!state.settings.skipBuild;
  apiHint.textContent = `当前前端代理：${state.apiUrl}`;

  const ssh = state.ssh || {};
  const sshHint = document.getElementById("sshHint");
  sshHint.textContent = ssh.host
    ? `${ssh.user}@${ssh.host}:${ssh.port} · ${authNames[ssh.auth] || ssh.auth}`
    : "未读到 SSH 主机";
  sshHint.className = `target ${ssh.auth === "missing" ? "bad" : ""}`;

  const tunnelEl = document.getElementById("tunnelHint");
  if (tunnelEl && state.tunnel) {
    tunnelEl.textContent = `隧道 ${state.tunnel.status} · MySQL ${state.tunnel.mysql} · Redis ${state.tunnel.redis} · API ${state.tunnel.api || ""}`;
  }

  const deployById = Object.fromEntries((state.deploys || []).map((item) => [item.id, item]));
  const remotes = ssh.remotes || {};
  document.getElementById("deployList").innerHTML = Object.keys(names)
    .map((id) => {
      const remote = remotes[id] || {};
      const deploy = deployById[id] || { status: "idle" };
      return `<div class="deploy-row">
        <div>
          <strong>${names[id]}</strong>
          <div class="hint">${remote.dir || "未配置 DEPLOY_REMOTE_DIR"} · ${deploy.status}</div>
        </div>
        <button class="btn" data-deploy="${id}" ${deploy.status === "running" ? "disabled" : ""}>部署</button>
      </div>`;
    })
    .join("");

  servicesEl.innerHTML = state.services
    .map((svc) => {
      const href = state.urls[svc.id];
      return `
        <article class="svc">
          <h2>${names[svc.id]}</h2>
          <div class="status ${svc.status}">状态：${svc.status}${svc.pid ? ` · pid ${svc.pid}` : ""}</div>
          <div>端口 ${svc.port}${href ? ` · <a href="${href}" target="_blank">打开</a>` : ""}</div>
          <div class="svc-actions">
            <button class="btn primary" data-start="${svc.id}">启动</button>
            <button class="btn" data-stop="${svc.id}">停止</button>
          </div>
        </article>`;
    })
    .join("");
}

function addLog(entry) {
  const row = document.createElement("div");
  row.className = entry.level === "error" ? "log-error" : entry.level === "warn" ? "log-warn" : "";
  row.textContent = `[${entry.time}] [${entry.service}] ${entry.line}`;
  logsEl.appendChild(row);
  logsEl.scrollTop = logsEl.scrollHeight;
}

document.querySelectorAll("[data-group] button").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const group = btn.closest("[data-group]").dataset.group;
    await post("/api/settings", { [group]: btn.dataset.value });
  });
});

document.querySelectorAll("#viewSwitch [data-view]").forEach((btn) => {
  btn.addEventListener("click", () => setView(btn.dataset.view));
});

skipBuildEl.addEventListener("change", () => {
  post("/api/settings", { skipBuild: skipBuildEl.checked });
});

document.getElementById("startAll").addEventListener("click", () => post("/api/start", { service: "all" }));
document.getElementById("stopAll").addEventListener("click", () => post("/api/stop", { service: "all" }));
document.getElementById("clearLogs").addEventListener("click", () => post("/api/logs/clear"));
document.getElementById("sshTest").addEventListener("click", () => post("/api/ssh/test").catch((err) => alert(err.message)));
document.getElementById("tunnelStart").addEventListener("click", () => post("/api/tunnel/start").catch((err) => alert(err.message)));
document.getElementById("tunnelStop").addEventListener("click", () => post("/api/tunnel/stop"));
document.getElementById("deployAll").addEventListener("click", () => {
  post("/api/deploy", { service: "all", skipBuild: skipBuildEl.checked }).catch((err) => alert(err.message));
});

document.getElementById("deployList").addEventListener("click", (event) => {
  const id = event.target.dataset.deploy;
  if (id) post("/api/deploy", { service: id, skipBuild: skipBuildEl.checked }).catch((err) => alert(err.message));
});

servicesEl.addEventListener("click", (event) => {
  const start = event.target.dataset.start;
  const stop = event.target.dataset.stop;
  if (start) post("/api/start", { service: start });
  if (stop) post("/api/stop", { service: stop });
});

const events = new EventSource("/api/events");
events.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  if (msg.type === "state") render(msg.state);
  if (msg.type === "log") addLog(msg.entry);
  if (msg.type === "clear-logs") logsEl.replaceChildren();
};

setView(currentView);

fetch("/api/state")
  .then((res) => res.json())
  .then(render);
