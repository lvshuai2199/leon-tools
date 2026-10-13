"use strict";

const { execFile } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const diff = require("./diff");
const { layout } = require("./graph");
const { branchTree, decorateRefs, isRemoteHead, parseTrack } = require("./branches");
const { branchName, filePath, isInside, message, providerOf, sha, stashRef, toPosix } = require("./paths");

const ASKPASS = path.join(__dirname, "..", "askpass.cmd");

function scrub(text, token) {
  let value = String(text || "");
  if (token) value = value.split(token).join("***");
  value = value.replace(/https?:\/\/[^/\s@]+@/gi, (match) => match.slice(0, match.indexOf("://") + 3));
  const lines = value.trim().split(/\r?\n/).filter(Boolean);
  return lines.slice(-8).join("\n");
}

function git(cwd, args, opts = {}) {
  const token = opts.token || "";
  const user = opts.provider === "gitee" ? "oauth2" : "x-access-token";
  const command = [
    "-c",
    "core.quotepath=false",
    "-c",
    "commit.gpgsign=false",
    "-c",
    "i18n.logOutputEncoding=utf-8",
    ...args,
  ];
  return new Promise((resolve, reject) => {
    execFile(
      "git",
      command,
      {
        cwd,
        windowsHide: true,
        timeout: opts.timeout || 30000,
        maxBuffer: opts.maxBuffer || 20 * 1024 * 1024,
        env: {
          ...process.env,
          GIT_TERMINAL_PROMPT: "0",
          GCM_INTERACTIVE: "Never",
          GIT_ASKPASS: ASKPASS,
          SSH_ASKPASS: ASKPASS,
          LOCAL_GIT_ASK_USER: token ? user : "",
          LOCAL_GIT_ASK_TOKEN: token,
        },
      },
      (err, stdout, stderr) => {
        if (err && err.code === "ENOENT") {
          reject(new Error("这台电脑没有 git 命令"));
          return;
        }
        if (err && (err.code === "ETIMEDOUT" || err.killed)) {
          reject(new Error("git 超时"));
          return;
        }
        const code = err && typeof err.code === "number" ? err.code : err ? -1 : 0;
        const ok = !err || (opts.okCodes || [0]).includes(code);
        if (!ok) {
          const detail = scrub(`${stderr || ""}\n${stdout || ""}`, token);
          reject(new Error(detail || "git 失败"));
          return;
        }
        resolve({ stdout: stdout || "", stderr: stderr || "", code: err ? code : 0 });
      }
    );
  });
}

function authFor(remoteUrl, tokens) {
  const provider = providerOf(remoteUrl);
  if (provider === "github") return { provider, token: tokens.githubToken || "" };
  if (provider === "gitee") return { provider, token: tokens.giteeToken || "" };
  return { provider, token: "" };
}

async function originUrl(repo) {
  try {
    return (await git(repo, ["config", "--get", "remote.origin.url"])).stdout.trim();
  } catch {
    return "";
  }
}

async function currentBranch(repo) {
  const name = (await git(repo, ["branch", "--show-current"])).stdout.trim();
  return name;
}

async function assertClean(repo) {
  const text = (await git(repo, ["status", "--porcelain"])).stdout.trim();
  if (text) throw new Error("工作区有未提交改动，请先提交");
}

async function assertBranch(repo) {
  const name = await currentBranch(repo);
  if (!name) throw new Error("先检出一个分支");
  return name;
}

function parseStatus(text) {
  const changes = [];
  const seen = new Set();
  for (const raw of String(text || "").split(/\r?\n/)) {
    if (!raw.trim() || raw.length < 4) continue;
    const index = raw[0];
    const worktree = raw[1];
    let file = raw.slice(3);
    if (file.includes(" -> ")) file = file.split(" -> ").pop();
    if (file.startsWith('"') && file.endsWith('"')) {
      try {
        file = JSON.parse(file);
      } catch {
        file = file.slice(1, -1);
      }
    }
    file = toPosix(file);
    if (!file || seen.has(file)) continue;
    seen.add(file);
    changes.push({ path: file, index, worktree });
  }
  changes.sort((a, b) => a.path.localeCompare(b.path));
  return changes;
}

function isConflict(change) {
  return Boolean(change) && (change.index === "U" || change.worktree === "U");
}

function isStaged(change) {
  return Boolean(change) && !isConflict(change) && "MADRC".includes(change.index);
}

