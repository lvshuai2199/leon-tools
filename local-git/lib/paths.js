"use strict";

const path = require("path");

function isInside(parent, child) {
  const rel = path.relative(path.resolve(parent), path.resolve(child));
  return rel === "" || (!rel.startsWith("..") && !path.isAbsolute(rel));
}

function toPosix(value) {
  return String(value || "").replace(/\\/g, "/");
}

/** 这台电脑上已有仓库所在的目录。拒绝磁盘根目录和系统目录。 */
function cloneRoot(raw) {
  const text = String(raw || "").trim();
  if (!text || text.includes("\0")) throw new Error("先填写仓库目录");
  if (!path.isAbsolute(text)) throw new Error("仓库目录要用绝对路径，例如 D:\\GitFiles");
  const resolved = path.resolve(text);
  const parsed = path.parse(resolved);
  if (resolved === parsed.root) throw new Error("不要把磁盘根目录当作仓库目录");
  const lower = resolved.toLowerCase();
  if (lower.includes("\\windows\\") || lower.includes("\\system32") || lower.includes("\\program files")) {
    throw new Error("这个目录不能用来放仓库");
  }
  return resolved;
}

function repoId(raw) {
  const text = toPosix(String(raw || "").trim());
  if (text === ".") return ".";
  if (!text || text.length > 240 || text.startsWith("/") || text.includes(":")) throw new Error("先选择仓库");
  const parts = text.split("/");
  if (!parts.length || parts.length > 4) throw new Error("仓库路径不对");
  for (const part of parts) {
    if (!part || part === "." || part === ".." || part === ".git") throw new Error("仓库路径不对");
  }
  return parts.join("/");
}

function resolveRepo(root, id) {
  const base = path.resolve(root);
  const repo = path.resolve(base, repoId(id));
  if (!isInside(base, repo)) throw new Error("仓库不在这个目录里");
  return repo;
}

function branchName(raw) {
  const name = String(raw || "").trim();
  if (!name || name.length > 200 || name.startsWith("-") || name.startsWith("/") || name.endsWith("/") || name.endsWith(".lock")) {
    throw new Error("分支名不对");
  }
  for (const token of ["..", "//", "@{", "~", "^", ":", "?", "*", "[", "\\", " "]) {
    if (name.includes(token)) throw new Error("分支名不对");
  }
  return name;
}

function sha(raw) {
  const text = String(raw || "").trim();
  if (!/^[0-9a-f]{7,40}$/i.test(text)) throw new Error("提交号不对");
  return text;
}

function message(raw) {
  const text = String(raw || "").replace(/\r\n/g, "\n").trim();
  if (!text) throw new Error("先写提交说明");
  if (text.length > 2000) throw new Error("提交说明太长");
  return text;
}

function stashRef(raw) {
  const text = String(raw || "").trim();
  if (/^stash@\{\d{1,6}\}$/.test(text)) return text;
  if (/^[0-9a-f]{7,40}$/i.test(text)) return text;
  throw new Error("贮藏编号不对");
}

function filePath(raw) {
  const text = toPosix(String(raw || "").trim());
  if (!text || text.length > 400 || text.startsWith("/") || text.includes("..") || text.includes(":")) {
    throw new Error("文件路径不对");
  }
  return text;
}

function parseRemote(raw) {
  const text = String(raw || "").trim();
  if (!text || text.length > 300) throw new Error("仓库地址不对");
  if (/^git@/i.test(text) || /^ssh:\/\//i.test(text)) throw new Error("只支持 GitHub 和 Gitee 的 https 地址");
  if (/[\s@]/.test(text)) throw new Error("仓库地址不对");
  let url;
  try {
    url = new URL(text);
  } catch {
    throw new Error("仓库地址不对");
  }
  const host = url.hostname.toLowerCase();
  if (url.protocol !== "https:" || url.username || url.password) {
    throw new Error("只支持 GitHub 和 Gitee 的 https 地址");
  }
  const provider = host === "github.com" ? "github" : host === "gitee.com" ? "gitee" : "";
  if (!provider) throw new Error("只支持 GitHub 和 Gitee 的 https 地址");
  let pathname = url.pathname.replace(/\.git$/i, "").replace(/\/+$/, "");
  const parts = pathname.split("/");
  if (parts.length !== 3 || !parts[1] || !parts[2] || parts[1].includes("..") || parts[2].includes("..")) {
    throw new Error("仓库地址要像 https://github.com/owner/name");
  }
  return {
    provider,
    owner: parts[1],
    name: parts[2],
    url: `https://${host}/${parts[1]}/${parts[2]}.git`,
  };
}

function providerOf(remoteUrl) {
  const text = String(remoteUrl || "").toLowerCase();
  if (!text) return "";
  if (text.startsWith("git@") || text.startsWith("ssh://")) return "ssh";
  if (text.includes("gitee.com")) return "gitee";
  if (text.includes("github.com")) return "github";
  return "";
}

module.exports = {
  isInside,
  toPosix,
  cloneRoot,
  repoId,
  resolveRepo,
  branchName,
  sha,
  message,
  filePath,
  stashRef,
  parseRemote,
  providerOf,
};
