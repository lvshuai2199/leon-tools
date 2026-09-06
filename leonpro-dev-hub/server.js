"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const net = require("net");
const { spawn, execFile } = require("child_process");

const ROOT = __dirname;
const PUBLIC = path.join(ROOT, "public");
const CONFIG_PATH = path.join(ROOT, "config.json");
const SETTINGS_PATH = path.join(ROOT, "settings.json");

const SERVICES = ["spring", "vue", "uni"];
const DEFAULT_SETTINGS = { db: "dev", frontendTarget: "local", skipBuild: false };

const config = JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8"));
const tunnelCfg = {
  mysqlLocal: 13306,
  redisLocal: 16379,
  apiLocal: 18089,
  ...(config.tunnel || {}),
};
const state = {
  settings: loadSettings(),
  services: Object.fromEntries(
    SERVICES.map((id) => [
      id,
      { id, status: "stopped", pid: null, startedAt: null, exitCode: null },
    ])
  ),
  children: {},
  tunnel: { status: "down", pid: null, child: null },
  deploys: Object.fromEntries(SERVICES.map((id) => [id, { status: "idle" }])),
  deployChild: null,
  logs: [],
  sseClients: new Set(),
};

function resolveAppPath(rel) {
  return path.resolve(ROOT, rel);
}

function loadSettings() {
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(fs.readFileSync(SETTINGS_PATH, "utf8")) };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

function saveSettings() {
  fs.writeFileSync(SETTINGS_PATH, JSON.stringify(state.settings, null, 2));
}

function now() {
  return new Date().toLocaleTimeString("zh-CN", { hour12: false });
}

function isNoisySpringLine(line) {
  return (
    /\sDEBUG\s/.test(line) ||
    line.includes("CONDITIONS EVALUATION REPORT") ||
    line.includes("Adding converter from class") ||
    line.includes("Positive matches:") ||
    line.includes("Negative matches:") ||
    line.includes("Did not match:") ||
    line.includes("Unconditional classes:")
  );
}

