"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");
const { EXTS } = require("./lib/image-info");
const { createService } = require("./lib/service");
const { scheduleDue } = require("./lib/store");

const DOC_EXTS = new Set([".md", ".markdown", ".html", ".htm"]);

const ROOT = __dirname;
const PUBLIC = path.resolve(ROOT, "public");
const service = createService(ROOT);
const store = service.store;

let job = { id: 0, type: "", status: "idle", logs: [], summary: null };

function send(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": "no-store",
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > 1024 * 1024) {
        reject(new Error("请求过大"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      if (!chunks.length) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(new Error("JSON 无法解析"));
      }
    });
    req.on("error", reject);
  });
}

function startJob(type, fn) {
  if (job.status === "running") {
    const err = new Error("已有任务在运行");
    err.statusCode = 409;
    throw err;
  }
  const current = {
    id: Date.now(),
    type,
    status: "running",
    logs: [],
    summary: null,
    startedAt: new Date().toISOString(),
    finishedAt: "",
  };
  job = current;
  const log = (line) => {
    current.logs.push(String(line));
    if (current.logs.length > 300) current.logs.shift();
  };
  Promise.resolve()
    .then(() => fn(log))
    .then((summary) => {
      current.status = "done";
      current.summary = summary || null;
      current.finishedAt = new Date().toISOString();
    })
    .catch((err) => {
      current.status = "error";
      log(err.message || String(err));
      current.finishedAt = new Date().toISOString();
    });
  return current;
}

let watchers = [];
let watchTimer = null;
let watchAgain = false;

function watchedPath(filename, kind, imageDir) {
  const parts = String(filename || "").split(/[/\\]/);
  if (!parts[0] || parts.includes(".git") || parts.includes("node_modules") || parts.includes(imageDir)) return false;
  const ext = path.extname(parts[parts.length - 1]).toLowerCase();
  if (kind === "img") return EXTS.has(ext);
  return DOC_EXTS.has(ext);
}

function stopWatchers() {
  for (const watcher of watchers) watcher.close();
  watchers = [];
}

function pokeWatch() {
  if (job.status === "running") {
    watchAgain = true;
    return;
  }
  clearTimeout(watchTimer);
  watchTimer = setTimeout(() => runAuto("监听到文件变化，开始推送"), 8000);
}

function startWatchers(config) {
  stopWatchers();
  clearTimeout(watchTimer);
  if (!config.schedule?.enabled) return;
  const dirs = [
    ...config.watchFolders.map((folder) => ({ ...folder, kind: "img" })),
    ...config.docFolders.map((folder) => ({ ...folder, kind: "doc" })),
  ];
  for (const folder of dirs) {
    if (!folder.path || !fs.existsSync(folder.path)) continue;
    try {
      const watcher = fs.watch(folder.path, { recursive: true }, (_event, filename) => {
        if (!watchedPath(filename, folder.kind, config.imageDir || "imgs")) return;
        pokeWatch();
      });
      watcher.on("error", () => {});
      watchers.push(watcher);
    } catch {
      // 目录暂时不可监听时，定时兜底仍会跑
    }
  }
}

function runAuto(reason) {
  if (job.status === "running") {
    watchAgain = true;
    return;
  }
  try {
    startJob("publish", async (log) => {
      log(reason);
      try {
        const summary = await service.publish({ log });
        store.saveRun({ lastRunAt: new Date().toISOString(), lastError: "" });
        return summary;
      } catch (err) {
        store.saveRun({ lastRunAt: new Date().toISOString(), lastError: err.message });
        throw err;
      } finally {
        if (watchAgain) {
          watchAgain = false;
          setTimeout(() => pokeWatch(), 2000);
        }
      }
    });
  } catch {
    watchAgain = true;
  }
}

function armSchedule(prev, next) {
  const run = store.loadRun();
  const changed =
    prev.schedule.enabled !== next.schedule.enabled || prev.schedule.intervalMinutes !== next.schedule.intervalMinutes;
  startWatchers(next);
  if (!next.schedule.enabled) {
    if (run.nextRunAt) store.saveRun({ nextRunAt: 0 });
    return;
  }
  if (changed || !run.nextRunAt) {
    store.saveRun({ nextRunAt: Date.now() + next.schedule.intervalMinutes * 60000 });
  }
}