function isUnstaged(change) {
  if (!change) return false;
  if (isConflict(change)) return true;
  return change.worktree === "?" || "MADRC".includes(change.worktree);
}

async function readStatus(repo) {
  const text = (await git(repo, ["status", "--porcelain=v1", "-uall"])).stdout;
  return parseStatus(text);
}

function parseStatusZ(text) {
  const parts = String(text || "").split("\0");
  const entries = [];
  for (let i = 0; i < parts.length; i++) {
    const rec = parts[i];
    if (!rec || rec.length < 4) continue;
    const code = rec.slice(0, 2);
    const first = toPosix(rec.slice(3));
    // git status -z 的重命名和复制是 XY 新路径\0旧路径\0
    const renamed = code[0] === "R" || code[0] === "C" || code[1] === "R" || code[1] === "C";
    if (renamed) {
      const orig = toPosix(parts[i + 1] || "");
      i += 1;
      entries.push({ code, path: first, orig, untracked: false });
      continue;
    }
    entries.push({ code, path: first, orig: "", untracked: code === "??" });
  }
  return entries;
}

async function statusEntries(repo) {
  const text = (await git(repo, ["status", "--porcelain=v1", "-z", "-uall"])).stdout;
  return parseStatusZ(text);
}

function insideRepo(repo, rel) {
  const abs = path.resolve(repo, ...String(rel).split("/"));
  if (!isInside(path.resolve(repo), abs)) throw new Error("文件路径不对");
  return abs;
}

function removeWorktreeFile(repo, rel) {
  const abs = insideRepo(repo, rel);
  let st;
  try {
    st = fs.lstatSync(abs);
  } catch {
    return;
  }
  if (st.isDirectory()) throw new Error("只能回滚文件");
  fs.unlinkSync(abs);
}

async function branches(repo, current) {
  let text = "";
  try {
    text = (
      await git(repo, [
        "for-each-ref",
        "--format=%(refname)%00%(upstream:short)%00%(upstream:track)%00%(HEAD)",
        "refs/heads",
        "refs/remotes",
      ])
    ).stdout;
  } catch {
    text = (await git(repo, ["for-each-ref", "--format=%(refname)%00%00%00%(HEAD)", "refs/heads", "refs/remotes"])).stdout;
  }
  const out = [];
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const [ref, upstream, track, head] = line.split("\0");
    if (!ref) continue;
    if (ref.startsWith("refs/heads/")) {
      const name = ref.slice("refs/heads/".length);
      const counts = parseTrack(upstream, track);
      out.push({ name, remote: false, current: head === "*" || name === current, ahead: counts.ahead, behind: counts.behind });
    } else if (ref.startsWith("refs/remotes/")) {
      const name = ref.slice("refs/remotes/".length);
      if (isRemoteHead(name)) continue;
      out.push({ name, remote: true, current: false, ahead: null, behind: null });
    }
  }
  out.sort((a, b) => {
    if (a.current !== b.current) return a.current ? -1 : 1;
    if (a.remote !== b.remote) return a.remote ? 1 : -1;
    return a.name.localeCompare(b.name);
  });
  return out;
}

async function tracking(repo) {
  try {
    const upstream = (await git(repo, ["rev-parse", "--abbrev-ref", "@{upstream}"])).stdout.trim();
    const counts = (await git(repo, ["rev-list", "--left-right", "--count", "HEAD...@{upstream}"])).stdout.trim().split(/\s+/);
    return { upstream, ahead: Number(counts[0]) || 0, behind: Number(counts[1]) || 0 };
  } catch {
    return { upstream: "", ahead: 0, behind: 0 };
  }
}

async function workspace(repo, id) {
  const branch = await currentBranch(repo);
  const detached = !branch;
  const track = detached ? { upstream: "", ahead: 0, behind: 0 } : await tracking(repo);
  const changes = await readStatus(repo);
  const branchList = await branches(repo, branch);
  return {
    id,
    branch,
    detached,
    upstream: track.upstream,
    ahead: track.ahead,
    behind: track.behind,
    dirty: changes.length > 0,
    changes,
    branches: branchList,
    branchTree: branchTree(branchList, { detached, current: branch }),
    stashes: await listStashes(repo),
  };
}

