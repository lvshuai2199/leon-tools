"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const git = require("../lib/git");

function gitCmd(repo, args) {
  execFileSync("git", args, { cwd: repo, windowsHide: true, stdio: "pipe" });
}

function initRepo(dir) {
  fs.mkdirSync(dir, { recursive: true });
  gitCmd(dir, ["init", "-b", "main"]);
  gitCmd(dir, ["config", "user.name", "Test"]);
  gitCmd(dir, ["config", "user.email", "t@example.com"]);
  gitCmd(dir, ["config", "commit.gpgsign", "false"]);
  gitCmd(dir, ["config", "core.autocrlf", "false"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "a1\na2\na3\n");
  fs.writeFileSync(path.join(dir, "b.txt"), "b1\n");
  gitCmd(dir, ["add", "."]);
  gitCmd(dir, ["commit", "-m", "init"]);
  return dir;
}

test("parseStatus 保留 index 和 worktree", () => {
  const changes = git.parseStatus(" M a.txt\nM  b.txt\nMM c.txt\n?? d.txt\nUU e.txt\nR  old.txt -> g.txt\n D h.txt\n");
  const by = Object.fromEntries(changes.map((item) => [item.path, item]));
  assert.deepEqual(by["a.txt"], { path: "a.txt", index: " ", worktree: "M" });
  assert.deepEqual(by["b.txt"], { path: "b.txt", index: "M", worktree: " " });
  assert.deepEqual(by["c.txt"], { path: "c.txt", index: "M", worktree: "M" });
  assert.deepEqual(by["d.txt"], { path: "d.txt", index: "?", worktree: "?" });
  assert.equal(git.isStaged(by["d.txt"]), false);
  assert.equal(git.isUnstaged(by["d.txt"]), true);
  assert.deepEqual(by["e.txt"], { path: "e.txt", index: "U", worktree: "U" });
  assert.equal(git.isStaged(by["e.txt"]), false);
  assert.equal(git.isUnstaged(by["e.txt"]), true);
  assert.equal(git.isStaged(by["c.txt"]), true);
  assert.equal(git.isUnstaged(by["c.txt"]), true);
  assert.deepEqual(by["g.txt"], { path: "g.txt", index: "R", worktree: " " });
  assert.deepEqual(by["h.txt"], { path: "h.txt", index: " ", worktree: "D" });
  assert.equal(changes.some((item) => Object.prototype.hasOwnProperty.call(item, "status")), false);
});

test("未暂存、已暂存、两边都有和未跟踪会分开", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-split-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "d.txt"), "d0\n");
  gitCmd(dir, ["add", "d.txt"]);
  gitCmd(dir, ["commit", "-m", "add d"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "unstaged\na2\na3\n");
  fs.writeFileSync(path.join(dir, "b.txt"), "staged\n");
  gitCmd(dir, ["add", "b.txt"]);
  fs.writeFileSync(path.join(dir, "d.txt"), "index\n");
  gitCmd(dir, ["add", "d.txt"]);
  fs.writeFileSync(path.join(dir, "d.txt"), "work\n");
  fs.writeFileSync(path.join(dir, "c.txt"), "new\n");
  const text = execFileSync("git", ["status", "--porcelain=v1", "-uall"], { cwd: dir, encoding: "utf8" });
  const parsed = git.parseStatus(text);
  const by = Object.fromEntries(parsed.map((item) => [item.path, item]));
  assert.equal(by["a.txt"].index, " ");
  assert.equal(by["a.txt"].worktree, "M");
  assert.equal(git.isStaged(by["a.txt"]), false);
  assert.equal(by["b.txt"].index, "M");
  assert.equal(by["b.txt"].worktree, " ");
  assert.equal(git.isUnstaged(by["b.txt"]), false);
  assert.equal(by["d.txt"].index, "M");
  assert.equal(by["d.txt"].worktree, "M");
  assert.equal(by["c.txt"].index, "?");
  assert.equal(by["c.txt"].worktree, "?");
  assert.equal(git.isStaged(by["c.txt"]), false);
  const stagedNew = await git.stageFiles(dir, "repo", ["c.txt"]);
  const neu = stagedNew.workspace.changes.find((item) => item.path === "c.txt");
  assert.equal(neu.index, "A");
  assert.equal(neu.worktree, " ");
  const back = await git.unstageFiles(dir, "repo", ["b.txt"]);
  const restored = back.workspace.changes.find((item) => item.path === "b.txt");
  assert.equal(restored.index, " ");
  assert.equal(restored.worktree, "M");
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "staged\n");
  const up = await git.stageFiles(dir, "repo", ["a.txt"]);
  const a = up.workspace.changes.find((item) => item.path === "a.txt");
  assert.equal(a.index, "M");
  assert.equal(a.worktree, " ");
});

test("只提交已暂存的文件", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-commit-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "a.txt"), "changed\na2\na3\n");
  fs.writeFileSync(path.join(dir, "c.txt"), "new\n");
  await git.stageFiles(dir, "repo", ["a.txt"]);
  const op = await git.commitFiles(dir, "repo", "only a", ["c.txt"]);
  assert.match(op.notice, /^已提交/);
  assert.equal(op.workspace.changes.length, 1);
  assert.equal(op.workspace.changes[0].path, "c.txt");
  assert.equal(op.workspace.changes[0].index, "?");
  assert.match(execFileSync("git", ["show", "HEAD:a.txt"], { cwd: dir, encoding: "utf8" }), /^changed/);
  assert.equal(fs.readFileSync(path.join(dir, "c.txt"), "utf8"), "new\n");
});

