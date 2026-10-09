"use strict";

const { safeName } = require("./paths");

function redact(url) {
  return String(url || "").replace(/\/\/[^@/\s]+@/g, "//");
}

function parseGitRemote(url) {
  const cleaned = String(url || "")
    .trim()
    .replace(/\.git$/i, "");
  if (!cleaned) return null;
  const ssh = cleaned.match(/^git@([^:]+):([^/]+)\/(.+)$/);
  const sshUrl = cleaned.match(/^ssh:\/\/(?:[^@/]+@)?([^/]+)\/([^/]+)\/(.+)$/);
  const http = cleaned.match(/^https?:\/\/(?:[^@/\s]+@)?([^/]+)\/([^/]+)\/(.+)$/);
  const matched = ssh || sshUrl || http;
  if (!matched) return null;
  const host = matched[1].toLowerCase();
  let provider = null;
  if (host === "github.com" || host.endsWith(".github.com")) provider = "github";
  if (host === "gitee.com" || host.endsWith(".gitee.com")) provider = "gitee";
  return { host, owner: matched[2], repo: matched[3], provider };
}

function hostLabel(provider) {
  return provider === "github" ? "GitHub" : "Gitee";
}

function resolveHost(provider, remoteUrl, owner, repo) {
  if (provider !== "gitee" && provider !== "github") {
    throw new Error("托管只能选 Gitee 或 GitHub");
  }
  const parsed = parseGitRemote(remoteUrl);
  if (!parsed) throw new Error("无法解析远程仓库地址");
  if (!parsed.provider) throw new Error("远程仓库需要是 Gitee 或 GitHub");
  if (parsed.provider !== provider) {
    throw new Error(`origin 指向 ${hostLabel(parsed.provider)}，请把托管改成 ${hostLabel(parsed.provider)}`);
  }
  return {
    provider,
    owner: safeName(owner || parsed.owner, "owner"),
    repo: safeName(repo || parsed.repo, "repo"),
    remoteUrl: redact(remoteUrl),
  };
}

function rawUrl({ provider, owner, repo, branch, filePath }) {
  const encoded = String(filePath || "")
    .split("/")
    .map((part) => encodeURIComponent(part))
    .join("/");
  if (provider === "github") {
    return `https://raw.githubusercontent.com/${owner}/${repo}/${branch}/${encoded}`;
  }
  return `https://raw.giteeusercontent.com/${owner}/${repo}/raw/${branch}/${encoded}`;
}

function bedPrefixes({ provider, owner, repo, branch, alsoBranches = [] }) {
  const branches = [];
  for (const name of [branch, ...alsoBranches]) {
    if (name && !branches.includes(name)) branches.push(name);
  }
  const prefixes = [];
  for (const name of branches) {
    if (provider === "github") {
      prefixes.push(`https://raw.githubusercontent.com/${owner}/${repo}/${name}/`);
    } else {
      prefixes.push(`https://raw.giteeusercontent.com/${owner}/${repo}/raw/${name}/`);
      prefixes.push(`https://gitee.com/${owner}/${repo}/raw/${name}/`);
    }
  }
  return prefixes;
}

function bedPrefix(args) {
  return bedPrefixes(args)[0];
}

module.exports = { redact, parseGitRemote, resolveHost, rawUrl, bedPrefix, bedPrefixes, hostLabel };
