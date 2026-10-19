const $ = (id) => document.getElementById(id);
const logLines = [];
let pollTimer = 0;
let controlsOn = null;
let primed = false;

function log(text) {
  const stamp = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  logLines.unshift(`${stamp}  ${text}`);
  $("log").textContent = logLines.slice(0, 8).join("\n");
}

async function api(path, body) {
  const response = await fetch(path, body === undefined ? undefined : {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || `请求失败 ${response.status}`);
  }
  return data;
}

function setConnected(state) {
  const connected = Boolean(state.connected);
  if (controlsOn !== connected) {
    controlsOn = connected;
    $("disconnect").disabled = !connected;
    $("serial").disabled = connected;
    $("sim").disabled = connected;
    document.querySelectorAll("#controls button, #controls input").forEach((node) => {
      node.disabled = !connected;
    });
  }
  const link = $("link");
  link.className = "link";
  if (!connected) {
    link.textContent = "未连接";
    return;
  }
  if (state.error) {
    link.classList.add("bad");
    link.textContent = state.error;
    return;
  }
  if (state.moving) {
    link.classList.add("busy");
    link.textContent = state.mode === "sim" ? "仿真 · 转动中" : `${state.port} · 转动中`;
    return;
  }
  link.classList.add("on");
  link.textContent = state.mode === "sim" ? "仿真" : state.port;
}

function pulseOf(state) {
  if (state.angle == null || state.minUs == null || state.maxUs == null) {
    return null;
  }
  const cdeg = Math.round(state.angle * 100);
  return state.minUs + Math.floor((cdeg * (state.maxUs - state.minUs)) / 18000);
}

function render(state) {
  setConnected(state);
  const needle = $("needle");
  const angle = state.angle == null ? 90 : state.angle;
  needle.setAttribute("transform", `rotate(${angle - 90} 110 108)`);
  $("angle").textContent = state.angle == null ? "--" : state.angle.toFixed(2);
  const pulse = pulseOf(state);
  const target = state.target == null ? "--" : state.target.toFixed(2);
  $("target-line").textContent = `目标 ${target}°`;
  $("pulse-line").textContent = pulse == null ? "脉宽 --" : `脉宽 ${pulse} µs`;
  if (!primed && state.connected && state.angle != null) {
    primed = true;
    $("target").value = state.angle;
    $("slider").value = state.angle;
  }
  if (document.activeElement !== $("min-us") && state.minUs != null) {
    $("min-us").value = state.minUs;
  }
  if (document.activeElement !== $("max-us") && state.maxUs != null) {
    $("max-us").value = state.maxUs;
  }
}

async function refresh() {
  try {
    render(await api("/api/state"));
  } catch (error) {
    log(error.message);
  }
}

function startPoll() {
  stopPoll();
  pollTimer = setInterval(refresh, 200);
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = 0;
  }
}

function syncPair(source, other) {
  other.value = source.value;
}

async function move(angle, speed) {
  const target = Number(angle);
  const rate = Number(speed);
  if (!Number.isFinite(target) || target < 0 || target > 180) {
    log("角度要在 0 到 180 之间");
    return;
  }
  if (!Number.isFinite(rate) || rate < 0 || rate > 360) {
    log("速度要在 0 到 360 之间");
    return;
  }
  const state = await api("/api/move", { angle: target, speed: rate });
  render(state);
  log(`转到 ${target.toFixed(1)}°，速度 ${rate} °/s`);
}

async function loadPorts() {
  const data = await api("/api/ports");
  const options = data.ports.map((port) => {
    const safe = String(port).replace(/[&<>"']/g, (ch) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[ch]));
    return `<option value="${safe}"></option>`;
  });
  $("ports").innerHTML = options.join("");
  if (! $("port").value && data.ports.length) {
    $("port").value = data.ports[0];
  }
}

$("slider").addEventListener("input", () => syncPair($("slider"), $("target")));
$("target").addEventListener("input", () => syncPair($("target"), $("slider")));
$("speed-slider").addEventListener("input", () => syncPair($("speed-slider"), $("speed")));
$("speed").addEventListener("input", () => syncPair($("speed"), $("speed-slider")));

$("refresh").addEventListener("click", () => {
  loadPorts().then(() => log("已刷新串口")).catch((error) => log(error.message));
});

$("serial").addEventListener("click", async () => {
  try {
    const state = await api("/api/connect", { mode: "serial", port: $("port").value.trim() });
    render(state);
    if (state.angle != null) {
      $("target").value = state.angle;
      $("slider").value = state.angle;
    }
    startPoll();
    log(`已连接 ${state.port}`);
  } catch (error) {
    log(error.message);
    render(await api("/api/state"));
  }
});

$("sim").addEventListener("click", async () => {
  try {
    const state = await api("/api/connect", { mode: "sim" });
    render(state);
    $("target").value = state.angle;
    $("slider").value = state.angle;
    startPoll();
    log("已进入仿真");
  } catch (error) {
    log(error.message);
  }
});

$("disconnect").addEventListener("click", async () => {
  stopPoll();
  render(await api("/api/disconnect", {}));
  log("已断开");
});

$("go").addEventListener("click", () => {
  move($("target").value, $("speed").value).catch((error) => log(error.message));
});

$("target").addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    move($("target").value, $("speed").value).catch((error) => log(error.message));
  }
});

document.querySelectorAll("[data-preset]").forEach((button) => {
  button.addEventListener("click", () => {
    const angle = button.dataset.preset;
    $("target").value = angle;
    $("slider").value = angle;
    move(angle, $("speed").value).catch((error) => log(error.message));
  });
});

$("stop").addEventListener("click", async () => {
  try {
    const state = await api("/api/stop", {});
    render(state);
    log(`停在 ${state.angle.toFixed(2)}°`);
  } catch (error) {
    log(error.message);
  }
});

$("apply-cal").addEventListener("click", async () => {
  try {
    const state = await api("/api/config", {
      minUs: Number($("min-us").value),
      maxUs: Number($("max-us").value),
    });
    render(state);
    log(`脉宽 ${state.minUs}–${state.maxUs} µs`);
  } catch (error) {
    log(error.message);
  }
});

loadPorts().catch((error) => log(error.message));
refresh();