async function log(repo, max = 50) {
  const limit = Math.min(Math.max(Number(max) || 50, 1), 80);
  const remotes = await listRemotes(repo);
  let text = "";
  try {
    text = (
      await git(repo, [
        "log",
        "--all",
        "--topo-order",
        "-n",
        String(limit + 1),
        "--pretty=format:%H%x1f%h%x1f%an%x1f%aI%x1f%D%x1f%P%x1f%s%x1e",
      ])
    ).stdout;
  } catch {
    return { commits: [], graph: [], truncated: false };
  }
  const records = [];
  for (const raw of text.split("\x1e")) {
    const record = raw.trim();
    if (!record) continue;
    const [full, shortSha, author, date, refs, parents, subject] = record.split("\x1f");
    if (!full) continue;
    records.push({
      sha: full.trim(),
      shortSha: (shortSha || full).trim().slice(0, 7),
      message: subject || "",
      author: author || "",
      date: (date || "").trim(),
      refs: decorateRefs(refs, remotes),
      parents: String(parents || "")
        .split(/\s+/)
        .map((item) => item.trim())
        .filter(Boolean),
    });
  }
  const truncated = records.length > limit;
  const commits = records.slice(0, limit);
  return {
    commits,
    truncated,
    graph: layout(commits.map((commit) => ({ sha: commit.sha, parents: commit.parents }))),
  };
}

function untrackedPatch(repo, rel) {
  const abs = path.join(repo, ...rel.split("/"));
  let buf;
  try {
    buf = fs.readFileSync(abs);
  } catch {
    return "";
  }
  if (buf.includes(0) || buf.length > 200000) {
    return `diff --git a/${rel} b/${rel}\nnew file mode 100644\nBinary files /dev/null and b/${rel} differ\n`;
  }
  const lines = buf.toString("utf8").replace(/\r\n/g, "\n").split("\n");
  if (lines.length && lines[lines.length - 1] === "") lines.pop();
  const body = lines.map((line) => `+${line}`).join("\n");
  return `diff --git a/${rel} b/${rel}\nnew file mode 100644\n--- /dev/null\n+++ b/${rel}\n@@ -0,0 +1,${lines.length} @@\n${body}\n`;
}

async function appendUntracked(repo, patch) {
  const extra = (await git(repo, ["ls-files", "--others", "--exclude-standard", "-z"])).stdout;
  let out = patch || "";
  for (const rel of extra.split("\0")) {
    if (!rel) continue;
    out += untrackedPatch(repo, toPosix(rel));
  }
  return out;
}

async function worktreePatch(repo) {
  const tracked = (await git(repo, ["diff", "--no-color", "HEAD"], { okCodes: [0, 1] })).stdout;
  return appendUntracked(repo, tracked);
}

async function unstagedPatch(repo) {
  const tracked = (await git(repo, ["diff", "--no-color"], { okCodes: [0, 1] })).stdout;
  return appendUntracked(repo, tracked);
}

function applySideNotes(file, scope) {
  if (!file || !file.side || file.side.binary) return;
  if (scope === "unstaged") {
    file.side.leftNote = file.status === "A" ? "暂存区没有" : "暂存区";
    file.side.rightNote = file.status === "D" ? "工作区已删除" : "工作区";
  } else if (scope === "staged") {
    file.side.leftNote = file.status === "A" ? "HEAD 没有" : "HEAD";
    file.side.rightNote = file.status === "D" ? "暂存区已删除" : "暂存区";
  }
}

async function scopedDiff(repo, scope, rel) {
  const fileRel = filePath(rel);
  const changes = await readStatus(repo);
  const entry = changes.find((item) => item.path === fileRel);
  if (!entry) throw new Error(`这个文件没有改动：${fileRel}`);
  let patch = "";
  if (scope === "unstaged") {
    if (!isUnstaged(entry)) throw new Error(`这个文件没有未暂存的改动：${fileRel}`);
    if (entry.worktree === "?" || entry.index === "?") patch = untrackedPatch(repo, fileRel);
    else patch = (await git(repo, ["diff", "--no-color", "--", fileRel], { okCodes: [0, 1] })).stdout;
  } else {
    if (!isStaged(entry)) throw new Error(`这个文件没有已暂存的改动：${fileRel}`);
    patch = (await git(repo, ["diff", "--cached", "--no-color", "--", fileRel], { okCodes: [0, 1] })).stdout;
  }
  let truncated = false;
  if (patch.length > 500000) {
    patch = patch.slice(0, 500000);
    truncated = true;
  }
  const capped = diff.capFiles(diff.parse(patch), truncated);
  const matched = capped.files.filter((file) => file.path === fileRel);
  const shown = matched.length ? matched : capped.files.slice(0, 1);
  for (const file of shown) {
    file.side = diff.sideBySide(file);
    applySideNotes(file, scope);
  }
  return {
    rev: "",
    scope,
    file: fileRel,
    hints: diff.summarize(shown, { truncated: capped.truncated }),
    files: shown,
  };
}