function stripAnsi(text) {
  return String(text).replace(/\u001b\[[0-9;]*m/g, "");
}

function appendLog(service, text, level = "info") {
  const lines = stripAnsi(text)
    .replace(/\r/g, "")
    .split("\n")
    .filter((line) => line.length)
    .filter((line) => service !== "spring" || !isNoisySpringLine(line));
  for (const line of lines) {
    const entry = { time: now(), service, level, line };
    state.logs.push(entry);
    if (state.logs.length > 800) state.logs.splice(0, state.logs.length - 800);
    broadcast({ type: "log", entry });
  }
}

function snapshot() {
  return {
    settings: state.settings,
    urls: {
      vue: `http://127.0.0.1:${config.ports.vue}`,
      uni: `http://127.0.0.1:${config.ports.uni}`,
      spring: config.urls.localApi,
      remoteApi: config.urls.remoteApi,
    },
    apiUrl: frontendApiUrl(),
    tunnel: {
      status: state.tunnel.status,
      mysql: `127.0.0.1:${tunnelCfg.mysqlLocal}`,
      redis: `127.0.0.1:${tunnelCfg.redisLocal}`,
      api: `127.0.0.1:${tunnelCfg.apiLocal}`,
    },
    ssh: publicSsh(),
    deploys: SERVICES.map((id) => ({ id, ...state.deploys[id] })),
    services: SERVICES.map((id) => ({ ...state.services[id], port: config.ports[id] })),
  };
}

function broadcast(payload) {
  const data = `data: ${JSON.stringify(payload)}\n\n`;
  for (const res of state.sseClients) {
    res.write(data);
  }
}

function frontendApiUrl() {
  if (state.settings.frontendTarget === "remote") {
    return `http://127.0.0.1:${tunnelCfg.apiLocal}`;
  }
  return config.urls.localApi;
}

function needsTunnel(id) {
  if (id === "spring") return true;
  return state.settings.frontendTarget === "remote" && (id === "vue" || id === "uni");
}

function parseDotEnv(file) {
  const out = {};
  if (!fs.existsSync(file)) return out;
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const idx = trimmed.indexOf("=");
    if (idx < 1) continue;
    out[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim();
  }
  return out;
}

function mergeDotEnv(files) {
  const merged = {};
  for (const file of files) {
    const parsed = parseDotEnv(file);
    for (const [key, value] of Object.entries(parsed)) {
      if (value === "" && merged[key]) continue;
      if (value !== "" || merged[key] === undefined) merged[key] = value;
    }
  }
  return merged;
}

function loadSshConfig() {
  const springDir = resolveAppPath(config.paths.spring);
  const files = [
    path.join(springDir, "deploy", "deploy.env"),
    path.resolve(ROOT, "../personal-server/bootstrap/bootstrap.env"),
    path.resolve(ROOT, "../personal-server/LeonPro_frontend/frontend_phone/deploy/deploy.env"),
    path.resolve(ROOT, "../personal-server/LeonPro_frontend/vue3_frontend/deploy/deploy.env"),
  ];
  const merged = mergeDotEnv(files);
  return {
    host: merged.DEPLOY_HOST || config.urls.dbHost,
    user: merged.DEPLOY_USER || "ubuntu",
    port: merged.DEPLOY_PORT || "22",
    password: merged.DEPLOY_PASSWORD || "",
    key: merged.DEPLOY_SSH_KEY || "",
    mysqlPassword: merged.MYSQL_PASSWORD || merged.MYSQL_ROOT_PASSWORD || "",
    askpass: path.join(springDir, "deploy", "askpass.cmd"),
  };
}

function resolveSshBin() {
  const winSsh = path.join(process.env.SystemRoot || "C:\\Windows", "System32", "OpenSSH", "ssh.exe");
  return fs.existsSync(winSsh) ? winSsh : "ssh";
}

function publicSsh() {
  const ssh = loadSshConfig();
  const remotes = {};
  for (const id of SERVICES) {
    const dir = resolveAppPath(config.paths[id]);
    const env = parseDotEnv(path.join(dir, "deploy", "deploy.env"));
    remotes[id] = {
      dir: env.DEPLOY_REMOTE_DIR || "",
      ready: fs.existsSync(path.join(dir, "deploy", "deploy.ps1")),
    };
  }
  return {
    host: ssh.host || "",
    user: ssh.user || "",
    port: String(ssh.port || "22"),
    auth: ssh.key ? "key" : ssh.password ? "password" : "missing",
    remotes,
  };
}

function applySshAuth(args, env, ssh) {
  if (ssh.key) {
    args.push("-i", ssh.key, "-o", "IdentitiesOnly=yes");
    return;
  }
  args.push("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no");
  env.DEPLOY_PASSWORD = ssh.password;
  env.SSH_ASKPASS = ssh.askpass;
  env.SSH_ASKPASS_REQUIRE = "force";
  env.DISPLAY = "127.0.0.1:0";
}

function requireSshLogin(ssh) {
  if (!ssh.host || !ssh.user) {
    throw new Error("缺少 SSH 主机/用户。请填写 SpringBoot/deploy/deploy.env 或 bootstrap.env");
  }
  if (!ssh.key && !ssh.password) {
    throw new Error("未找到 SSH 密码或密钥。请至少填一处 DEPLOY_PASSWORD 或 DEPLOY_SSH_KEY");
  }
}

function testSsh() {
  const ssh = loadSshConfig();
  requireSshLogin(ssh);
  const args = [
    "-p",
    String(ssh.port),
    "-o",
    "StrictHostKeyChecking=accept-new",
    "-o",
    "NumberOfPasswordPrompts=1",
    "-o",
    "ConnectTimeout=12",
  ];
  const env = { ...process.env };
  applySshAuth(args, env, ssh);
  args.push(`${ssh.user}@${ssh.host}`, "echo", "leonpro-ssh-ok");

  return new Promise((resolve, reject) => {
    appendLog("hub", `测试 SSH ${ssh.user}@${ssh.host} …`);
    const child = spawn(resolveSshBin(), args, {
      env,
      windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let out = "";
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error("SSH 测试超时"));
    }, 20000);
    child.stdout.on("data", (chunk) => {
      out += chunk.toString();
      appendLog("hub", chunk.toString());
    });
    child.stderr.on("data", (chunk) => appendLog("hub", chunk.toString(), "warn"));
    child.on("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });
    child.on("exit", (code) => {
      clearTimeout(timer);
      if (code === 0 && out.includes("leonpro-ssh-ok")) {
        appendLog("hub", "SSH 测试成功");
        resolve();
        return;
      }
      reject(new Error(`SSH 测试失败，code=${code}`));
    });
  });
}

function isDeployBusy() {
  return SERVICES.some((id) => state.deploys[id].status === "running");
}

function startDeploy(id, skipBuild) {
  return new Promise((resolve, reject) => {
    const cwd = resolveAppPath(config.paths[id]);
    const script = path.join(cwd, "deploy", "deploy.ps1");
    if (!fs.existsSync(script)) {
      reject(new Error(`${id} 没有 deploy/deploy.ps1`));
      return;
    }
    const ssh = loadSshConfig();
    requireSshLogin(ssh);
    appendLog(id, `开始部署到服务器${skipBuild ? "（跳过构建）" : ""}…`);
    state.deploys[id] = { status: "running", startedAt: Date.now() };
    broadcast({ type: "state", state: snapshot() });

    const child = spawn(
      "powershell.exe",
      ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script],
      {
        cwd,
        env: { ...process.env, HUB_SKIP_BUILD: skipBuild ? "1" : "0" },
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"],
      }
    );
    state.deployChild = child;
    child.stdout.on("data", (chunk) => appendLog(id, chunk.toString()));
    child.stderr.on("data", (chunk) => appendLog(id, chunk.toString(), "warn"));
    child.on("error", (err) => {
      state.deploys[id] = { status: "error" };
      state.deployChild = null;
      appendLog(id, err.message, "error");
      broadcast({ type: "state", state: snapshot() });
      reject(err);
    });
    child.on("exit", (code) => {
      state.deployChild = null;
      state.deploys[id] = { status: code === 0 ? "ok" : "error" };
      appendLog(id, code === 0 ? "部署完成" : `部署失败，code=${code}`, code === 0 ? "info" : "error");
      broadcast({ type: "state", state: snapshot() });
      if (code === 0) resolve();
      else reject(new Error(`${id} 部署失败，code=${code}`));
    });
  });
}