function publicFile(urlPath) {
  const raw = decodeURIComponent(urlPath.split("?")[0]);
  const rel = raw === "/" ? "index.html" : raw.replace(/^\/+/, "");
  const abs = path.resolve(PUBLIC, rel);
  if (abs !== PUBLIC && !abs.startsWith(`${PUBLIC}${path.sep}`)) return null;
  return abs;
}

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
};

async function handle(req, res) {
  const url = new URL(req.url, "http://127.0.0.1");
  if (req.method === "GET" && url.pathname === "/api/state") {
    const state = await service.describe();
    send(res, 200, { ...state, job });
    return;
  }
  if (req.method === "GET" && url.pathname === "/api/job") {
    send(res, 200, job);
    return;
  }
  if (req.method === "PUT" && url.pathname === "/api/config") {
    const prev = store.loadConfig();
    const next = store.saveConfig(await readBody(req));
    armSchedule(prev, next);
    send(res, 200, next);
    return;
  }
  if (req.method === "POST" && url.pathname === "/api/sync") {
    const body = await readBody(req);
    const dryRun = Boolean(body.dryRun);
    const current = startJob(dryRun ? "preview" : "sync", async (log) => {
      try {
        const summary = await service.sync({ dryRun, log });
        if (!dryRun) store.saveRun({ lastRunAt: new Date().toISOString(), lastError: "" });
        return summary;
      } catch (err) {
        if (!dryRun) store.saveRun({ lastRunAt: new Date().toISOString(), lastError: err.message });
        throw err;
      }
    });
    send(res, 202, current);
    return;
  }
  if (req.method === "POST" && url.pathname === "/api/links") {
    send(res, 202, startJob("links", (log) => service.updateLinks({ log })));
    return;
  }
  if (req.method === "POST" && url.pathname === "/api/push") {
    send(res, 202, startJob("push", (log) => service.push(log)));
    return;
  }
  if (req.method === "GET") {
    const file = publicFile(url.pathname);
    if (!file || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      send(res, 404, { error: "没有这个页面" });
      return;
    }
    const ext = path.extname(file);
    res.writeHead(200, { "Content-Type": MIME[ext] || "application/octet-stream", "Cache-Control": "no-store" });
    fs.createReadStream(file).pipe(res);
    return;
  }
  send(res, 404, { error: "没有这个接口" });
}

function openBrowser(url) {
  if (process.env.PICBED_NO_BROWSER === "1") return;
  if (process.platform === "win32") {
    spawn("cmd", ["/c", "start", "", url], { detached: true, stdio: "ignore", windowsHide: true }).unref();
    return;
  }
  const opener = process.platform === "darwin" ? "open" : "xdg-open";
  spawn(opener, [url], { detached: true, stdio: "ignore" }).unref();
}

function main() {
  const config = store.loadConfig();
  const server = http.createServer((req, res) => {
    handle(req, res).catch((err) => {
      if (!res.headersSent) send(res, err.statusCode || 400, { error: err.message || "请求失败" });
    });
  });
  server.listen(config.port, "127.0.0.1", () => {
    const url = `http://127.0.0.1:${config.port}`;
    console.log(`Git 图床  ${url}`);
    openBrowser(url);
    const current = store.loadConfig();
    if (current.schedule.enabled && !store.loadRun().nextRunAt) {
      store.saveRun({ nextRunAt: Date.now() + current.schedule.intervalMinutes * 60000 });
    }
    startWatchers(current);
  });
  setInterval(() => {
    const current = store.loadConfig();
    const run = store.loadRun();
    if (!scheduleDue(run, current, Date.now())) return;
    if (job.status === "running") return;
    store.saveRun({ nextRunAt: Date.now() + current.schedule.intervalMinutes * 60000 });
    try {
      startJob("publish", async (log) => {
        log("定时推送开始");
        try {
          const summary = await service.publish({ log });
          store.saveRun({ lastRunAt: new Date().toISOString(), lastError: "" });
          return summary;
        } catch (err) {
          store.saveRun({ lastRunAt: new Date().toISOString(), lastError: err.message });
          throw err;
        }
      });
    } catch {
      // 手动任务刚启动时跳过这一轮
    }
  }, 15000);
  server.on("error", (err) => {
    if (err.code === "EADDRINUSE") {
      console.error(`端口 ${config.port} 已被占用`);
    } else {
      console.error(err.message);
    }
    process.exit(1);
  });
}

if (require.main === module) main();

module.exports = { scheduleDue };