async function commitPatch(repo, rev) {
  const id = sha(rev);
  try {
    return (await git(repo, ["show", "-m", "--first-parent", "--format=", "--no-color", id], { okCodes: [0, 1] })).stdout;
  } catch {
    return (await git(repo, ["show", "--format=", "--no-color", "--root", id], { okCodes: [0, 1] })).stdout;
  }
}

async function parentCount(repo, rev) {
  try {
    const text = (await git(repo, ["rev-list", "--parents", "-n", "1", sha(rev)])).stdout.trim();
    const parts = text.split(/\s+/);
    return Math.max(0, parts.length - 1);
  } catch {
    return 0;
  }
}

async function contains(repo, rev, target) {
  if (!target) return false;
  try {
    await git(repo, ["merge-base", "--is-ancestor", sha(rev), target]);
    return true;
  } catch {
    return false;
  }
}

async function diffPage(repo, rev, target, extra) {
  const scope = extra && extra.scope;
  if (scope === "unstaged" || scope === "staged") return scopedDiff(repo, scope, extra.file || "");
  const dirty = (await git(repo, ["status", "--porcelain"])).stdout.trim().length > 0;
  let patch = "";
  let parents = 0;
  let shown = "";
  let onTarget = false;
  if (!rev) patch = await worktreePatch(repo);
  else {
    shown = (await git(repo, ["rev-parse", sha(rev)])).stdout.trim();
    patch = await commitPatch(repo, shown);
    parents = await parentCount(repo, shown);
    onTarget = await contains(repo, shown, target);
  }
  let truncated = false;
  if (patch.length > 500000) {
    patch = patch.slice(0, 500000);
    truncated = true;
  }
  const capped = diff.capFiles(diff.parse(patch), truncated);
  for (const file of capped.files) file.side = diff.sideBySide(file);
  return {
    rev: shown,
    hints: diff.summarize(capped.files, { parents, dirty, onTarget, truncated: capped.truncated }),
    files: capped.files,
  };
}

async function withAuth(repo, tokens, fn) {
  const url = await originUrl(repo);
  const auth = authFor(url, tokens);
  return fn(auth);
}

async function commitFiles(repo, id, text) {
  await assertBranch(repo);
  const changes = await readStatus(repo);
  if (!changes.some(isStaged)) throw new Error("没有已暂存的文件");
  await git(repo, ["commit", "-m", message(text)]);
  const head = (await git(repo, ["rev-parse", "--short", "HEAD"])).stdout.trim();
  return done(repo, id, `已提交 ${head}`);
}

async function chosenPaths(repo, files, mode) {
  if (!files || !files.length) throw new Error(mode === "stage" ? "先选择要暂存的文件" : "先选择要取消暂存的文件");
  const changes = await readStatus(repo);
  const paths = [];
  for (const file of files) {
    const rel = filePath(file);
    const entry = changes.find((item) => item.path === rel);
    if (!entry) throw new Error(`这个文件没有改动：${rel}`);
    if (isConflict(entry)) throw new Error(mode === "stage" ? `冲突文件不能暂存：${rel}` : `冲突文件不能取消暂存：${rel}`);
    if (mode === "unstage" && !isStaged(entry)) throw new Error(`这个文件不在暂存区：${rel}`);
    paths.push(rel);
  }
  return paths;
}

async function stageFiles(repo, id, files) {
  const paths = await chosenPaths(repo, files, "stage");
  await git(repo, ["add", "--", ...paths]);
  return done(repo, id, paths.length > 1 ? `已暂存 ${paths.length} 个文件` : `已暂存 ${paths[0]}`);
}

async function unstageFiles(repo, id, files) {
  const paths = await chosenPaths(repo, files, "unstage");
  await git(repo, ["restore", "--staged", "--", ...paths]);
  return done(repo, id, paths.length > 1 ? `已取消暂存 ${paths.length} 个文件` : `已取消暂存 ${paths[0]}`);
}

async function fetch(repo, id, tokens) {
  await requireOrigin(repo);
  await withAuth(repo, tokens, (auth) => git(repo, ["fetch", "origin"], { ...auth, timeout: 90000 }));
  return done(repo, id, "已获取远程更新");
}

