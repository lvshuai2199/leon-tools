"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const os = require("os");
const path = require("path");
const { cloneRoot, parseRemote, repoId, resolveRepo } = require("../lib/paths");

test("克隆目录必须在这台电脑上，且不能是磁盘根目录", () => {
  assert.equal(cloneRoot("D:\\GitFiles"), path.resolve("D:\\GitFiles"));
  assert.throws(() => cloneRoot("D:\\"), /磁盘根目录/);
  assert.throws(() => cloneRoot("repos"), /绝对路径/);
  assert.throws(() => cloneRoot("C:\\Windows\\Temp"), /不能用来放仓库/);
});

test("仓库路径不能逃出仓库目录", () => {
  const root = path.join(os.tmpdir(), "local-git-root");
  assert.equal(repoId("."), ".");
  assert.equal(resolveRepo(root, "."), path.resolve(root));
  assert.throws(() => repoId("../outside"), /仓库路径不对/);
  assert.throws(() => resolveRepo(root, "../outside"), /仓库路径不对|不在这个目录/);
});

test("只接受 GitHub 和 Gitee 的 https 地址", () => {
  const github = parseRemote("https://github.com/leon/tools.git");
  assert.equal(github.provider, "github");
  assert.equal(github.url, "https://github.com/leon/tools.git");
  const gitee = parseRemote("https://gitee.com/leon/tools");
  assert.equal(gitee.provider, "gitee");
  assert.throws(() => parseRemote("git@github.com:leon/tools.git"), /https/);
  assert.throws(() => parseRemote("https://gitlab.com/leon/tools.git"), /只支持/);
});