test("两边都有时提交的是暂存区", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-mm-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "a.txt"), "index\na2\na3\n");
  gitCmd(dir, ["add", "a.txt"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "work\na2\na3\n");
  const op = await git.commitFiles(dir, "repo", "index only");
  assert.match(op.notice, /^已提交/);
  assert.match(execFileSync("git", ["show", "HEAD:a.txt"], { cwd: dir, encoding: "utf8" }), /^index/);
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^work/);
  assert.equal(op.workspace.changes.length, 1);
  assert.equal(op.workspace.changes[0].path, "a.txt");
  assert.equal(op.workspace.changes[0].index, " ");
  assert.equal(op.workspace.changes[0].worktree, "M");
});

test("暂存区为空时提交失败", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-empty-index-${Date.now()}`));
  await assert.rejects(git.commitFiles(dir, "repo", "clean"), /没有已暂存的文件/);
  fs.writeFileSync(path.join(dir, "a.txt"), "changed\na2\na3\n");
  fs.writeFileSync(path.join(dir, "c.txt"), "new\n");
  await assert.rejects(git.commitFiles(dir, "repo", "dirty", ["a.txt"]), /没有已暂存的文件/);
  assert.equal(execFileSync("git", ["log", "-1", "--format=%s"], { cwd: dir, encoding: "utf8" }).trim(), "init");
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^changed/);
  assert.equal(fs.readFileSync(path.join(dir, "c.txt"), "utf8"), "new\n");
});

test("冲突文件不能暂存", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-conflict-stage-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "feature\n");
  gitCmd(dir, ["add", "a.txt"]);
  gitCmd(dir, ["commit", "-m", "feature"]);
  gitCmd(dir, ["switch", "main"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "master\n");
  gitCmd(dir, ["add", "a.txt"]);
  gitCmd(dir, ["commit", "-m", "master"]);
  assert.throws(() => gitCmd(dir, ["merge", "feature"]));
  const text = execFileSync("git", ["status", "--porcelain=v1"], { cwd: dir, encoding: "utf8" });
  const conflict = git.parseStatus(text).find((item) => item.path === "a.txt");
  assert.ok(conflict.index === "U" || conflict.worktree === "U");
  await assert.rejects(git.stageFiles(dir, "repo", ["a.txt"]), /冲突文件不能暂存/);
  assert.match(execFileSync("git", ["status", "--porcelain"], { cwd: dir, encoding: "utf8" }), /U/);
});

test("未暂存对比工作区和暂存区，已暂存对比暂存区和 HEAD", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-scope-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "a.txt"), "index\na2\na3\n");
  gitCmd(dir, ["add", "a.txt"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "work\na2\na3\n");
  fs.writeFileSync(path.join(dir, "c.txt"), "new\n");
  const unstaged = await git.diffPage(dir, "", "", { scope: "unstaged", file: "a.txt" });
  assert.equal(unstaged.files.length, 1);
  assert.equal(unstaged.files[0].side.leftNote, "暂存区");
  assert.equal(unstaged.files[0].side.rightNote, "工作区");
  const unstagedText = JSON.stringify(unstaged.files[0].side.rows);
  assert.match(unstagedText, /index/);
  assert.match(unstagedText, /work/);
  const staged = await git.diffPage(dir, "", "", { scope: "staged", file: "a.txt" });
  assert.equal(staged.files[0].side.leftNote, "HEAD");
  assert.equal(staged.files[0].side.rightNote, "暂存区");
  const stagedText = JSON.stringify(staged.files[0].side.rows);
  assert.match(stagedText, /index/);
  assert.doesNotMatch(stagedText, /work/);
  const fresh = await git.diffPage(dir, "", "", { scope: "unstaged", file: "c.txt" });
  assert.equal(fresh.files[0].side.leftNote, "暂存区没有");
  assert.equal(fresh.files[0].side.rightNote, "工作区");
  assert.match(JSON.stringify(fresh.files[0].side.rows), /new/);
  await assert.rejects(git.diffPage(dir, "", "", { scope: "staged", file: "c.txt" }), /没有已暂存/);
});

test("把一个文件的片段拣选到另一个分支", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-pick-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "A1\na2\na3\n");
  fs.writeFileSync(path.join(dir, "b.txt"), "B1\n");
  gitCmd(dir, ["add", "."]);
  gitCmd(dir, ["commit", "-m", "both"]);
  const sha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: dir, encoding: "utf8" }).trim();
  gitCmd(dir, ["switch", "main"]);
  gitCmd(dir, ["branch", "other"]);
  const page = await git.diffPage(dir, sha, "other");
  assert.match(page.hints[0], /2 个文件/);
  const op = await git.cherryPickPartial(dir, "pick", sha, "other", [{ file: "b.txt", index: 0 }], "pick b");
  assert.match(op.notice, /other/);
  assert.equal(op.workspace.branch, "main");
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "b1\n");
  gitCmd(dir, ["switch", "other"]);
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "B1\n");
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^a1/);
});

test("合并冲突会取消并保持当前分支", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-merge-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "feature\n");
  gitCmd(dir, ["add", "a.txt"]);
  gitCmd(dir, ["commit", "-m", "feature"]);
  gitCmd(dir, ["switch", "main"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "master\n");
  gitCmd(dir, ["add", "a.txt"]);
  gitCmd(dir, ["commit", "-m", "master"]);
  await assert.rejects(git.merge(dir, "merge", "feature"), /冲突/);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "master\n");
  const ws = await git.workspace(dir, "merge");
  assert.equal(ws.branch, "main");
  assert.equal(ws.changes.length, 0);
});

test("变基到 main 后两边的改动都在", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-rebase-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  fs.writeFileSync(path.join(dir, "c.txt"), "c\n");
  gitCmd(dir, ["add", "c.txt"]);
  gitCmd(dir, ["commit", "-m", "extra"]);
  gitCmd(dir, ["switch", "main"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "main\na2\na3\n");
  gitCmd(dir, ["add", "a.txt"]);
  gitCmd(dir, ["commit", "-m", "main-change"]);
  gitCmd(dir, ["switch", "feature"]);
  const op = await git.rebase(dir, "rebase", "main");
  assert.match(op.notice, /main/);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "main\na2\na3\n");
  assert.equal(fs.readFileSync(path.join(dir, "c.txt"), "utf8"), "c\n");
  const history = await git.log(dir, 10);
  assert.ok(history.commits.some((commit) => commit.message.startsWith("extra")));
});

test("脏工作区不贮藏时检出失败，文件不动", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-dirty-${Date.now()}`));
  gitCmd(dir, ["branch", "other"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "dirty\na2\na3\n");
  await assert.rejects(git.checkout(dir, "repo", "other"), /未提交/);
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^dirty/);
  assert.equal((await git.workspace(dir, "repo")).branch, "main");
});