async function pull(repo, id, tokens) {
  await assertClean(repo);
  const branch = await assertBranch(repo);
  await requireOrigin(repo);
  try {
    await withAuth(repo, tokens, (auth) =>
      git(repo, ["pull", "--no-rebase", "--no-edit", "origin", branch], { ...auth, timeout: 120000 })
    );
  } catch (error) {
    await git(repo, ["merge", "--abort"]).catch(() => {});
    throw new Error(conflictText("拉取", error.message));
  }
  return done(repo, id, "已拉取");
}

async function push(repo, id, tokens) {
  const branch = await assertBranch(repo);
  await requireOrigin(repo);
  try {
    await withAuth(repo, tokens, (auth) => git(repo, ["push", "-u", "origin", "HEAD"], { ...auth, timeout: 120000 }));
  } catch (error) {
    throw new Error(scrub(error.message, tokens.githubToken || tokens.giteeToken) || "推送被拒绝");
  }
  return done(repo, id, `已推送 ${branch}`);
}

async function createBranch(repo, id, name) {
  const branch = branchName(name);
  await git(repo, ["switch", "-c", branch]);
  return done(repo, id, `已创建并检出 ${branch}`);
}

async function checkout(repo, id, name, options = {}) {
  const dirty = (await git(repo, ["status", "--porcelain"])).stdout.trim().length > 0;
  if (!dirty) {
    const landed = await switchTo(repo, name);
    return done(repo, id, `已检出 ${landed}`);
  }
  if (!options.stash) throw new Error("工作区有未提交改动，请先提交");
  const from = await currentBranch(repo);
  const fromSha = (await git(repo, ["rev-parse", "HEAD"])).stdout.trim();
  const entry = await pushStash(repo, from || "游离 HEAD");
  let landed;
  try {
    landed = await switchTo(repo, name);
  } catch (error) {
    try {
      await returnTo(repo, from, fromSha);
      await applyStash(repo, entry);
    } catch {
      await cleanConflict(repo).catch(() => {});
    }
    throw error;
  }
  const applied = await applyStash(repo, entry);
  if (applied.conflict) return done(repo, id, "已切到目标分支，贮藏还原冲突已取消，贮藏还在，工作区是干净的");
  return done(repo, id, `已检出 ${landed}`);
}

async function deleteBranch(repo, id, name) {
  const branch = branchName(name);
  if (await isRemoteBranch(repo, branch)) throw new Error("这里只删除本地分支");
  const current = await currentBranch(repo);
  if (branch === current) throw new Error("不能删除当前分支");
  try {
    await git(repo, ["branch", "-d", branch]);
  } catch (error) {
    if (/not fully merged/i.test(error.message)) throw new Error("分支还没合并，不能删除");
    throw error;
  }
  return done(repo, id, `已删除 ${branch}`);
}

async function merge(repo, id, branch) {
  const name = branchName(branch);
  await assertClean(repo);
  const current = await assertBranch(repo);
  if (name === current) throw new Error("不能合并当前分支");
  try {
    await git(repo, ["merge", "--no-edit", name]);
  } catch (error) {
    await git(repo, ["merge", "--abort"]).catch(() => git(repo, ["reset", "--hard", "HEAD"]));
    throw new Error(conflictText("合并", error.message));
  }
  return done(repo, id, `已合并 ${name}`);
}

async function rebase(repo, id, onto) {
  const name = branchName(onto);
  await assertClean(repo);
  const current = await assertBranch(repo);
  if (name === current) throw new Error("不能变基到当前分支");
  try {
    await git(repo, ["rebase", name]);
  } catch (error) {
    await git(repo, ["rebase", "--abort"]).catch(() => git(repo, ["reset", "--hard", "HEAD"]));
    throw new Error(conflictText("变基", error.message));
  }
  return done(repo, id, `已变基到 ${name}`);
}

async function cherryPick(repo, id, rev, target) {
  const commit = sha(rev);
  await assertClean(repo);
  const current = await assertBranch(repo);
  const landed = !target || target === current ? current : await switchTo(repo, target);
  try {
    await git(repo, ["cherry-pick", commit]);
  } catch (error) {
    await git(repo, ["cherry-pick", "--abort"]).catch(() => git(repo, ["reset", "--hard", "HEAD"]));
    if (landed !== current) await git(repo, ["switch", current]).catch(() => {});
    throw new Error(conflictText("拣选", error.message));
  }
  if (landed !== current) await git(repo, ["switch", current]);
  const shortSha = commit.slice(0, 7);
  const notice = landed === current ? `已拣选 ${shortSha}` : `已拣选 ${shortSha} 到 ${landed}，当前仍在 ${current}`;
  return done(repo, id, notice);
}