async function runDeploys(ids, skipBuild) {
  for (const id of ids) {
    await startDeploy(id, skipBuild);
  }
}

function serviceEnv(service) {
  const env = { ...process.env };
  env.VITE_APP_API_URL = frontendApiUrl();
  env.UNI_API_URL = frontendApiUrl();
  env.BROWSER = "none";
  env.FORCE_COLOR = "1";
  if (service === "spring") {
    const ssh = loadSshConfig();
    const profile = state.settings.db === "prod" ? "prod" : "dev";
    const dbName = profile === "prod" ? "leonpro_db_prod" : "leonpro_db_dev";
    const jdbc =
      `jdbc:mysql://127.0.0.1:${tunnelCfg.mysqlLocal}/${dbName}` +
      "?useSSL=false&useUnicode=true&characterEncoding=utf-8&autoReconnect=true&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true";
    env.SPRING_PROFILES_ACTIVE = profile;
    env.SPRING_DATASOURCE_URL = jdbc;
    env.SPRING_DATA_REDIS_URL = `redis://127.0.0.1:${tunnelCfg.redisLocal}`;
    env.MYSQL_HOST = "127.0.0.1";
    env.MYSQL_PORT = String(tunnelCfg.mysqlLocal);
    env.REDIS_HOST = "127.0.0.1";
    env.REDIS_PORT = String(tunnelCfg.redisLocal);
    env.SPRING_DEVTOOLS_ADD_PROPERTIES = "false";
    env.LOGGING_LEVEL_ROOT = "INFO";
    env.LOGGING_LEVEL_ORG_SPRINGFRAMEWORK = "INFO";
    if (ssh.mysqlPassword) env.MYSQL_PASSWORD = ssh.mysqlPassword;
  }
  return env;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitPort(port, timeoutMs) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await isPortOpen(port)) return true;
    await sleep(250);
  }
  return false;
}

