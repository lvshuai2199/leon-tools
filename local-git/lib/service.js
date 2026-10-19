"use strict";

const fs = require("fs");
const path = require("path");
const git = require("./git");
const { cloneRoot, isInside, parseRemote, repoId, resolveRepo, toPosix } = require("./paths");

function createService(rootDir, store) {
  let chain = Promise.resolve();

  function enqueue(fn) {
    const run = chain.then(fn, fn);
    chain = run.then(
      () => undefined,
      () => undefined
    );
    return run;
  }

  function config() {
    return store.read();
  }

  function rootOrThrow() {
    const cfg = config();
    if (!cfg.cloneRoot) throw new Error("先填写仓库目录");
    const root = cloneRoot(cfg.cloneRoot);
    fs.mkdirSync(root, { recursive: true });
    return root;
  }

  function openRepo(id) {
    const root = rootOrThrow();
    const repo = resolveRepo(root, id);
    if (!fs.existsSync(path.join(repo, ".git"))) throw new Error("不是 Git 仓库");
    const realBase = fs.realpathSync(root);
    const real = fs.realpathSync(repo);
    if (!isInside(realBase, real)) throw new Error("仓库不在这个目录里");
    return { id: repoId(id), dir: real };
  }

  function listRepos(root) {
    const realBase = fs.realpathSync(root);
    if (fs.existsSync(path.join(realBase, ".git"))) {
      return [{ id: ".", name: path.basename(realBase), dir: realBase }];
    }
    const out = [];
    walk(root, 3, root, out);
    return out;
  }

  function walk(dir, depth, base, out) {
    if (depth < 0) return;
    let entries = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const ent of entries) {
      if (!ent.isDirectory() || ent.name === ".git" || ent.name === "node_modules") continue;
      const full = path.join(dir, ent.name);
      if (fs.existsSync(path.join(full, ".git"))) {
        let real = full;
        try {
          real = fs.realpathSync(full);
          if (!isInside(fs.realpathSync(base), real)) continue;
        } catch {
          continue;
        }
        out.push({ id: toPosix(path.relative(base, full)), name: ent.name, dir: real });
        continue;
      }
      if (depth > 0) walk(full, depth - 1, base, out);
    }
  }

  async function home() {
    const cfg = config();
    const body = {
      cloneRoot: cfg.cloneRoot,
      ready: false,
      warning: "",
      github: { configured: Boolean(cfg.githubToken), hint: store.hint(cfg.githubToken) },
      gitee: { configured: Boolean(cfg.giteeToken), hint: store.hint(cfg.giteeToken) },
      repos: [],
    };
    if (!cfg.cloneRoot) return body;
    try {
      const root = rootOrThrow();
      body.ready = true;
      const repos = [];
      for (const item of listRepos(root)) {
        const info = await git.describe(item.dir);
        repos.push({ id: item.id, name: item.name, branch: info.branch, remote: info.remote });
      }
      repos.sort((a, b) => a.id.localeCompare(b.id));
      body.repos = repos;
    } catch (error) {
      body.warning = error.message || "仓库目录不可用";
    }
    return body;
  }

  function saveSettings(patch) {
    const saved = store.save(patch || {});
    if (saved.cloneRoot) fs.mkdirSync(cloneRoot(saved.cloneRoot), { recursive: true });
    return home();
  }

  function act(id, fn) {
    return enqueue(async () => {
      const repo = openRepo(id);
      return fn(repo.dir, repo.id, config());
    });
  }

  return {
    home: () => enqueue(home),
    saveSettings: (patch) => enqueue(() => saveSettings(patch)),
    clone(url) {
      return enqueue(async () => {
        const remote = parseRemote(url);
        const root = rootOrThrow();
        const folder = await git.cloneRepo(root, remote, config());
        const id = folder;
        const opened = openRepo(id);
        const op = { notice: `已克隆到 ${opened.dir}`, workspace: await git.workspace(opened.dir, id), repoId: id };
        return op;
      });
    },
    workspace: (id) => act(id, (dir, repoId) => git.workspace(dir, repoId)),
    log: (id, max, ref) => act(id, (dir) => git.log(dir, max, ref)),
    diff: (id, rev, target, scope, file) => act(id, (dir) => git.diffPage(dir, rev, target, { scope: scope || "", file: file || "" })),
    commit: (id, text) => act(id, (dir, repoId) => git.commitFiles(dir, repoId, text)),
    stage: (id, files) => act(id, (dir, repoId) => git.stageFiles(dir, repoId, files)),
    unstage: (id, files) => act(id, (dir, repoId) => git.unstageFiles(dir, repoId, files)),
    fetch: (id) => act(id, (dir, repoId, cfg) => git.fetch(dir, repoId, cfg)),
    pull: (id) => act(id, (dir, repoId, cfg) => git.pull(dir, repoId, cfg)),
    push: (id, remote, branch) => act(id, (dir, repoId, cfg) => git.push(dir, repoId, cfg, { remote, branch })),
    branch: (id, name) => act(id, (dir, repoId) => git.createBranch(dir, repoId, name)),
    branchAt: (id, name, rev) => act(id, (dir, repoId) => git.createBranchAt(dir, repoId, name, rev)),
    checkout: (id, name, stash) => act(id, (dir, repoId) => git.checkout(dir, repoId, name, { stash: stash === true })),
    deleteBranch: (id, name, force) => act(id, (dir, repoId) => git.deleteBranch(dir, repoId, name, { force: force === true })),
    renameBranch: (id, from, to) => act(id, (dir, repoId) => git.renameBranch(dir, repoId, from, to)),
    merge: (id, branch) => act(id, (dir, repoId) => git.merge(dir, repoId, branch)),
    rebase: (id, onto) => act(id, (dir, repoId) => git.rebase(dir, repoId, onto)),
    rebaseFrom: (id, rev, onto) => act(id, (dir, repoId) => git.rebaseFrom(dir, repoId, rev, onto)),
    historyRange: (id, mode, onto, rev) => act(id, (dir) => git.historyRange(dir, mode, onto, rev)),
    squash: (id, shas, text) => act(id, (dir, repoId) => git.squashTip(dir, repoId, shas, text)),
    rebaseSquash: (id, onto, shas, text) => act(id, (dir, repoId) => git.rebaseSquash(dir, repoId, onto, shas, text)),
    cherryPick: (id, rev, target) => act(id, (dir, repoId) => git.cherryPick(dir, repoId, rev, target)),
    cherryPickPartial: (id, rev, target, picks, text) =>
      act(id, (dir, repoId) => git.cherryPickPartial(dir, repoId, rev, target, picks, text)),
    restore: (id, files) => act(id, (dir, repoId) => git.restoreFiles(dir, repoId, files)),
    restoreHunk: (id, file, index) => act(id, (dir, repoId) => git.restoreHunk(dir, repoId, file, index)),
    stashApply: (id, stash) => act(id, (dir, repoId) => git.applyStashAction(dir, repoId, stash)),
    stashDrop: (id, stash) => act(id, (dir, repoId) => git.dropStash(dir, repoId, stash)),
    stashShow: (id, stash) => act(id, (dir) => git.showStash(dir, stash)),
  };
}

module.exports = { createService };
