"use strict";

const { execFile } = require("child_process");
const fs = require("fs");
const path = require("path");
const { isInside, joinPosix, safeBranch, safeRemote, toPosix } = require("./paths");

function git(repo, args, timeout = 20000) {
  return new Promise((resolve, reject) => {
    execFile(
      "git",
      args,
      {
        cwd: repo,
        windowsHide: true,
        timeout,
        maxBuffer: 8 * 1024 * 1024,
        env: {
          ...process.env,
          GIT_TERMINAL_PROMPT: "0",
          GCM_INTERACTIVE: "Never",
        },
      },
      (err, stdout, stderr) => {
        if (err) {
          const detail = `${stderr || ""}\n${stdout || ""}`.trim().split("\n").slice(-12).join("\n");
          const wrapped = new Error(detail || err.message);
          wrapped.code = err.code;
          reject(wrapped);
          return;
        }
        resolve({ stdout: stdout || "", stderr: stderr || "" });
      }
    );
  });
}

function friendlyGitError(err) {
  if (/please tell me who you are|Author identity unknown|user\.name|user\.email/i.test(err.message || "")) {
    return new Error("这个仓库还没有提交身份。请在该仓库配置 user.name 和 user.email 后再整理。");
  }
  return err;
}

async function assertRepo(repoPath) {
  if (!repoPath || !fs.existsSync(repoPath)) throw new Error("本地仓库路径不存在");
  let inside = "";
  try {
    inside = (await git(repoPath, ["rev-parse", "--is-inside-work-tree"])).stdout.trim();
  } catch {
    throw new Error("这个路径不是 git 仓库");
  }
  if (inside !== "true") throw new Error("这个路径不是 git 仓库");
  const top = (await git(repoPath, ["rev-parse", "--show-toplevel"])).stdout.trim();
  return path.resolve(top);
}

async function remoteUrl(repoPath, remote) {
  try {
    return (await git(repoPath, ["config", "--get", `remote.${remote}.url`])).stdout.trim();
  } catch {
    throw new Error(`仓库里没有远程 ${remote}`);
  }
}

async function currentBranch(repoPath) {
  try {
    const name = (await git(repoPath, ["symbolic-ref", "--short", "HEAD"])).stdout.trim();
    if (name) return name;
  } catch {
    // 游离 HEAD 时再读简称
  }
  return (await git(repoPath, ["rev-parse", "--abbrev-ref", "HEAD"])).stdout.trim();
}

async function isDirty(repoPath) {
  return Boolean((await git(repoPath, ["status", "--porcelain"])).stdout.trim());
}

async function branchExists(repoPath, branch) {
  try {
    await git(repoPath, ["show-ref", "--verify", "--quiet", `refs/heads/${branch}`]);
    return true;
  } catch {
    return false;
  }
}

function imageWorktreePath(repoPath, branch) {
  return path.resolve(path.dirname(repoPath), `${path.basename(repoPath)}-${branch}`);
}

