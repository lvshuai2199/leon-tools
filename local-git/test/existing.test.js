"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { createStore } = require("../lib/store");
const { createService } = require("../lib/service");

function initRepo(dir) {
  fs.mkdirSync(dir, { recursive: true });
  const git = (args) => execFileSync("git", args, { cwd: dir, windowsHide: true, stdio: "pipe" });
  git(["init", "-b", "main"]);
  git(["config", "user.name", "Test"]);
  git(["config", "user.email", "t@example.com"]);
  git(["config", "commit.gpgsign", "false"]);
  fs.writeFileSync(path.join(dir, "a.txt"), "a\n");
  git(["add", "."]);
  git(["commit", "-m", "init"]);
}

test("列出目录里已有的仓库，目录本身是仓库时也能打开", async () => {
  const base = fs.mkdtempSync(path.join(os.tmpdir(), "local-git-have-"));
  const parent = path.join(base, "repos");
  initRepo(path.join(parent, "demo"));
  const storeDir = path.join(base, "cfg");
  fs.mkdirSync(storeDir);
  const store = createStore(storeDir);
  store.save({ cloneRoot: parent });
  const service = createService(storeDir, store);
  const home = await service.home();
  assert.equal(home.repos.length, 1);
  assert.equal(home.repos[0].id, "demo");
  assert.equal(home.repos[0].branch, "main");

  const alone = path.join(base, "alone");
  initRepo(alone);
  store.save({ cloneRoot: alone });
  const opened = await service.home();
  assert.deepEqual(opened.repos.map((repo) => repo.id), ["."]);
  const ws = await service.workspace(".");
  assert.equal(ws.branch, "main");
  assert.equal(ws.id, ".");
});