async function ensureTunnel() {
  if (await isPortOpen(tunnelCfg.mysqlLocal)) {
    state.tunnel.status = "up";
    appendLog("hub", `复用 SSH 隧道 127.0.0.1:${tunnelCfg.mysqlLocal} -> 服务器 3306`);
    return;
  }

  const ssh = loadSshConfig();
  requireSshLogin(ssh);

  const args = [
    "-N",
    "-L",
    `${tunnelCfg.mysqlLocal}:127.0.0.1:3306`,
    "-L",
    `${tunnelCfg.redisLocal}:127.0.0.1:6379`,
    "-L",
    `${tunnelCfg.apiLocal}:127.0.0.1:8089`,
    "-p",
    String(ssh.port),
    "-o",
    "StrictHostKeyChecking=accept-new",
    "-o",
    "ExitOnForwardFailure=yes",
    "-o",
    "ServerAliveInterval=30",
    "-o",
    "NumberOfPasswordPrompts=1",
  ];
  const env = { ...process.env };
  applySshAuth(args, env, ssh);
  args.push(`${ssh.user}@${ssh.host}`);

  const sshBin = resolveSshBin();
  appendLog("hub", `正在建立 SSH 隧道 ${ssh.user}@${ssh.host}（${ssh.key ? "密钥" : "密码"}）…`);
  state.tunnel.status = "starting";
  broadcast({ type: "state", state: snapshot() });

  const child = spawn(sshBin, args, {
    env,
    windowsHide: true,
    detached: true,
    stdio: ["ignore", "pipe", "pipe"],
  });
  state.tunnel.child = child;
  state.tunnel.pid = child.pid;
  child.stdout.on("data", (chunk) => appendLog("hub", chunk.toString()));
  child.stderr.on("data", (chunk) => appendLog("hub", chunk.toString(), "warn"));
  child.on("exit", (code) => {
    if (state.tunnel.child === child) {
      state.tunnel = { status: "down", pid: null, child: null };
      appendLog("hub", `SSH 隧道已断开，code=${code}`, code === 0 ? "info" : "warn");
      broadcast({ type: "state", state: snapshot() });
    }
  });

  const ok = await waitPort(tunnelCfg.mysqlLocal, 20000);
  if (!ok) {
    await stopTunnel();
    throw new Error("SSH 隧道建立失败：本机 13306 未监听。检查 SSH 账号或密钥");
  }
  state.tunnel.status = "up";
  appendLog(
    "hub",
    `隧道已就绪：MySQL :${tunnelCfg.mysqlLocal}，Redis :${tunnelCfg.redisLocal}，API :${tunnelCfg.apiLocal}`
  );
}

async function stopTunnel() {
  const child = state.tunnel.child;
  const pids = new Set();
  if (child?.pid) pids.add(child.pid);
  if (state.tunnel.pid) pids.add(state.tunnel.pid);
  for (const pid of await findPidsOnPort(tunnelCfg.mysqlLocal)) pids.add(pid);
  for (const pid of pids) await taskkill(pid);
  state.tunnel = { status: "down", pid: null, child: null };
}

function isPortOpen(port) {
  return new Promise((resolve) => {
    const socket = net.connect({ port, host: "127.0.0.1" }, () => {
      socket.end();
      resolve(true);
    });
    socket.setTimeout(400);
    socket.on("timeout", () => {
      socket.destroy();
      resolve(false);
    });
    socket.on("error", () => resolve(false));
  });
}

function findPidsOnPort(port) {
  return new Promise((resolve) => {
    execFile("netstat", ["-ano"], { windowsHide: true }, (err, stdout) => {
      if (err || !stdout) return resolve([]);
      const pids = new Set();
      const needle = `:${port}`;
      for (const raw of stdout.split(/\r?\n/)) {
        if (!raw.includes(needle) || !raw.includes("LISTENING")) continue;
        const parts = raw.trim().split(/\s+/);
        const pid = Number(parts[parts.length - 1]);
        if (pid > 0) pids.add(pid);
      }
      resolve([...pids]);
    });
  });
}

function taskkill(pid) {
  return new Promise((resolve) => {
    execFile("taskkill", ["/PID", String(pid), "/T", "/F"], { windowsHide: true }, () => resolve());
  });
}

async function killService(id) {
  const child = state.children[id];
  const pids = new Set();
  if (child?.pid) pids.add(child.pid);
  if (state.services[id].pid) pids.add(state.services[id].pid);
  for (const pid of await findPidsOnPort(config.ports[id])) pids.add(pid);
  for (const pid of pids) await taskkill(pid);
  delete state.children[id];
  Object.assign(state.services[id], { status: "stopped", pid: null, startedAt: null });
}

function frontendCommand(id, cwd) {
  const viteJs = path.join(cwd, "node_modules", "vite", "bin", "vite.js");
  if (!fs.existsSync(viteJs)) {
    const hint = id === "vue" ? "vue3_frontend 执行 pnpm install" : "frontend_phone 执行 npm install";
    throw new Error("未找到 vite，请先在 " + hint);
  }
  return { file: process.execPath, args: [viteJs] };
}