test("贮藏并切换后目标分支看得到改动，贮藏列表为空", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-stash-${Date.now()}`));
  gitCmd(dir, ["branch", "other"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "dirty\na2\na3\n");
  fs.writeFileSync(path.join(dir, "c.txt"), "new\n");
  const op = await git.checkout(dir, "repo", "other", { stash: true });
  assert.equal(op.workspace.branch, "other");
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^dirty/);
  assert.equal(fs.readFileSync(path.join(dir, "c.txt"), "utf8"), "new\n");
  assert.equal(op.workspace.stashes.length, 0);
});

test("贮藏还原冲突后留在目标分支，工作区干净且贮藏还在", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-stash-conflict-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "feature\n");
  gitCmd(dir, ["add", "a.txt"]);
  gitCmd(dir, ["commit", "-m", "feature"]);
  gitCmd(dir, ["switch", "main"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "dirty\n");
  const op = await git.checkout(dir, "repo", "feature", { stash: true });
  assert.match(op.notice, /贮藏还原冲突已取消/);
  assert.equal(op.workspace.branch, "feature");
  assert.equal(op.workspace.changes.length, 0);
  assert.equal(execFileSync("git", ["status", "--porcelain"], { cwd: dir, encoding: "utf8" }).trim(), "");
  assert.ok(op.workspace.stashes.length >= 1);
  const shown = execFileSync("git", ["stash", "show", "-p"], { cwd: dir, encoding: "utf8" });
  assert.match(shown, /dirty/);
  const parsed = await git.showStash(dir, op.workspace.stashes[0].id);
  assert.ok(parsed.files.length >= 1);
});

test("拉取、合并、变基、拣选仍要求干净工作区", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-clean-ops-${Date.now()}`));
  gitCmd(dir, ["branch", "other"]);
  const sha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: dir, encoding: "utf8" }).trim();
  fs.writeFileSync(path.join(dir, "a.txt"), "dirty\na2\na3\n");
  await assert.rejects(git.pull(dir, "repo", {}), /未提交/);
  await assert.rejects(git.merge(dir, "repo", "other"), /未提交/);
  await assert.rejects(git.rebase(dir, "repo", "other"), /未提交/);
  await assert.rejects(git.cherryPick(dir, "repo", sha, "other"), /未提交/);
  await assert.rejects(git.cherryPickPartial(dir, "repo", sha, "other", [{ file: "a.txt", index: 0 }], "x"), /未提交/);
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^dirty/);
});

test("回滚已改文件时保留未跟踪文件，回滚未跟踪文件后工作区干净", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-restore-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "a.txt"), "changed\na2\na3\n");
  fs.writeFileSync(path.join(dir, "c.txt"), "new\n");
  const first = await git.restoreFiles(dir, "repo", ["a.txt"]);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "a1\na2\na3\n");
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "b1\n");
  assert.equal(fs.readFileSync(path.join(dir, "c.txt"), "utf8"), "new\n");
  assert.deepEqual(first.workspace.changes.map((item) => item.path), ["c.txt"]);
  const second = await git.restoreFiles(dir, "repo", ["c.txt"]);
  assert.equal(fs.existsSync(path.join(dir, "c.txt")), false);
  assert.equal(second.workspace.changes.length, 0);
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "b1\n");
});