async function cherryPickPartial(repo, id, rev, target, picks, text) {
  const commit = sha(rev);
  const selectedPicks = (picks || [])
    .filter((pick) => pick && pick.file)
    .map((pick) => ({ file: filePath(pick.file), index: Number(pick.index) }));
  if (!selectedPicks.length) throw new Error("先勾选要拣选的片段");
  await assertClean(repo);
  const current = await assertBranch(repo);
  const patch = diff.select(await commitPatch(repo, commit), selectedPicks);
  if (!patch.trim()) throw new Error("没有可拣选的片段");
  const landed = !target || target === current ? current : await switchTo(repo, target);
  const file = path.join(os.tmpdir(), `local-git-${process.pid}-${Date.now()}.patch`);
  try {
    fs.writeFileSync(file, patch);
    await git(repo, ["apply", "--index", "--whitespace=nowarn", file]);
    const msg = text && String(text).trim() ? message(text) : `拣选 ${commit.slice(0, 7)} 的部分改动`;
    await git(repo, ["commit", "-m", msg]);
  } catch (error) {
    await git(repo, ["reset", "--hard", "HEAD"]).catch(() => {});
    await git(repo, ["clean", "-fd"]).catch(() => {});
    if (landed !== current) await git(repo, ["switch", current]).catch(() => {});
    throw error.message && /nothing to commit|无文件要提交|nothing added/.test(error.message)
      ? new Error("这些片段在目标分支上没有产生改动")
      : error;
  } finally {
    fs.rmSync(file, { force: true });
  }
  if (landed !== current) await git(repo, ["switch", current]);
  let notice = `已把 ${selectedPicks.length} 个片段拣选到 ${landed}`;
  if (landed !== current) notice += `，当前仍在 ${current}`;
  return done(repo, id, notice);
}

async function listRemotes(repo) {
  try {
    const text = (await git(repo, ["remote"])).stdout;
    return text
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean)
      .sort((a, b) => b.length - a.length);
  } catch {
    return [];
  }
}

async function refExists(repo, ref) {
  try {
    await git(repo, ["show-ref", "--verify", "--quiet", ref]);
    return true;
  } catch {
    return false;
  }
}

async function isRemoteBranch(repo, branch) {
  const remote = (await listRemotes(repo)).find((item) => branch.startsWith(`${item}/`));
  if (!remote) return false;
  return refExists(repo, `refs/remotes/${branch}`);
}

async function switchTo(repo, name) {
  const branch = branchName(name);
  const remote = (await listRemotes(repo)).find((item) => branch.startsWith(`${item}/`));
  if (remote && (await refExists(repo, `refs/remotes/${branch}`))) {
    const localName = branchName(branch.slice(remote.length + 1));
    if (await refExists(repo, `refs/heads/${localName}`)) await git(repo, ["switch", localName]);
    else await git(repo, ["switch", "--track", branch]);
    return localName;
  }
  await git(repo, ["switch", branch]);
  return branch;
}

async function listStashes(repo) {
  let text = "";
  try {
    text = (await git(repo, ["stash", "list", "--format=%gd%x1f%H%x1f%s"])).stdout;
  } catch {
    return [];
  }
  const out = [];
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const bits = line.split("\x1f");
    const selector = bits[0] || "";
    const id = bits[1] || "";
    const messageText = bits.slice(2).join("\x1f");
    if (!/^stash@\{\d{1,6}\}$/.test(selector)) continue;
    if (!/^[0-9a-f]{7,40}$/i.test(id)) continue;
    out.push({ id, selector, message: messageText || "" });
  }
  return out;
}

async function resolveStash(repo, raw) {
  const id = stashRef(raw);
  const list = await listStashes(repo);
  const found = list.filter((item) => item.id === id || item.selector === id || (id.length >= 7 && item.id.startsWith(id)));
  if (found.length !== 1) throw new Error("没有这条贮藏");
  if (!/^stash@\{\d{1,6}\}$/.test(found[0].selector)) throw new Error("贮藏编号不对");
  return found[0];
}