async function startService(id) {
  if (state.services[id].status === "running") {
    appendLog(id, "已在运行");
    return;
  }
  if (state.services[id].status === "starting") {
    appendLog(id, "仍在启动中。若一直无日志，请先点停止再启动");
    return;
  }

  const cwd = resolveAppPath(config.paths[id]);
  if (!fs.existsSync(cwd)) {
    appendLog(id, `目录不存在：${cwd}`, "error");
    state.services[id].status = "error";
    broadcast({ type: "state", state: snapshot() });
    return;
  }

  if (needsTunnel(id)) {
    Object.assign(state.services[id], { status: "starting", startedAt: Date.now() });
    broadcast({ type: "state", state: snapshot() });
    try {
      await ensureTunnel();
    } catch (err) {
      appendLog(id, err.message, "error");
      Object.assign(state.services[id], { status: "error", pid: null });
      broadcast({ type: "state", state: snapshot() });
      return;
    }
  }

  let command;
  try {
    if (id === "spring") {
      command = {
        file: "powershell.exe",
        args: ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", path.join(cwd, "run-dev.ps1")],
      };
    } else {
      command = frontendCommand(id, cwd);
    }
  } catch (err) {
    appendLog(id, err.message, "error");
    Object.assign(state.services[id], { status: "error", pid: null });
    broadcast({ type: "state", state: snapshot() });
    return;
  }

  appendLog(
    id,
    `启动中… cwd=${cwd}` +
      (id === "spring"
        ? ` db=${state.settings.db} via 127.0.0.1:${tunnelCfg.mysqlLocal}`
        : ` api=${frontendApiUrl()}`)
  );

  const child = spawn(command.file, command.args, {
    cwd,
    env: serviceEnv(id),
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"],
  });

  state.children[id] = child;
  Object.assign(state.services[id], {
    status: "starting",
    pid: child.pid,
    startedAt: Date.now(),
    exitCode: null,
  });

  const onChunk = (chunk, level) => appendLog(id, chunk.toString(), level);
  child.stdout.on("data", (chunk) => onChunk(chunk, "info"));
  child.stderr.on("data", (chunk) => onChunk(chunk, "warn"));

  child.on("error", (err) => {
    appendLog(id, err.message, "error");
    Object.assign(state.services[id], { status: "error", pid: null });
    delete state.children[id];
    broadcast({ type: "state", state: snapshot() });
  });

  child.on("exit", (code) => {
    if (state.children[id] === child) {
      delete state.children[id];
      if (state.services[id].status !== "stopped") {
        Object.assign(state.services[id], {
          status: code === 0 ? "stopped" : "error",
          pid: null,
          exitCode: code,
        });
        appendLog(id, `进程退出，code=${code}`, code === 0 ? "info" : "error");
        broadcast({ type: "state", state: snapshot() });
      }
    }
  });

  broadcast({ type: "state", state: snapshot() });
}

async function refreshPorts() {
  let changed = false;
  for (const id of SERVICES) {
    const svc = state.services[id];
    const open = await isPortOpen(config.ports[id]);
    if (open && svc.status !== "running") {
      svc.status = "running";
      changed = true;
    } else if (!open && svc.status === "running") {
      svc.status = state.children[id] ? "starting" : "stopped";
      if (!state.children[id]) svc.pid = null;
      changed = true;
    }
  }
  if (changed) broadcast({ type: "state", state: snapshot() });
}

function sendJson(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(json),
  });
  res.end(json);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (err) {
        reject(err);
      }
    });
    req.on("error", reject);
  });
}

function contentType(file) {
  if (file.endsWith(".css")) return "text/css; charset=utf-8";
  if (file.endsWith(".js")) return "text/javascript; charset=utf-8";
  if (file.endsWith(".svg")) return "image/svg+xml";
  return "text/html; charset=utf-8";
}