test("回滚重命名后回到 HEAD 的原路径", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-rename-${Date.now()}`));
  gitCmd(dir, ["mv", "a.txt", "d.txt"]);
  const op = await git.restoreFiles(dir, "repo", ["d.txt"]);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "a1\na2\na3\n");
  assert.equal(fs.existsSync(path.join(dir, "d.txt")), false);
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "b1\n");
  assert.equal(op.workspace.changes.length, 0);
});

test("回滚已 add 未提交的文件", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-added-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "e.txt"), "e\n");
  gitCmd(dir, ["add", "e.txt"]);
  const op = await git.restoreFiles(dir, "repo", ["e.txt"]);
  assert.equal(fs.existsSync(path.join(dir, "e.txt")), false);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "a1\na2\na3\n");
  assert.equal(op.workspace.changes.length, 0);
});

test("本地斜杠分支不当成远程，其他远程可以检出跟踪", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-track-${Date.now()}`));
  const remote = initRepo(path.join(os.tmpdir(), `local-git-upstream-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature/foo"]);
  gitCmd(dir, ["switch", "main"]);
  let op = await git.checkout(dir, "repo", "feature/foo");
  assert.equal(op.workspace.branch, "feature/foo");
  gitCmd(remote, ["switch", "-c", "topic"]);
  gitCmd(dir, ["remote", "add", "upstream", remote]);
  gitCmd(dir, ["fetch", "upstream"]);
  op = await git.checkout(dir, "repo", "upstream/topic");
  assert.equal(op.workspace.branch, "topic");
  const names = [];
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.checkout) names.push(node.checkout);
      walk(node.children);
    }
  };
  for (const group of op.workspace.branchTree.groups) walk(group.nodes);
  assert.equal(names.includes("upstream/HEAD"), false);
  assert.ok(names.includes("upstream/topic"));
  gitCmd(dir, ["switch", "main"]);
  op = await git.checkout(dir, "repo", "upstream/topic");
  assert.equal(op.workspace.branch, "topic");
});

test("日志 ref 用 remote 名区分本地和远程，提交说明仍在", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-refs-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature/foo"]);
  fs.writeFileSync(path.join(dir, "c.txt"), "c\n");
  gitCmd(dir, ["add", "c.txt"]);
  gitCmd(dir, ["commit", "-m", "second"]);
  gitCmd(dir, ["tag", "v1"]);
  const remote = initRepo(path.join(os.tmpdir(), `local-git-refs-remote-${Date.now()}`));
  gitCmd(dir, ["remote", "add", "upstream", remote]);
  gitCmd(dir, ["fetch", "upstream"]);
  const history = await git.log(dir, 10);
  assert.ok(history.commits.some((commit) => commit.message.startsWith("init")));
  assert.equal(history.graph.length, history.commits.length);
  const refs = history.commits.flatMap((commit) => commit.refs);
  assert.equal(refs.find((ref) => ref.name === "feature/foo").kind, "local");
  assert.equal(refs.find((ref) => ref.name === "v1").kind, "tag");
  assert.ok(history.commits.every((commit) => Array.isArray(commit.parents)));
  assert.ok(history.commits.every((commit) => !commit.sha.includes("\n")));
  const known = new Set(history.commits.map((commit) => commit.sha));
  assert.ok(history.commits.some((commit) => commit.parents.some((parent) => known.has(parent))));
  assert.ok(history.graph.some((row) => (row.links || []).some((link) => link.kind !== "bottom")));
  assert.ok(history.commits.some((commit) => commit.parents.length === 0));
});

test("一个文件两段修改可以只回滚一段", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-hunk-${Date.now()}`));
  const text = Array.from({ length: 20 }, (_, index) => `L${index + 1}`).join("\n") + "\n";
  fs.writeFileSync(path.join(dir, "a.txt"), text);
  gitCmd(dir, ["add", "a.txt"]);
  gitCmd(dir, ["commit", "-m", "lines"]);
  const changed = text.replace("L2\n", "L2-changed\n").replace("L16\n", "L16-changed\n");
  fs.writeFileSync(path.join(dir, "a.txt"), changed);
  const page = await git.diffPage(dir, "", "");
  assert.equal(page.files[0].hunks.length, 2);
  await git.restoreHunk(dir, "repo", "a.txt", 0);
  const now = fs.readFileSync(path.join(dir, "a.txt"), "utf8");
  assert.match(now, /^L1\nL2\n/);
  assert.match(now, /L16-changed/);
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "b1\n");
});

test("反向 apply 失败时文件字节与操作前一致", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-apply-fail-${Date.now()}`));
  const abs = path.join(dir, "a.txt");
  const before = fs.readFileSync(abs);
  const bad = `diff --git a/a.txt b/a.txt
--- a/a.txt
+++ b/a.txt
@@ -1,3 +1,3 @@
 a1
-no-such-line
+other
 a3
`;
  await assert.rejects(git.applyReversePatch(dir, "a.txt", bad), /回滚失败/);
  assert.deepEqual(fs.readFileSync(abs), before);
});

test("工作区不干净时不能还原贮藏，已有改动还在", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-stash-dirty-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "a.txt"), "stashed\n");
  gitCmd(dir, ["stash", "push", "-u", "-m", "keep"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "keep-me\n");
  const ws = await git.workspace(dir, "repo");
  assert.equal(ws.stashes.length, 1);
  await assert.rejects(git.applyStashAction(dir, "repo", ws.stashes[0].id), /未提交/);
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^keep-me/);
  assert.equal((await git.workspace(dir, "repo")).stashes.length, 1);
});

test("贮藏编号拒绝随意字符串", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-stash-id-${Date.now()}`));
  await assert.rejects(git.dropStash(dir, "repo", "stash@{0};touch x"), /贮藏编号/);
  await assert.rejects(git.applyStashAction(dir, "repo", "../x"), /贮藏编号/);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "a1\na2\na3\n");
});

