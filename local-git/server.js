"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");
const { createStore } = require("./lib/store");
const { createService } = require("./lib/service");

const ROOT = __dirname;
const PUBLIC = path.join(ROOT, "public");
const store = createStore(ROOT);
const service = createService(ROOT, store);

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
      if (size > 2 * 1024 * 1024) {
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

function sendFile(res, file) {
  const ext = path.extname(file);
  const types = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8" };
  fs.readFile(file, (err, buf) => {
    if (err) {
      res.writeHead(404);
      res.end("not found");
      return;
    }
    res.writeHead(200, { "Content-Type": types[ext] || "application/octet-stream", "Cache-Control": "no-store" });
    res.end(buf);
  });
}

async function route(req, res) {
  const url = new URL(req.url, "http://127.0.0.1");
  if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/index.html")) {
    sendFile(res, path.join(PUBLIC, "index.html"));
    return;
  }
  if (req.method === "GET" && (url.pathname === "/app.js" || url.pathname === "/styles.css")) {
    sendFile(res, path.join(PUBLIC, path.basename(url.pathname)));
    return;
  }
  if (!url.pathname.startsWith("/api/")) {
    res.writeHead(404);
    res.end();
    return;
  }
  const body = req.method === "POST" ? await readBody(req) : {};
  const repo = url.searchParams.get("repo") || body.repo || "";
  let data;
  if (req.method === "GET" && url.pathname === "/api/home") data = await service.home();
  else if (req.method === "POST" && url.pathname === "/api/settings") data = await service.saveSettings(body);
  else if (req.method === "POST" && url.pathname === "/api/clone") data = await service.clone(body.url);
  else if (req.method === "GET" && url.pathname === "/api/workspace") data = await service.workspace(repo);
  else if (req.method === "GET" && url.pathname === "/api/log") {
    data = await service.log(repo, url.searchParams.get("max"), url.searchParams.get("ref") || "");
  }
  else if (req.method === "GET" && url.pathname === "/api/diff") {
    data = await service.diff(repo, url.searchParams.get("rev") || "", url.searchParams.get("target") || "", url.searchParams.get("scope") || "", url.searchParams.get("file") || "");
  } else if (req.method === "POST" && url.pathname === "/api/commit") data = await service.commit(repo, body.message);
  else if (req.method === "POST" && url.pathname === "/api/stage") data = await service.stage(repo, body.files);
  else if (req.method === "POST" && url.pathname === "/api/unstage") data = await service.unstage(repo, body.files);
  else if (req.method === "POST" && url.pathname === "/api/fetch") data = await service.fetch(repo);
  else if (req.method === "POST" && url.pathname === "/api/pull") data = await service.pull(repo);
  else if (req.method === "POST" && url.pathname === "/api/push") data = await service.push(repo);
  else if (req.method === "POST" && url.pathname === "/api/branch") data = await service.branch(repo, body.name);
  else if (req.method === "POST" && url.pathname === "/api/branch-at") data = await service.branchAt(repo, body.name, body.sha);
  else if (req.method === "POST" && url.pathname === "/api/checkout") data = await service.checkout(repo, body.name, body.stash === true);
  else if (req.method === "POST" && url.pathname === "/api/delete-branch") data = await service.deleteBranch(repo, body.name, body.force === true);
  else if (req.method === "POST" && url.pathname === "/api/merge") data = await service.merge(repo, body.branch);
  else if (req.method === "POST" && url.pathname === "/api/rebase") data = await service.rebase(repo, body.onto);
  else if (req.method === "POST" && url.pathname === "/api/rebase-from") data = await service.rebaseFrom(repo, body.sha, body.onto);
  else if (req.method === "GET" && url.pathname === "/api/history-range") {
    data = await service.historyRange(repo, url.searchParams.get("mode") || "", url.searchParams.get("onto") || "", url.searchParams.get("rev") || "");
  } else if (req.method === "POST" && url.pathname === "/api/squash") data = await service.squash(repo, body.shas, body.message);
  else if (req.method === "POST" && url.pathname === "/api/rebase-squash") {
    data = await service.rebaseSquash(repo, body.onto, body.shas, body.message);
  }
  else if (req.method === "POST" && url.pathname === "/api/cherry-pick") data = await service.cherryPick(repo, body.sha, body.target);
  else if (req.method === "POST" && url.pathname === "/api/cherry-pick/partial") {
    data = await service.cherryPickPartial(repo, body.sha, body.target, body.hunks, body.message);
  } else if (req.method === "POST" && url.pathname === "/api/restore") data = await service.restore(repo, body.files);
  else if (req.method === "POST" && url.pathname === "/api/restore-hunk") data = await service.restoreHunk(repo, body.file, body.index);
  else if (req.method === "POST" && url.pathname === "/api/stash/apply") data = await service.stashApply(repo, body.stash || body.id);
  else if (req.method === "POST" && url.pathname === "/api/stash/drop") data = await service.stashDrop(repo, body.stash || body.id);
  else if (req.method === "GET" && url.pathname === "/api/stash/show") {
    data = await service.stashShow(repo, url.searchParams.get("id") || "");
  } else {
    res.writeHead(404);
    res.end();
    return;
  }
  send(res, 200, data);
}

if (!fs.existsSync(store.file)) {
  const existing = "D:\\GitFiles";
  if (fs.existsSync(existing)) store.save({ cloneRoot: existing });
}
const port = Number(process.env.LOCAL_GIT_PORT) || store.read().port || 3792;
const server = http.createServer((req, res) => {
  route(req, res).catch((error) => {
    const status = error.statusCode || 400;
    send(res, status, { error: error.message || "失败" });
  });
});

server.listen(port, "127.0.0.1", () => {
  const address = `http://127.0.0.1:${port}`;
  console.log(`本机 Git 工具 ${address}`);
  if (process.platform === "win32" && !process.env.LOCAL_GIT_NO_OPEN) {
    spawn("cmd", ["/c", "start", "", address], { windowsHide: true, detached: true, stdio: "ignore" }).unref();
  }
});