async function pushStash(repo, label) {
  const before = new Set((await listStashes(repo)).map((item) => item.id));
  await git(repo, ["stash", "push", "-u", "-m", `从 ${label} 检出前`]);
  const created = (await listStashes(repo)).find((item) => !before.has(item.id));
  if (!created) throw new Error("贮藏失败，没有切换");
  return created;
}

async function cleanConflict(repo) {
  await git(repo, ["reset", "--hard", "HEAD"]).catch(() => {});
  let left = (await git(repo, ["status", "--porcelain"])).stdout.trim();
  if (left) {
    await git(repo, ["clean", "-fd"]).catch(() => {});
    left = (await git(repo, ["status", "--porcelain"])).stdout.trim();
  }
  if (left) throw new Error("贮藏还原冲突已取消，但工作区仍不干净");
}

async function applyStash(repo, entry) {
  const current = await resolveStash(repo, entry.id);
  try {
    await git(repo, ["stash", "apply", current.selector]);
  } catch {
    await cleanConflict(repo);
    return { conflict: true };
  }
  const text = (await git(repo, ["status", "--porcelain"])).stdout;
  const conflicted = text.split(/\r?\n/).some((line) => line.length >= 2 && (line[0] === "U" || line[1] === "U"));
  if (conflicted) {
    await cleanConflict(repo);
    return { conflict: true };
  }
  const again = await resolveStash(repo, entry.id);
  await git(repo, ["stash", "drop", again.selector]);
  return { conflict: false };
}

async function returnTo(repo, from, fromSha) {
  try {
    const now = await currentBranch(repo);
    const nowSha = (await git(repo, ["rev-parse", "HEAD"])).stdout.trim();
    if (from) {
      if (now !== from) await git(repo, ["switch", from]);
    } else if (nowSha !== fromSha) await git(repo, ["switch", "--detach", fromSha]);
  } catch {
    await git(repo, ["merge", "--abort"]).catch(() => {});
    await git(repo, ["rebase", "--abort"]).catch(() => {});
    await git(repo, ["cherry-pick", "--abort"]).catch(() => {});
    await git(repo, ["reset", "--hard", fromSha]).catch(() => {});
    if (from) await git(repo, ["switch", from]).catch(() => {});
    await git(repo, ["clean", "-fd"]).catch(() => {});
    const left = (await git(repo, ["status", "--porcelain"])).stdout.trim();
    if (left) throw new Error("切回原分支失败");
  }
}

async function applyStashAction(repo, id, raw) {
  await assertClean(repo);
  const entry = await resolveStash(repo, raw);
  const applied = await applyStash(repo, entry);
  if (applied.conflict) return done(repo, id, "贮藏还原冲突已取消，贮藏还在，工作区是干净的");
  return done(repo, id, "已还原贮藏");
}

async function dropStash(repo, id, raw) {
  const entry = await resolveStash(repo, raw);
  await git(repo, ["stash", "drop", entry.selector]);
  return done(repo, id, "已丢弃贮藏");
}

async function showStash(repo, raw) {
  const entry = await resolveStash(repo, raw);
  let patch = "";
  try {
    patch = (await git(repo, ["stash", "show", "-p", "--no-color", entry.selector], { okCodes: [0, 1] })).stdout;
  } catch {
    throw new Error("打不开这条贮藏的差异");
  }
  const capped = diff.capFiles(diff.parse(patch), false);
  for (const file of capped.files) file.side = diff.sideBySide(file);
  return {
    id: entry.id,
    message: entry.message,
    hints: diff.summarize(capped.files),
    files: capped.files,
  };
}

async function restoreOne(repo, entry) {
  if (entry.untracked) {
    removeWorktreeFile(repo, entry.path);
    return;
  }
  if (entry.orig) {
    await git(repo, ["restore", "--source=HEAD", "--worktree", "--staged", "--", entry.orig]);
    try {
      await git(repo, ["restore", "--source=HEAD", "--worktree", "--staged", "--", entry.path]);
    } catch {
      removeWorktreeFile(repo, entry.path);
    }
    return;
  }
  try {
    await git(repo, ["restore", "--source=HEAD", "--worktree", "--staged", "--", entry.path]);
  } catch (error) {
    if (entry.code.includes("A")) {
      await git(repo, ["reset", "-q", "HEAD", "--", entry.path]).catch(() => {});
      removeWorktreeFile(repo, entry.path);
      return;
    }
    throw error;
  }
}