function serveStatic(req, res) {
  const url = new URL(req.url, "http://127.0.0.1");
  let file = url.pathname === "/" ? "/index.html" : url.pathname;
  const abs = path.normalize(path.join(PUBLIC, file));
  if (!abs.startsWith(PUBLIC)) {
    res.writeHead(403);
    res.end();
    return;
  }
  fs.readFile(abs, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end("Not found");
      return;
    }
    res.writeHead(200, { "Content-Type": contentType(abs) });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://127.0.0.1");

  if (req.method === "GET" && url.pathname === "/api/events") {
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });
    res.write(`data: ${JSON.stringify({ type: "state", state: snapshot() })}\n\n`);
    for (const entry of state.logs.slice(-200)) {
      res.write(`data: ${JSON.stringify({ type: "log", entry })}\n\n`);
    }
    state.sseClients.add(res);
    req.on("close", () => state.sseClients.delete(res));
    return;
  }

  try {
    if (req.method === "GET" && url.pathname === "/api/state") {
      return sendJson(res, 200, snapshot());
    }

    if (req.method === "POST" && url.pathname === "/api/settings") {
      const body = await readBody(req);
      if (body.db === "dev" || body.db === "prod") state.settings.db = body.db;
      if (body.frontendTarget === "local" || body.frontendTarget === "remote") {
        state.settings.frontendTarget = body.frontendTarget;
      }
      if (typeof body.skipBuild === "boolean") state.settings.skipBuild = body.skipBuild;
      saveSettings();
      appendLog("hub", `设置已更新：数据库=${state.settings.db}，前端接口=${state.settings.frontendTarget}`);
      broadcast({ type: "state", state: snapshot() });
      return sendJson(res, 200, snapshot());
    }

    if (req.method === "POST" && url.pathname === "/api/start") {
      const body = await readBody(req);
      const targets = body.service === "all" ? SERVICES : [body.service];
      if (targets.some((id) => !SERVICES.includes(id))) {
        return sendJson(res, 400, { error: "未知服务" });
      }
      for (const id of targets) await startService(id);
      return sendJson(res, 200, snapshot());
    }

    if (req.method === "POST" && url.pathname === "/api/stop") {
      const body = await readBody(req);
      const targets = body.service === "all" ? SERVICES : [body.service];
      if (targets.some((id) => !SERVICES.includes(id))) {
        return sendJson(res, 400, { error: "未知服务" });
      }
      for (const id of targets) {
        appendLog(id, "正在停止…");
        await killService(id);
        appendLog(id, "已停止");
      }
      if (body.service === "all") await stopTunnel();
      broadcast({ type: "state", state: snapshot() });
      return sendJson(res, 200, snapshot());
    }

    if (req.method === "POST" && url.pathname === "/api/tunnel/start") {
      await ensureTunnel();
      broadcast({ type: "state", state: snapshot() });
      return sendJson(res, 200, snapshot());
    }

    if (req.method === "POST" && url.pathname === "/api/tunnel/stop") {
      await stopTunnel();
      appendLog("hub", "SSH 隧道已断开");
      broadcast({ type: "state", state: snapshot() });
      return sendJson(res, 200, snapshot());
    }

    if (req.method === "POST" && url.pathname === "/api/ssh/test") {
      await testSsh();
      broadcast({ type: "state", state: snapshot() });
      return sendJson(res, 200, { ok: true, ssh: publicSsh() });
    }

    if (req.method === "POST" && url.pathname === "/api/deploy") {
      const body = await readBody(req);
      const targets = body.service === "all" ? SERVICES : [body.service];
      if (targets.some((id) => !SERVICES.includes(id))) {
        return sendJson(res, 400, { error: "未知服务" });
      }
      if (isDeployBusy()) {
        return sendJson(res, 409, { error: "已有部署在进行，请等当前任务结束" });
      }
      const skipBuild = !!(body.skipBuild ?? state.settings.skipBuild);
      runDeploys(targets, skipBuild).catch((err) => appendLog("hub", err.message, "error"));
      return sendJson(res, 200, snapshot());
    }

    if (req.method === "POST" && url.pathname === "/api/logs/clear") {
      state.logs.length = 0;
      broadcast({ type: "clear-logs" });
      return sendJson(res, 200, { ok: true });
    }

    if (req.method === "GET" && !url.pathname.startsWith("/api/")) {
      return serveStatic(req, res);
    }

    sendJson(res, 404, { error: "not found" });
  } catch (err) {
    sendJson(res, 500, { error: err.message });
  }
});

async function shutdown() {
  for (const id of SERVICES) {
    await killService(id);
  }
  await stopTunnel();
  server.close();
  process.exit(0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

server.listen(config.hubPort, "127.0.0.1", () => {
  appendLog("hub", `启动台 v2 已打开：http://127.0.0.1:${config.hubPort}（SSH 隧道 + 直跑 Vite）`);
  console.log(`LeonPro Dev Hub  http://127.0.0.1:${config.hubPort}`);
});

setInterval(refreshPorts, 2000).unref();