test("porcelain -z 认重命名的新旧路径", () => {
  const entries = git.parseStatusZ("R  d.txt\0a.txt\0?? c.txt\0");
  assert.equal(entries[0].path, "d.txt");
  assert.equal(entries[0].orig, "a.txt");
  assert.equal(entries[1].untracked, true);
  assert.equal(entries[1].path, "c.txt");
});

function rev(dir, spec = "HEAD") {
  return execFileSync("git", ["rev-parse", spec], { cwd: dir, encoding: "utf8" }).trim();
}

function subjectOf(dir, spec = "HEAD") {
  return execFileSync("git", ["log", "-1", "--format=%s", spec], { cwd: dir, encoding: "utf8" }).trim();
}

function porcelain(dir) {
  return execFileSync("git", ["status", "--porcelain"], { cwd: dir, encoding: "utf8" }).trim();
}

function branchOf(dir) {
  return execFileSync("git", ["branch", "--show-current"], { cwd: dir, encoding: "utf8" }).trim();
}

function commitFile(dir, file, content, message) {
  fs.writeFileSync(path.join(dir, file), content);
  gitCmd(dir, ["add", file]);
  gitCmd(dir, ["commit", "-m", message]);
}

function progressLeft(dir) {
  const gitDir = path.join(dir, ".git");
  return ["CHERRY_PICK_HEAD", "MERGE_HEAD", "rebase-merge", "rebase-apply", "sequencer"].some((name) => fs.existsSync(path.join(gitDir, name)));
}

test("当前分支只有根提交时不能合成", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-range-${Date.now()}`));
  const range = await git.historyRange(dir, "squash");
  assert.deepEqual(range.commits, []);
  assert.match(range.reason, /至少要有两笔自己的提交才能合成/);
});

test("三笔提交只合成最新两笔", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-squash-${Date.now()}`));
  const oldest = rev(dir);
  gitCmd(dir, ["branch", "other", oldest]);
  commitFile(dir, "a.txt", "second\n", "second");
  commitFile(dir, "b.txt", "third\n", "third");
  const beforeTree = rev(dir, "HEAD^{tree}");
  const otherSha = rev(dir, "other");
  const range = await git.historyRange(dir, "squash");
  assert.equal(range.commits.length, 2);
  assert.equal(range.commits[0].sha, rev(dir));
  assert.equal(range.commits[0].message, "third");
  assert.equal(range.commits[0].parentCount, 1);
  assert.equal(range.commits.some((commit) => commit.sha === oldest), false);
  assert.ok(range.commits.every((commit) => !commit.sha.includes("\n") && commit.onRemote === false));
  const op = await git.squashTip(dir, "repo", [range.commits[0].sha, range.commits[1].sha], "合成这两笔");
  assert.match(op.notice, /已把 2 笔合成一个/);
  assert.equal(rev(dir, "HEAD^"), oldest);
  assert.equal(rev(dir, "HEAD^{tree}"), beforeTree);
  assert.equal(subjectOf(dir), "合成这两笔");
  assert.equal(rev(dir, "other"), otherSha);
  assert.equal(porcelain(dir), "");
  assert.equal(op.workspace.branch, "main");
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "second\n");
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "third\n");
});

test("不连续、只有一笔或含根提交时拒绝且不改仓库", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-squash-reject-${Date.now()}`));
  const root = rev(dir);
  commitFile(dir, "a.txt", "second\n", "second");
  commitFile(dir, "b.txt", "third\n", "third");
  commitFile(dir, "c.txt", "fourth\n", "fourth");
  const range = await git.historyRange(dir, "squash");
  assert.equal(range.commits.length, 3);
  const head = rev(dir);
  const clean = porcelain(dir);
  await assert.rejects(git.squashTip(dir, "repo", [range.commits[0].sha, range.commits[2].sha], "跳过"), /中间不能空/);
  await assert.rejects(git.squashTip(dir, "repo", [range.commits[0].sha], "一笔"), /中间不能空/);
  await assert.rejects(git.squashTip(dir, "repo", [range.commits[0].sha, range.commits[1].sha, range.commits[2].sha, root], "含根"), /中间不能空/);
  assert.equal(rev(dir), head);
  assert.equal(porcelain(dir), clean);
  assert.equal(subjectOf(dir), "fourth");
});

test("含合并提交不能合成", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-squash-merge-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  commitFile(dir, "a.txt", "feature\n", "feature-edit");
  const featureSha = rev(dir);
  gitCmd(dir, ["switch", "main"]);
  commitFile(dir, "b.txt", "main\n", "main-edit");
  gitCmd(dir, ["merge", "--no-edit", "feature"]);
  commitFile(dir, "c.txt", "after\n", "after");
  const head = rev(dir);
  const merge = rev(dir, "HEAD^");
  const replay = await git.historyRange(dir, "replay", "feature");
  assert.equal(replay.hasMerges, true);
  assert.equal(replay.commits.some((commit) => commit.sha === merge), false);
  await assert.rejects(git.squashTip(dir, "repo", [head, merge], "不要合成合并"), /合并提交/);
  assert.equal(rev(dir), head);
  assert.equal(porcelain(dir), "");
  assert.equal(rev(dir, "feature"), featureSha);
  assert.equal(subjectOf(dir), "after");
});

test("脏工作区不能合成也不能接到后面", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-squash-dirty-${Date.now()}`));
  gitCmd(dir, ["branch", "other"]);
  commitFile(dir, "c.txt", "c\n", "second");
  commitFile(dir, "d.txt", "d\n", "third");
  const head = rev(dir);
  fs.writeFileSync(path.join(dir, "a.txt"), "dirty\n");
  await assert.rejects(git.squashTip(dir, "repo", [], ""), /未提交/);
  await assert.rejects(git.rebaseSquash(dir, "repo", "other", [], ""), /未提交/);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "dirty\n");
  assert.equal(rev(dir), head);
  assert.equal(progressLeft(dir), false);
});

