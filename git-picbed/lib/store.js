"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { safeBranch, safeName, safePosixDir, safeRemote } = require("./paths");

const DEFAULT_CONFIG = {
  port: 3791,
  provider: "gitee",
  repoPath: "",
  remote: "origin",
  branch: "master",
  imageDir: "imgs",
  owner: "",
  repo: "",
  watchFolders: [],
  docFolders: [],
  clearMissingLinks: true,
  schedule: { enabled: false, intervalMinutes: 30 },
  push: { branch: "", directory: "", message: "docs: 更新图床链接" },
};

function defaultConfig() {
  return {
    ...DEFAULT_CONFIG,
    watchFolders: [],
    docFolders: [],
    schedule: { ...DEFAULT_CONFIG.schedule },
    push: { ...DEFAULT_CONFIG.push },
  };
}

function createStore(appRoot) {
  const configPath = path.join(appRoot, "config.json");
  const dataDir = path.join(appRoot, "data");
  const sourcesPath = path.join(dataDir, "sources.json");
  const pendingPath = path.join(dataDir, "pending.json");
  const runPath = path.join(dataDir, "run.json");
  const deferredPath = path.join(dataDir, "deferred-docs.json");

  function readJson(file, fallback) {
    try {
      return JSON.parse(fs.readFileSync(file, "utf8"));
    } catch {
      return fallback;
    }
  }

  function writeJson(file, value) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
  }

  function mergeFolders(prev, text) {
    const old = new Map((prev || []).map((item) => [path.resolve(item.path).toLowerCase(), item]));
    const out = [];
    const seen = new Set();
    for (const line of String(text ?? "").split(/\r?\n/)) {
      const raw = line.trim();
      if (!raw) continue;
      const resolved = path.resolve(raw);
      const key = resolved.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      const hit = old.get(key);
      out.push(hit ? { id: hit.id, path: resolved } : { id: `f_${crypto.randomBytes(4).toString("hex")}`, path: resolved });
    }
    return out;
  }

  function foldersFromArray(value) {
    const out = [];
    const seen = new Set();
    for (const item of value) {
      const raw = typeof item === "string" ? item : item?.path;
      if (!raw) continue;
      const resolved = path.resolve(String(raw));
      const key = resolved.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      out.push({
        id: item && item.id ? String(item.id) : `f_${crypto.randomBytes(4).toString("hex")}`,
        path: resolved,
      });
    }
    return out;
  }

  function foldersFrom(value, text, prev) {
    if (typeof text === "string") return mergeFolders(prev, text);
    if (Array.isArray(value)) return foldersFromArray(value);
    return prev || [];
  }

  function normalize(input, prev) {
    const src = input || {};
    const base = prev || defaultConfig();
    const provider = src.provider === "github" || src.provider === "gitee" ? src.provider : base.provider || "gitee";
    const interval = Number(src.schedule?.intervalMinutes ?? base.schedule.intervalMinutes);
    const pushBranch = String(src.push?.branch ?? "").trim();
    const repoPath = String(src.repoPath ?? "").trim();
    return {
      port: Number(src.port || base.port || 3791) || 3791,
      provider,
      repoPath: repoPath ? path.resolve(repoPath) : "",
      remote: safeRemote(src.remote ?? base.remote),
      branch: safeBranch(src.branch || base.branch || "master"),
      imageDir: safePosixDir(src.imageDir || base.imageDir || "imgs", "imgs"),
      owner: safeName(src.owner, "owner"),
      repo: safeName(src.repo, "repo"),
      watchFolders: foldersFrom(src.watchFolders, src.watchText, base.watchFolders),
      docFolders: foldersFrom(src.docFolders, src.docText, base.docFolders),
      clearMissingLinks: src.clearMissingLinks !== false,
      schedule: {
        enabled: Boolean(src.schedule?.enabled),
        intervalMinutes: Math.min(1440, Math.max(1, Number.isFinite(interval) ? Math.round(interval) : 30)),
      },
      push: {
        branch: pushBranch ? safeBranch(pushBranch) : "",
        directory: String(src.push?.directory ?? "").trim() ? safePosixDir(src.push.directory, "") : "",
        message: String(src.push?.message || "docs: 更新图床链接").trim() || "docs: 更新图床链接",
      },
    };
  }

  function loadConfig() {
    const raw = readJson(configPath, null);
    if (!raw) return defaultConfig();
    return normalize(raw, defaultConfig());
  }

  function saveConfig(input) {
    const next = normalize(input, loadConfig());
    writeJson(configPath, next);
    return next;
  }

  function loadSources() {
    return readJson(sourcesPath, { items: {} });
  }

  function saveSources(value) {
    writeJson(sourcesPath, value);
  }

  function loadPending() {
    return readJson(pendingPath, { files: [], at: "" });
  }

  function savePending(value) {
    writeJson(pendingPath, value);
  }

  function loadRun() {
    return readJson(runPath, { nextRunAt: 0, lastRunAt: "", lastError: "" });
  }

  function saveRun(patch) {
    const next = { ...loadRun(), ...patch };
    writeJson(runPath, next);
    return next;
  }

  function loadDeferred() {
    return readJson(deferredPath, { needed: false, at: "", count: 0 });
  }

  function saveDeferred(value) {
    writeJson(deferredPath, value || { needed: false, at: "", count: 0 });
  }

  return {
    loadConfig,
    saveConfig,
    loadSources,
    saveSources,
    loadPending,
    savePending,
    loadRun,
    saveRun,
    loadDeferred,
    saveDeferred,
  };
}

function scheduleDue(run, config, now) {
  if (!config.schedule?.enabled) return false;
  if (!run?.nextRunAt) return false;
  return now >= run.nextRunAt;
}

module.exports = { createStore, scheduleDue, defaultConfig };
