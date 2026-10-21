"use strict";

const fs = require("fs");
const path = require("path");
const { bedPrefixes, hostLabel, parseGitRemote, rawUrl, redact, resolveHost } = require("./host");
const { MAX_BYTES, inspectFile } = require("./image-info");
const { createDecider, rewriteContent } = require("./links");
const { normKey, toPosix } = require("./paths");
const { walk } = require("./scan");
const { scanFolders } = require("./scan");
const { buildPlan } = require("./sync-plan");
const { createStore } = require("./store");
const { isTyporaRunning } = require("./typora");
const {
  applyImagePlan,
  assertRepo,
  commitPaths,
  currentBranch,
  git,
  openImageRepo,
  peekImageRepo,
  publishFiles,
  pushBranch,
  readManifest,
  remoteUrl,
} = require("./git-repo");

const DOC_EXTS = new Set([".md", ".markdown", ".html", ".htm"]);

function createService(appRoot) {
  const store = createStore(appRoot);

  function preserveSet(locals, sources) {
    const byAbs = new Map();
    for (const [sha, list] of Object.entries(sources.items || {})) {
      for (const item of list || []) byAbs.set(normKey(item.abs), sha);
    }
    const keep = new Set();
    for (const local of locals) {
      if (!local.tooBig) continue;
      const sha = byAbs.get(normKey(local.abs));
      if (sha) keep.add(sha);
    }
    return keep;
  }

  function sourcesFrom(plan, prev, preserve) {
    const items = {};
    for (const group of [...plan.uploads, ...plan.reuses]) {
      items[group.sha256] = group.sources.map((item) => ({
        rootId: item.rootId,
        rel: item.rel,
        abs: path.resolve(item.abs),
        name: item.name,
      }));
    }
    for (const sha of preserve) {
      if (!items[sha] && prev.items?.[sha]) items[sha] = prev.items[sha];
    }
    return { items };
  }

  function logPlan(log, plan) {
    const reusedPaths = plan.reuses.reduce((sum, item) => sum + item.sources.length, 0);
    const folded = plan.uploads.reduce((sum, item) => sum + Math.max(0, item.sources.length - 1), 0);
    log(
      `比对完成：新增 ${plan.uploads.length}，复用 ${reusedPaths}，合并重复 ${folded}，清除 ${plan.deletes.length}，跳过 ${plan.skipped.length}`
    );
    for (const item of plan.uploads) {
      const clash = item.nameClash ? "，同名但内容不同" : "";
      log(`新增 ${item.local.name} → ${item.remotePath}（${item.local.size} 字节，${item.local.width}x${item.local.height}${clash}）`);
      const extra = item.sources.slice(1).map((source) => source.name);
      if (extra.length) log(`相同内容不重复上传：${extra.join("、")}`);
    }
    for (const item of plan.reuses) {
      const names = item.sources.map((source) => source.name).join("、");
      const sample = item.sources[0];
      log(`复用 ${names} ← ${item.image.path}（SHA-256 相同，${sample.size} 字节，${sample.width}x${sample.height}）`);
    }
    for (const item of plan.deletes) log(`清除 ${item.name} ← ${item.path}`);
    for (const item of plan.skipped) log(`跳过 ${item.name}，超过 50MB`);
  }

  function syncMessage(plan) {
    if (plan.uploads.length && plan.deletes.length) return `picbed: 整理图片 +${plan.uploads.length} -${plan.deletes.length}`;
    if (plan.uploads.length) return `picbed: 新增 ${plan.uploads.length} 张图片`;
    if (plan.deletes.length) return `picbed: 清除 ${plan.deletes.length} 张失效图片`;
    return "picbed: 整理图片";
  }

  function counts(plan) {
    return {
      upload: plan.uploads.length,
      reuse: plan.reuses.reduce((sum, item) => sum + item.sources.length, 0),
      remove: plan.deletes.length,
      skip: plan.skipped.length,
    };
  }

  async function connect(config) {
    if (!config.repoPath) throw new Error("请先填写本地仓库路径");
    const top = await assertRepo(config.repoPath);
    const url = await remoteUrl(top, config.remote);
    const identity = resolveHost(config.provider, url, config.owner, config.repo);
    return { top, identity };
  }

  async function sync({ dryRun = false, log = () => {} } = {}) {
    const config = store.loadConfig();
    if (!config.watchFolders.length) throw new Error("请先填写监听文件夹");
    const { top, identity } = await connect(config);
    log(`${hostLabel(identity.provider)} ${identity.owner}/${identity.repo}`);
    const image = dryRun ? await peekImageRepo(top, config.branch) : await openImageRepo(top, config.branch);
    if (image.created) log(`已为图片分支 ${image.branch} 建了单独目录 ${image.root}`);
    else if (image.separate) log(`图片分支 ${image.branch} 在 ${image.root}`);
    else if (dryRun) log(`预览当前分支 ${image.branch}`);
    const manifest = image.missing ? [] : readManifest(image.root, config.imageDir);
    const locals = scanFolders(config.watchFolders);
    const preserve = preserveSet(locals, store.loadSources());
    const plan = buildPlan(locals, manifest, { imageDir: config.imageDir, preserveShas: preserve });
    logPlan(log, plan);
    if (dryRun) return counts(plan);
    const changed = applyImagePlan(image.root, config.imageDir, plan, log);
    const committed = await commitPaths(image.root, changed, syncMessage(plan));
    store.saveSources(sourcesFrom(plan, store.loadSources(), preserve));
    log(committed ? "已在本地提交，尚未推送" : "图片没有变化");
    return { ...counts(plan), committed };
  }

  async function hasHead(repo) {
    try {
      await git(repo, ["rev-parse", "--verify", "--quiet", "HEAD"]);
      return true;
    } catch {
      return false;
    }
  }

  async function unpushedCommits(top, remote, branch) {
    try {
      const out = await git(top, ["rev-list", "--count", `${remote}/${branch}..HEAD`]);
      return Number(out.stdout.trim()) || 0;
    } catch {
      return null;
    }
  }

  async function publish({ log = () => {} } = {}) {
    const synced = await sync({ log });
    const config = store.loadConfig();
    const image = await openImageRepo(config.repoPath, config.branch);
    const ahead = await unpushedCommits(image.root, config.remote, config.branch);
    const pendingPush = ahead == null && (await hasHead(image.root));
    if (synced.committed || ahead > 0 || pendingPush) {
      const pushed = await push(log);
      return { ...synced, pushed };
    }
    const linked = await updateLinks({ log });
    if (!linked.changed) {
      log("没有需要推送的图片或链接");
      return { ...synced, pushed: false, changed: 0 };
    }
    const pushed = await push(log);
    return { ...synced, pushed, changed: linked.changed };
  }

  function relFor(folder, abs, folders) {
    const rel = toPosix(path.relative(folder.path, abs));
    if (folders.length < 2) return rel;
    const base = path.basename(folder.path);
    const dup = folders.filter((item) => path.basename(item.path) === base).length > 1;
    return `${dup ? `${base}-${folder.id}` : base}/${rel}`;
  }

  async function remotePaths(repo, remote, branch) {
    const ref = `${remote}/${branch}`;
    try {
      await git(repo, ["rev-parse", "--verify", "--quiet", `${ref}^{commit}`]);
    } catch {
      return null;
    }
    const listed = await git(repo, ["ls-tree", "-r", "--name-only", ref]);
    return new Set(listed.stdout.split(/\r?\n/).filter(Boolean));
  }

  async function updateLinks({ log = () => {} } = {}) {
    const config = store.loadConfig();
    if (!config.docFolders.length) throw new Error("请先填写文档文件夹");
    const { identity } = await connect(config);
    const image = await openImageRepo(config.repoPath, config.branch);
    if (image.separate) log(`图片分支 ${image.branch} 在 ${image.root}`);
    const branch = image.branch;
    const manifest = readManifest(image.root, config.imageDir);
    const onRemote = await remotePaths(image.root, config.remote, branch);
    const knownUrls = new Set();
    const urlBySha = new Map();
    const urlByPath = new Map();
    let waiting = 0;
    for (const img of manifest) {
      const url = rawUrl({ ...identity, branch, filePath: img.path });
      if (!onRemote || !onRemote.has(img.path)) {
        waiting += 1;
        knownUrls.add(url);
        continue;
      }
      knownUrls.add(url);
      urlByPath.set(img.path, url);
      urlBySha.set(img.sha256, url);
    }
    if (waiting) log(`有 ${waiting} 张图片还没推送，文档里先留着本地路径`);
    const sourceSha = new Map();
    for (const [sha, list] of Object.entries(store.loadSources().items || {})) {
      for (const item of list || []) sourceSha.set(normKey(item.abs), sha);
    }
    const alsoBranches = [];
    for (const folder of config.docFolders) {
      try {
        const name = await currentBranch(await assertRepo(folder.path));
        if (name && name !== branch) alsoBranches.push(name);
      } catch {
        // 文档目录不是 git 仓库时，只认图片分支上的链接
      }
    }
    const prefixes = bedPrefixes({ ...identity, branch, alsoBranches });
    const classifyFile = (abs) => {
      if (!fs.existsSync(abs) || !fs.statSync(abs).isFile()) return { missing: true };
      const size = fs.statSync(abs).size;
      const sha = size > MAX_BYTES ? sourceSha.get(normKey(abs)) : inspectFile(abs).sha256;
      if (sha && urlBySha.has(sha)) return { url: urlBySha.get(sha) };
      return null;
    };
    const changed = [];
    let deferred = 0;
    const typoraOpen = isTyporaRunning();
    for (const folder of config.docFolders) {
      if (!folder.path || !fs.existsSync(folder.path) || !fs.statSync(folder.path).isDirectory()) {
        throw new Error(`文档文件夹不存在：${folder.path || ""}`);
      }
      walk(folder.path, (abs) => {
        if (!DOC_EXTS.has(path.extname(abs).toLowerCase())) return;
        const buf = fs.readFileSync(abs);
        if (buf.includes(0)) {
          log(`跳过二进制文件 ${abs}`);
          return;
        }
        const original = buf.toString("utf8");
        const decide = createDecider({
          fileDir: path.dirname(abs),
          bedPrefixes: prefixes,
          knownUrls,
          urlByPath,
          clearMissing: config.clearMissingLinks,
          classifyFile,
        });
        const next = rewriteContent(original, decide);
        if (next === original) return;
        if (typoraOpen) {
          deferred += 1;
          return;
        }
        fs.writeFileSync(abs, next);
        changed.push({ abs, rel: relFor(folder, abs, config.docFolders), rootId: folder.id });
        log(`已更新 ${abs}`);
      });
    }
    if (typoraOpen && deferred) {
      store.saveDeferred({ needed: true, at: new Date().toISOString(), count: deferred });
      log(`Typora 未关闭，已推迟改写 ${deferred} 个文档；关掉后再写回`);
      return { changed: 0, deferred, pending: (store.loadPending().files || []).length };
    }
    if (!typoraOpen) store.saveDeferred({ needed: false, at: "", count: 0 });
    const pending = store.loadPending();
    const merged = new Map((pending.files || []).map((file) => [normKey(file.abs), file]));
    for (const file of changed) merged.set(normKey(file.abs), file);
    store.savePending({ files: [...merged.values()], at: new Date().toISOString() });
    log(changed.length ? `链接已更新 ${changed.length} 个文件` : "没有需要更新的链接");
    return { changed: changed.length, deferred: 0, pending: merged.size };
  }

  async function publishDocs(config, files, log) {
    const groups = new Map();
    for (const file of files) {
      let top;
      try {
        top = await assertRepo(path.dirname(file.abs));
      } catch {
        top = await assertRepo(config.repoPath);
      }
      const key = path.resolve(top);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(file);
    }
    let last = null;
    for (const [top, group] of groups) {
      const current = await currentBranch(top);
      const branch = config.push.branch || current;
      if (branch !== current) log(`文档将提交到分支 ${branch}`);
      last = await publishFiles({
        repoPath: top,
        branch,
        directory: config.push.directory,
        remote: config.remote,
        files: group,
        message: config.push.message,
        doPush: true,
        log,
      });
      log(last.committed ? "文档已提交" : "文档没有新的改动");
      if (last.pushOutput) log(last.pushOutput);
      else if (last.committed) log("已推送");
    }
    return last;
  }

  async function push(log = () => {}) {
    const config = store.loadConfig();
    const { identity } = await connect(config);
    const image = await openImageRepo(config.repoPath, config.branch);
    log(`推送到 ${hostLabel(identity.provider)} ${identity.owner}/${identity.repo}`);
    if (image.separate) log(`图片分支 ${image.branch} 在 ${image.root}`);
    let pushed = false;
    if (await hasHead(image.root)) {
      const result = await pushBranch(image.root, image.branch, config.remote, log);
      if (result.pushOutput) log(result.pushOutput);
      pushed = true;
    } else {
      log("图片分支还没有提交");
    }
    const linked = await updateLinks({ log });
    const files = (store.loadPending().files || []).filter((file) => fs.existsSync(file.abs));
    if (!files.length) {
      if (!linked.changed) log(pushed ? "图片已推送" : "没有新的文档要提交");
      return { committed: false, pushed, branch: image.branch };
    }
    const published = await publishDocs(config, files, log);
    return published || { committed: false, pushed, branch: image.branch };
  }

  async function describe() {
    const config = store.loadConfig();
    const pending = store.loadPending();
    const run = store.loadRun();
    const payload = {
      config,
      remoteUrl: "",
      originProvider: "",
      hostError: "",
      hostWarning: "",
      pushStatus: { ahead: null, label: "未知" },
      identity: null,
      images: [],
      pending,
      run,
    };
    if (!config.repoPath) return payload;
    let top = "";
    let url = "";
    try {
      top = await assertRepo(config.repoPath);
      url = await remoteUrl(top, config.remote);
      payload.remoteUrl = redact(url);
      const parsed = parseGitRemote(url);
      payload.originProvider = parsed?.provider || "";
      if (parsed?.provider && parsed.provider !== config.provider) {
        payload.hostWarning = `当前 origin 实际是 ${hostLabel(parsed.provider)}（${payload.remoteUrl}）。选 Gitee 时 origin 必须是 gitee.com，否则图片会推到另一家。`;
      } else if (payload.remoteUrl) {
        payload.hostWarning = `当前 origin：${payload.remoteUrl}`;
      }
    } catch (err) {
      payload.hostError = err.message;
    }
    try {
      if (!top) throw new Error(payload.hostError || "仓库不可用");
      const identity = resolveHost(config.provider, url, config.owner, config.repo);
      payload.remoteUrl = identity.remoteUrl;
      payload.identity = { provider: identity.provider, owner: identity.owner, repo: identity.repo };
      payload.hostError = "";
      if (!payload.hostWarning) payload.hostWarning = `当前 origin：${identity.remoteUrl}`;
      const image = await peekImageRepo(top, config.branch);
      payload.imageRoot = image.separate ? image.root : "";
      payload.imageNote = image.missing
        ? `图片分支 ${image.branch} 会单独放到 ${image.root}`
        : image.separate
          ? `图片分支 ${image.branch} 在 ${image.root}`
          : "";
      const sources = store.loadSources();
      const manifestRoot = image.missing ? top : image.root;
      const urlBranch = image.missing ? await currentBranch(top) : image.branch;
      const onRemote = image.missing ? null : await remotePaths(image.root, config.remote, image.branch);
      const ahead = image.missing ? null : await unpushedCommits(image.root, config.remote, image.branch);
      if (ahead == null && !onRemote) {
        payload.pushStatus = { ahead: null, label: "远程分支未见（需先推送）" };
      } else if (ahead > 0) {
        payload.pushStatus = { ahead, label: `已提交未推送（${ahead} 个提交）` };
      } else if (ahead === 0) {
        payload.pushStatus = { ahead: 0, label: "已推送" };
      } else {
        payload.pushStatus = { ahead: null, label: "推送状态未知" };
      }
      payload.images = readManifest(manifestRoot, config.imageDir).map((img) => {
        const remoteUrlForImg = rawUrl({ ...identity, branch: urlBranch, filePath: img.path });
        let status = "unknown";
        let statusLabel = "未知";
        if (!onRemote) {
          status = "local";
          statusLabel = "已提交未推送";
        } else if (onRemote.has(img.path)) {
          status = "pushed";
          statusLabel = "已推送";
        } else {
          status = "local";
          statusLabel = "已提交未推送";
        }
        return {
          ...img,
          url: status === "pushed" ? remoteUrlForImg : "",
          pendingUrl: status === "pushed" ? "" : remoteUrlForImg,
          status,
          statusLabel,
          sources: (sources.items?.[img.sha256] || []).map((item) => item.rel || item.name),
        };
      });
    } catch (err) {
      if (!payload.hostError) payload.hostError = err.message;
      try {
        if (fs.existsSync(config.repoPath)) {
          payload.images = readManifest(config.repoPath, config.imageDir).map((img) => ({
            ...img,
            url: "",
            status: "unknown",
            statusLabel: "不可用",
            sources: [],
          }));
        }
      } catch {
        payload.images = [];
      }
    }
    return payload;
  }

  async function flushDeferredDocs({ log = () => {} } = {}) {
    const state = store.loadDeferred();
    if (!state.needed) return { skipped: true, reason: "none" };
    if (isTyporaRunning()) return { skipped: true, reason: "typora" };
    log("Typora 已关闭，开始补写文档链接");
    const linked = await updateLinks({ log });
    const files = (store.loadPending().files || []).filter((file) => fs.existsSync(file.abs));
    if (!files.length) return { ...linked, published: false };
    const config = store.loadConfig();
    const published = await publishDocs(config, files, log);
    return { ...linked, published: Boolean(published && published.committed) };
  }

  return { store, sync, updateLinks, push, publish, describe, flushDeferredDocs };
}

module.exports = { createService };