test("接到后面时把最新连续几笔合成一个", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-replay-${Date.now()}`));
  const fork = rev(dir);
  commitFile(dir, "a.txt", "main\n", "main-extra");
  const mainSha = rev(dir);
  gitCmd(dir, ["switch", "-c", "feature", fork]);
  commitFile(dir, "c.txt", "A\n", "A");
  commitFile(dir, "b.txt", "B\n", "B");
  commitFile(dir, "d.txt", "C\n", "C");
  const range = await git.historyRange(dir, "replay", "main");
  assert.deepEqual(range.commits.map((commit) => commit.message), ["C", "B", "A"]);
  assert.equal(range.truncated, false);
  assert.equal(range.hasMerges, false);
  const op = await git.rebaseSquash(dir, "repo", "main", [range.commits[0].sha, range.commits[1].sha], "BC一起");
  assert.match(op.notice, /已接到 main 后面，并把 2 笔合成一个/);
  assert.equal(branchOf(dir), "feature");
  assert.equal(op.workspace.branch, "feature");
  assert.equal(rev(dir, "main"), mainSha);
  assert.equal(subjectOf(dir), "BC一起");
  assert.equal(subjectOf(dir, "HEAD^"), "A");
  assert.equal(rev(dir, "HEAD^^"), mainSha);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "main\n");
  assert.equal(fs.readFileSync(path.join(dir, "c.txt"), "utf8"), "A\n");
  assert.equal(fs.readFileSync(path.join(dir, "b.txt"), "utf8"), "B\n");
  assert.equal(fs.readFileSync(path.join(dir, "d.txt"), "utf8"), "C\n");
  assert.equal(porcelain(dir), "");
  assert.equal(progressLeft(dir), false);
});

test("接到后面发生冲突时回到操作前", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-replay-conflict-${Date.now()}`));
  const fork = rev(dir);
  commitFile(dir, "a.txt", "main\n", "main-extra");
  const mainSha = rev(dir);
  gitCmd(dir, ["switch", "-c", "feature", fork]);
  commitFile(dir, "c.txt", "A\n", "A");
  commitFile(dir, "a.txt", "feature\n", "B");
  commitFile(dir, "d.txt", "C\n", "C");
  const head = rev(dir);
  const shas = execFileSync("git", ["rev-list", "--reverse", "main..HEAD"], { cwd: dir, encoding: "utf8" })
    .trim()
    .split(/\r?\n/);
  await assert.rejects(git.rebaseSquash(dir, "repo", "main", [shas[2], shas[1]], "不要"), /接到后面没有完成，已取消/);
  assert.equal(rev(dir), head);
  assert.equal(branchOf(dir), "feature");
  assert.equal(porcelain(dir), "");
  assert.equal(rev(dir, "main"), mainSha);
  assert.equal(progressLeft(dir), false);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "feature\n");
});

test("空 shas 不能合成也不能接到后面", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-empty-shas-${Date.now()}`));
  gitCmd(dir, ["branch", "other"]);
  commitFile(dir, "c.txt", "c\n", "second");
  commitFile(dir, "d.txt", "d\n", "third");
  const head = rev(dir);
  await assert.rejects(git.squashTip(dir, "repo", [], "有说明"), /中间不能空/);
  await assert.rejects(git.rebaseSquash(dir, "repo", "other", [], "有说明"), /中间不能空/);
  assert.equal(rev(dir), head);
  assert.equal(porcelain(dir), "");
  assert.equal(subjectOf(dir), "third");
});

test("push 的参数仍是 push -u origin HEAD", async () => {
  const source = fs.readFileSync(path.join(__dirname, "../lib/git.js"), "utf8");
  assert.match(source, /\["push", "-u", "origin", "HEAD"\]/);
  assert.doesNotMatch(source, /push",\s*"--force/);
  const dir = initRepo(path.join(os.tmpdir(), `local-git-push-${Date.now()}`));
  const bare = path.join(os.tmpdir(), `local-git-bare-${Date.now()}`);
  execFileSync("git", ["init", "--bare", "-b", "main", bare], { windowsHide: true, stdio: "pipe" });
  gitCmd(dir, ["remote", "add", "origin", bare]);
  const op = await git.push(dir, "repo", {});
  assert.match(op.notice, /已推送/);
  assert.equal(execFileSync("git", ["rev-parse", "--abbrev-ref", "@{upstream}"], { cwd: dir, encoding: "utf8" }).trim(), "origin/main");
});

test("新建分支可以带着未提交改动", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-carry-${Date.now()}`));
  fs.writeFileSync(path.join(dir, "a.txt"), "dirty\na2\na3\n");
  const op = await git.createBranch(dir, "repo", "topic");
  assert.equal(op.workspace.branch, "topic");
  assert.match(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), /^dirty/);
});