async function restoreFiles(repo, id, files) {
  if (!files || !files.length) throw new Error("先选择要回滚的文件");
  const entries = await statusEntries(repo);
  const picked = [];
  const seen = new Set();
  for (const file of files) {
    const rel = filePath(file);
    const entry = entries.find((item) => item.path === rel || item.orig === rel);
    if (!entry) throw new Error(`这个文件没有改动：${rel}`);
    const key = `${entry.orig}\0${entry.path}`;
    if (seen.has(key)) continue;
    seen.add(key);
    picked.push(entry);
  }
  for (const entry of picked) await restoreOne(repo, entry);
  return done(repo, id, picked.length > 1 ? `已回滚 ${picked.length} 个文件` : `已回滚 ${picked[0].path}`);
}

async function applyReversePatch(repo, rel, patchText) {
  const abs = insideRepo(repo, filePath(rel));
  let existed = false;
  let before = null;
  try {
    const st = fs.lstatSync(abs);
    if (!st.isDirectory()) {
      existed = true;
      before = fs.readFileSync(abs);
    }
  } catch {
    existed = false;
  }
  const file = path.join(os.tmpdir(), `local-git-hunk-${process.pid}-${Date.now()}.patch`);
  try {
    fs.writeFileSync(file, patchText);
    await git(repo, ["apply", "-R", "--whitespace=nowarn", file]);
  } catch {
    try {
      if (existed) fs.writeFileSync(abs, before);
      else removeWorktreeFile(repo, rel);
      for (const rej of [abs + ".rej", path.join(repo, path.basename(abs) + ".rej")]) {
        if (fs.existsSync(rej)) fs.unlinkSync(rej);
      }
    } catch {
      /* 还原字节失败时仍把 apply 的错误抛出去 */
    }
    throw new Error("这段回滚失败，文件已还原");
  } finally {
    fs.rmSync(file, { force: true });
  }
}

async function restoreHunk(repo, id, file, index) {
  const rel = filePath(file);
  const hunk = Number(index);
  if (!Number.isInteger(hunk) || hunk < 0) throw new Error("没有这段差异");
  const entries = await statusEntries(repo);
  if (!entries.some((entry) => entry.path === rel || entry.orig === rel)) throw new Error(`这个文件没有改动：${rel}`);
  const patchText = diff.select(await unstagedPatch(repo), [{ file: rel, index: hunk }]);
  if (!patchText.trim()) throw new Error("没有这段差异");
  await applyReversePatch(repo, rel, patchText);
  return done(repo, id, `已回滚 ${rel} 的一个 hunk`);
}

async function requireOrigin(repo) {
  if (!(await originUrl(repo))) throw new Error("还没有 origin 远程");
}

async function done(repo, id, notice) {
  return { notice, workspace: await workspace(repo, id) };
}

function conflictText(action, detail) {
  if (/conflict/i.test(detail || "")) return `${action}冲突，已取消`;
  return detail || `${action}没有完成，已取消`;
}

async function cloneRepo(parent, remote, tokens) {
  let folder = remote.name;
  let dest = path.join(parent, folder);
  if (fs.existsSync(dest)) {
    folder = `${remote.owner}-${remote.name}`;
    dest = path.join(parent, folder);
    if (fs.existsSync(dest)) throw new Error("这个目录里已经有同名仓库");
  }
  const auth = authFor(remote.url, tokens);
  await git(parent, ["clone", remote.url, folder], { ...auth, timeout: 180000 });
  return folder;
}

async function describe(repo) {
  let branch = "";
  let remote = "";
  try {
    branch = await currentBranch(repo);
    if (!branch) {
      const head = (await git(repo, ["rev-parse", "--short", "HEAD"])).stdout.trim();
      branch = head;
    }
    remote = providerOf(await originUrl(repo));
    if (remote === "ssh") remote = "ssh";
  } catch {
    branch = "";
  }
  return { branch, remote };
}

module.exports = {
  git,
  parseStatus,
  parseStatusZ,
  isStaged,
  isUnstaged,
  workspace,
  log,
  diffPage,
  commitFiles,
  stageFiles,
  unstageFiles,
  fetch,
  pull,
  push,
  createBranch,
  checkout,
  deleteBranch,
  merge,
  rebase,
  cherryPick,
  cherryPickPartial,
  restoreFiles,
  restoreHunk,
  applyReversePatch,
  applyStashAction,
  dropStash,
  showStash,
  cloneRepo,
  describe,
  originUrl,
};
