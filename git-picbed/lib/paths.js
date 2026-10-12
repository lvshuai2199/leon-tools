"use strict";

const path = require("path");

function normKey(value) {
  const resolved = path.resolve(value);
  return process.platform === "win32" ? resolved.toLowerCase() : resolved;
}

function safePosixDir(value, fallback) {
  const clean = String(value ?? fallback ?? "")
    .trim()
    .replace(/\\/g, "/")
    .replace(/^\/+|\/+$/g, "");
  if (!clean) return "";
  const parts = clean.split("/");
  if (!/^[A-Za-z0-9._/-]+$/.test(clean) || parts.some((part) => !part || part === "." || part === "..")) {
    throw new Error("目录不合法");
  }
  return clean;
}

function safeBranch(value) {
  const branch = String(value || "").trim();
  if (
    !branch ||
    branch.startsWith("-") ||
    branch.includes("..") ||
    branch.endsWith("/") ||
    branch.endsWith(".lock") ||
    /[\s~^:\\?*[\]]/.test(branch)
  ) {
    throw new Error("分支名不合法");
  }
  return branch;
}

function safeRemote(value) {
  const remote = String(value || "origin").trim() || "origin";
  if (!/^[A-Za-z0-9._-]+$/.test(remote)) throw new Error("远程名不合法");
  return remote;
}

function safeName(value, label) {
  const text = String(value || "").trim();
  if (!text) return "";
  if (!/^[A-Za-z0-9._-]+$/.test(text)) throw new Error(`${label}不合法`);
  return text;
}

function toPosix(value) {
  return String(value || "").split(path.sep).join("/");
}

function isInside(root, abs) {
  const rel = path.relative(path.resolve(root), path.resolve(abs));
  return Boolean(rel) && !rel.startsWith("..") && !path.isAbsolute(rel);
}

function joinPosix(dir, rel) {
  const left = safePosixDir(dir, "");
  const right = String(rel || "")
    .replace(/\\/g, "/")
    .replace(/^\/+/, "");
  if (!right || right.split("/").some((part) => !part || part === "." || part === "..")) {
    throw new Error("文件路径不合法");
  }
  return [left, right].filter(Boolean).join("/");
}

module.exports = {
  normKey,
  safePosixDir,
  safeBranch,
  safeRemote,
  safeName,
  toPosix,
  isInside,
  joinPosix,
};