test("logArgs 空 ref 用 --all，有 ref 不用 --all", () => {
  const all = git.logArgs("", 10);
  assert.equal(all[0], "log");
  assert.equal(all[1], "--all");
  assert.ok(all.includes("--topo-order"));
  assert.equal(all[all.indexOf("-n") + 1], "11");
  for (const ref of ["feature", "upstream/topic"]) {
    const args = git.logArgs(ref, 10);
    assert.equal(args[1], ref);
    assert.equal(args.includes("--all"), false);
    assert.ok(args.includes("--topo-order"));
    assert.equal(args[args.indexOf("-n") + 1], "11");
  }
});

test("单分支日志只走到这条 ref，另一条独有提交不在里面", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-logref-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  commitFile(dir, "c.txt", "feature\n", "feature-only");
  gitCmd(dir, ["switch", "main"]);
  commitFile(dir, "d.txt", "main\n", "main-only");
  const all = await git.log(dir, 10);
  assert.ok(all.commits.some((commit) => commit.message.startsWith("feature-only")));
  assert.ok(all.commits.some((commit) => commit.message.startsWith("main-only")));
  const mainLog = await git.log(dir, 10, "main");
  assert.equal(mainLog.commits.some((commit) => commit.message.startsWith("feature-only")), false);
  assert.ok(mainLog.commits.some((commit) => commit.message.startsWith("main-only")));
  const featureLog = await git.log(dir, 10, "feature");
  assert.ok(featureLog.commits.some((commit) => commit.message.startsWith("feature-only")));
  assert.ok(featureLog.commits.some((commit) => commit.message.startsWith("init")));
  assert.equal(featureLog.commits.some((commit) => commit.message.startsWith("main-only")), false);
  assert.equal(featureLog.graph.length, featureLog.commits.length);
  assert.ok(featureLog.graph.some((row) => (row.links || []).some((link) => link.kind !== "bottom")));
});

test("远程 ref 的日志包含那条远程提交", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-logremote-${Date.now()}`));
  const remote = initRepo(path.join(os.tmpdir(), `local-git-logremote-up-${Date.now()}`));
  gitCmd(remote, ["switch", "-c", "topic"]);
  commitFile(remote, "t.txt", "topic\n", "remote-topic");
  gitCmd(dir, ["remote", "add", "upstream", remote]);
  gitCmd(dir, ["fetch", "upstream"]);
  const history = await git.log(dir, 10, "upstream/topic");
  assert.ok(history.commits.some((commit) => commit.message.startsWith("remote-topic")));
});

test("非法 ref 和未知分支不改 HEAD", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-logbad-${Date.now()}`));
  const head = rev(dir);
  for (const bad of ["a..b", "a b", "a\\b", "origin/HEAD"]) {
    await assert.rejects(git.log(dir, 10, bad), /分支名不对/);
    assert.equal(rev(dir), head);
  }
  await assert.rejects(git.log(dir, 10, "no-such-branch"), /没有这条分支/);
  assert.equal(rev(dir), head);
  assert.equal(branchOf(dir), "main");
});

test("单分支超过上限时 truncated 且只留上限条数", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-logcap-${Date.now()}`));
  commitFile(dir, "c.txt", "c\n", "second");
  const page = await git.log(dir, 1, "main");
  assert.equal(page.truncated, true);
  assert.equal(page.commits.length, 1);
});

test("提交差异列出全部文件，只展开请求的那一个", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-difffile-${Date.now()}`));
  commitFile(dir, "c.txt", "c\n", "add-c");
  fs.writeFileSync(path.join(dir, "a.txt"), "changed\n");
  fs.writeFileSync(path.join(dir, "b.txt"), "changed-b\n");
  gitCmd(dir, ["add", "."]);
  gitCmd(dir, ["commit", "-m", "two-files"]);
  const sha = rev(dir);
  const page = await git.diffPage(dir, sha, "");
  assert.equal(page.files.length, 2);
  assert.deepEqual(page.files.map((file) => file.path).sort(), ["a.txt", "b.txt"]);
  const opened = page.files.filter((file) => (file.hunks || []).length > 0);
  assert.equal(opened.length, 1);
  const other = page.files.find((file) => file.path !== opened[0].path);
  const again = await git.diffPage(dir, sha, "", { file: other.path });
  assert.equal(again.files.length, 2);
  assert.ok((again.files.find((file) => file.path === other.path).hunks || []).length >= 1);
  assert.equal((again.files.find((file) => file.path === opened[0].path).hunks || []).length, 0);
  assert.equal(again.file, other.path);
});