async function listWorktrees(repoPath) {
  const out = await git(repoPath, ["worktree", "list", "--porcelain"]);
  const trees = [];
  let current = null;
  for (const line of out.stdout.split(/\r?\n/)) {
    if (line.startsWith("worktree ")) {
      if (current) trees.push(current);
      current = { path: path.resolve(line.slice("worktree ".length)), branch: "" };
    } else if (line.startsWith("branch ") && current) {
      current.branch = line.slice("branch ".length).replace(/^refs\/heads\//, "");
    }
  }
  if (current) trees.push(current);
  return trees;
}

async function peekImageRepo(repoPath, branch) {
  const top = await assertRepo(repoPath);
  const target = safeBranch(branch);
  if ((await currentBranch(top)) === target) return { root: top, branch: target, created: false, separate: false };
  const found = (await listWorktrees(top)).find((item) => item.branch === target);
  if (found) return { root: found.path, branch: target, created: false, separate: true };
  return { root: imageWorktreePath(top, target), branch: target, created: false, separate: true, missing: true };
}

async function openImageRepo(repoPath, branch) {
  const top = await assertRepo(repoPath);
  const target = safeBranch(branch);
  const current = await currentBranch(top);
  if (current === target) return { root: top, branch: target, created: false, separate: false };
  const found = (await listWorktrees(top)).find((item) => item.branch === target);
  if (found) return { root: found.path, branch: target, created: false, separate: true };
  const dest = imageWorktreePath(top, target);
  if (fs.existsSync(dest)) {
    throw new Error(`图片分支 ${target} 要放到 ${dest}，但这个目录已经存在`);
  }
  if (await branchExists(top, target)) await git(top, ["worktree", "add", dest, target], 60000);
  else await git(top, ["worktree", "add", "--orphan", "-b", target, dest], 60000);
  return { root: dest, branch: target, created: true, separate: true };
}

async function ensureBranch(repoPath, branch) {
  const target = safeBranch(branch);
  const current = await currentBranch(repoPath);
  if (current === target) return { switched: false, from: current, branch: target };
  if (await isDirty(repoPath)) {
    throw new Error(`工作区有未提交改动，先处理完再切到分支 ${target}`);
  }
  if (await branchExists(repoPath, target)) await git(repoPath, ["checkout", target]);
  else await git(repoPath, ["checkout", "-b", target]);
  return { switched: true, from: current, branch: target };
}

async function commitPaths(repoPath, rels, message) {
  const unique = [...new Set(rels.filter(Boolean))];
  if (!unique.length) return false;
  try {
    await git(repoPath, ["add", "-A", "--", ...unique], 60000);
    const staged = (await git(repoPath, ["diff", "--cached", "--name-only"])).stdout.trim();
    if (!staged) return false;
    await git(repoPath, ["commit", "-m", message], 60000);
    return true;
  } catch (err) {
    throw friendlyGitError(err);
  }
}

function repoAbs(repoPath, rel) {
  const abs = path.resolve(repoPath, ...String(rel).split("/"));
  if (!isInside(repoPath, abs)) throw new Error("路径越出仓库");
  return abs;
}

function readManifest(repoPath, imageDir) {
  const abs = path.join(repoPath, ...`${imageDir}/picbed.json`.split("/"));
  if (!fs.existsSync(abs)) return [];
  try {
    const json = JSON.parse(fs.readFileSync(abs, "utf8"));
    return Array.isArray(json.images) ? json.images : [];
  } catch {
    throw new Error("picbed.json 无法解析");
  }
}

function applyImagePlan(repoPath, imageDir, plan, log = () => {}) {
  const changed = [];
  for (const item of plan.reuses) {
    const dest = repoAbs(repoPath, item.image.path);
    if (!fs.existsSync(dest)) {
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(item.sources[0].abs, dest);
      changed.push(item.image.path);
      log(`补回 ${item.image.path}`);
    }
  }
  for (const item of plan.uploads) {
    const dest = repoAbs(repoPath, item.remotePath);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(item.local.abs, dest);
    changed.push(item.remotePath);
  }
  const imageRoot = path.resolve(repoPath, ...imageDir.split("/"));
  for (const item of plan.deletes) {
    const dest = repoAbs(repoPath, item.path);
    if (fs.existsSync(dest)) fs.rmSync(dest);
    changed.push(item.path);
    const parent = path.dirname(dest);
    if (path.resolve(parent) !== imageRoot && fs.existsSync(parent) && fs.readdirSync(parent).length === 0) {
      fs.rmdirSync(parent);
    }
  }
  const manifestRel = `${imageDir}/picbed.json`;
  const manifestAbs = repoAbs(repoPath, manifestRel);
  fs.mkdirSync(path.dirname(manifestAbs), { recursive: true });
  const body = `${JSON.stringify({ version: 1, images: plan.nextImages }, null, 2)}\n`;
  const prev = fs.existsSync(manifestAbs) ? fs.readFileSync(manifestAbs, "utf8").replace(/\r\n/g, "\n") : "";
  if (prev !== body) {
    fs.writeFileSync(manifestAbs, body);
    changed.push(manifestRel);
  }
  return changed;
}

async function publishFiles({ repoPath, branch, directory, remote, files, message, doPush, log = () => {} }) {
  const top = await assertRepo(repoPath);
  const target = safeBranch(branch);
  const remoteName = safeRemote(remote);
  const switched = await ensureBranch(top, target);
  if (switched.switched) log(`已切到分支 ${target}（从 ${switched.from}）`);
  const rels = [];
  for (const file of files) {
    if (!fs.existsSync(file.abs)) throw new Error(`待推送文件不存在：${file.abs}`);
    const inside = isInside(top, file.abs);
    if (directory) {
      const destRel = joinPosix(directory, file.rel);
      const destAbs = repoAbs(top, destRel);
      if (path.resolve(destAbs) !== path.resolve(file.abs)) {
        fs.mkdirSync(path.dirname(destAbs), { recursive: true });
        fs.copyFileSync(file.abs, destAbs);
      }
      rels.push(destRel);
    } else if (!inside) {
      throw new Error("文档在仓库外，请填写推送目录");
    }
    if (inside) rels.push(toPosix(path.relative(top, file.abs)));
  }
  const committed = await commitPaths(top, rels, message);
  let pushOutput = "";
  if (doPush) {
    const result = await git(top, ["push", "-u", remoteName, target], 120000);
    pushOutput = (result.stderr || result.stdout || "").trim();
  }
  return { committed, pushed: Boolean(doPush), branch: target, files: [...new Set(rels)], pushOutput };
}

async function pushBranch(repoPath, branch, remote, log = () => {}) {
  const top = await assertRepo(repoPath);
  const target = safeBranch(branch);
  const remoteName = safeRemote(remote);
  const switched = await ensureBranch(top, target);
  if (switched.switched) log(`已切到分支 ${target}（从 ${switched.from}）`);
  const result = await git(top, ["push", "-u", remoteName, target], 120000);
  return { branch: target, pushOutput: (result.stderr || result.stdout || "").trim() };
}

module.exports = {
  git,
  assertRepo,
  remoteUrl,
  currentBranch,
  ensureBranch,
  openImageRepo,
  peekImageRepo,
  imageWorktreePath,
  listWorktrees,
  commitPaths,
  readManifest,
  applyImagePlan,
  publishFiles,
  pushBranch,
};