test("从选定提交变基，更早的不跟着走，目标分支不动", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-from-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  commitFile(dir, "d.txt", "stay\n", "stay");
  const stay = rev(dir);
  gitCmd(dir, ["branch", "kept", stay]);
  commitFile(dir, "b.txt", "b-feature\n", "from-b");
  const start = rev(dir);
  commitFile(dir, "c.txt", "c\n", "from-c");
  gitCmd(dir, ["switch", "main"]);
  commitFile(dir, "a.txt", "main\na2\na3\n", "main-change");
  const mainSha = rev(dir);
  gitCmd(dir, ["switch", "feature"]);
  const op = await git.rebaseFrom(dir, "repo", start, "main");
  assert.match(op.notice, /开始变基到 main/);
  assert.equal(branchOf(dir), "feature");
  assert.equal(rev(dir, "main"), mainSha);
  assert.equal(rev(dir, "kept"), stay);
  assert.equal(fs.readFileSync(path.join(dir, "a.txt"), "utf8"), "main\na2\na3\n");
  assert.equal(fs.readFileSync(path.join(dir, "c.txt"), "utf8"), "c\n");
  const history = await git.log(dir, 20, "feature");
  const messages = history.commits.map((commit) => commit.message);
  assert.ok(messages.includes("from-b"));
  assert.ok(messages.includes("from-c"));
  assert.ok(messages.includes("main-change"));
  assert.equal(messages.includes("stay"), false);
});

test("不在当前分支上不能从这里变基", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-from-off-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  commitFile(dir, "c.txt", "c\n", "only-feature");
  gitCmd(dir, ["switch", "main"]);
  commitFile(dir, "a.txt", "main\na2\na3\n", "only-main");
  const mainSha = rev(dir);
  gitCmd(dir, ["switch", "feature"]);
  const head = rev(dir);
  await assert.rejects(git.rebaseFrom(dir, "repo", mainSha, "main"), /不在当前分支/);
  assert.equal(rev(dir), head);
  assert.equal(branchOf(dir), "feature");
});

test("从这里变基冲突会取消", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-from-conflict-${Date.now()}`));
  gitCmd(dir, ["switch", "-c", "feature"]);
  commitFile(dir, "a.txt", "feature\na2\na3\n", "feature-a");
  const start = rev(dir);
  gitCmd(dir, ["switch", "main"]);
  commitFile(dir, "a.txt", "main\na2\na3\n", "main-a");
  gitCmd(dir, ["switch", "feature"]);
  const head = rev(dir);
  await assert.rejects(git.rebaseFrom(dir, "repo", start, "main"), /冲突|已取消/);
  assert.equal(rev(dir), head);
  assert.equal(branchOf(dir), "feature");
  assert.equal((await git.workspace(dir, "repo")).changes.length, 0);
});

test("没合并的本地分支要确认后才删除，另一条指着同一提交的分支还在", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-del-${Date.now()}`));
  const base = rev(dir);
  gitCmd(dir, ["switch", "-c", "side"]);
  commitFile(dir, "c.txt", "c\n", "only-on-side");
  const sideTip = rev(dir);
  gitCmd(dir, ["branch", "same", sideTip]);
  gitCmd(dir, ["switch", "main"]);
  await assert.rejects(git.deleteBranch(dir, "repo", "same"), /还没合并/);
  assert.equal(branchOf(dir), "main");
  assert.equal(rev(dir), base);
  await git.deleteBranch(dir, "repo", "same", { force: true });
  const names = execFileSync("git", ["branch", "--format=%(refname:short)"], { cwd: dir, windowsHide: true, encoding: "utf8" });
  assert.equal(names.includes("same"), false);
  assert.equal(rev(dir, "side"), sideTip);
  assert.equal(branchOf(dir), "main");
  await assert.rejects(git.deleteBranch(dir, "repo", "main", { force: true }), /不能删除当前分支/);
});

test("从某次提交新建分支，当前分支和 HEAD 不动", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-branch-at-${Date.now()}`));
  const first = rev(dir);
  commitFile(dir, "c.txt", "c\n", "second");
  const head = rev(dir);
  const op = await git.createBranchAt(dir, "repo", "from-old", first);
  assert.match(op.notice, /from-old/);
  assert.equal(branchOf(dir), "main");
  assert.equal(rev(dir), head);
  assert.equal(rev(dir, "from-old"), first);
  await assert.rejects(git.createBranchAt(dir, "repo", "from-old", first), /已经有这个分支/);
  assert.equal(rev(dir), head);
});

test("可以改本地分支的名字，当前分支和提交不动", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-rename-${Date.now()}`));
  gitCmd(dir, ["branch", "topic"]);
  const head = rev(dir);
  const op = await git.renameBranch(dir, "repo", "topic", "topic-2");
  assert.match(op.notice, /topic-2/);
  assert.equal(branchOf(dir), "main");
  assert.equal(rev(dir), head);
  assert.equal(rev(dir, "topic-2"), head);
  await assert.rejects(git.renameBranch(dir, "repo", "topic-2", "main"), /已经有这个分支/);
  assert.equal(branchOf(dir), "main");
  const renamed = await git.renameBranch(dir, "repo", "main", "trunk");
  assert.match(renamed.notice, /trunk/);
  assert.equal(branchOf(dir), "trunk");
  assert.equal(rev(dir), head);
});

test("远程分支不能改名", async () => {
  const dir = initRepo(path.join(os.tmpdir(), `local-git-rename-remote-${Date.now()}`));
  const head = rev(dir);
  gitCmd(dir, ["remote", "add", "origin", "https://example.com/example.git"]);
  gitCmd(dir, ["update-ref", "refs/remotes/origin/topic", head]);
  await assert.rejects(git.renameBranch(dir, "repo", "origin/topic", "topic-2"), /只改本地分支/);
  assert.equal(rev(dir), head);
  assert.equal(branchOf(dir), "main");
});
