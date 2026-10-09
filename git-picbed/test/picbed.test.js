"use strict";

const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { describe, it, after } = require("node:test");
const { imageSize, inspectBuffer } = require("../lib/image-info");
const { resolveHost, rawUrl } = require("../lib/host");
const { buildPlan } = require("../lib/sync-plan");
const { rewriteContent, createDecider } = require("../lib/links");
const { createStore, scheduleDue } = require("../lib/store");
const { git, publishFiles } = require("../lib/git-repo");
const { createService } = require("../lib/service");

const temps = [];

function tmp() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "git-picbed-"));
  temps.push(dir);
  return dir;
}

after(() => {
  for (const dir of temps) fs.rmSync(dir, { recursive: true, force: true });
});

function solidPng(width, height, salt = 0) {
  const buf = Buffer.alloc(32);
  Buffer.from("89504e470d0a1a0a", "hex").copy(buf, 0);
  buf.write("IHDR", 12);
  buf.writeUInt32BE(width, 16);
  buf.writeUInt32BE(height, 20);
  buf[31] = salt;
  return buf;
}

async function initRepo(dir) {
  fs.mkdirSync(dir, { recursive: true });
  await git(dir, ["init", "-b", "master"]);
  await git(dir, ["config", "user.email", "picbed@example.com"]);
  await git(dir, ["config", "user.name", "picbed"]);
  await git(dir, ["config", "core.autocrlf", "false"]);
  await git(dir, ["config", "commit.gpgsign", "false"]);
}

describe("image info", () => {
  it("reads png and jpeg size and hashes the same bytes once", () => {
    const png = solidPng(8, 4, 1);
    assert.deepEqual(imageSize(png), { width: 8, height: 4 });
    const jpeg = Buffer.from([
      0xff, 0xd8, 0xff, 0xc0, 0x00, 0x0b, 0x08, 0x00, 0x10, 0x00, 0x20, 0x01, 0x11, 0x00, 0x00, 0x00,
    ]);
    assert.deepEqual(imageSize(jpeg), { width: 32, height: 16 });
    const left = inspectBuffer(png);
    const right = inspectBuffer(Buffer.from(png));
    assert.equal(left.sha256, right.sha256);
    assert.equal(left.size, png.length);
  });
});

describe("host", () => {
  it("keeps one provider and hides credentials", () => {
    const gitee = resolveHost("gitee", "https://user:secret@gitee.com/demo/pics.git", "", "");
    assert.equal(gitee.owner, "demo");
    assert.equal(gitee.repo, "pics");
    assert.equal(gitee.remoteUrl.includes("secret"), false);
    assert.equal(
      rawUrl({ provider: "gitee", owner: "demo", repo: "pics", branch: "master", filePath: "imgs/a.png" }),
      "https://raw.giteeusercontent.com/demo/pics/raw/master/imgs/a.png"
    );
    const github = resolveHost("github", "git@github.com:octo/pics.git", "", "");
    assert.equal(github.provider, "github");
    assert.equal(
      rawUrl({ provider: "github", owner: "octo", repo: "pics", branch: "main", filePath: "imgs/a.png" }),
      "https://raw.githubusercontent.com/octo/pics/main/imgs/a.png"
    );
    assert.throws(() => resolveHost("github", "https://gitee.com/demo/pics.git", "", ""), /Gitee/);
    assert.throws(() => resolveHost("gitee", "https://github.com/octo/pics.git", "", ""), /GitHub/);
    assert.throws(() => resolveHost("gitee", "https://gitlab.com/demo/pics.git", "", ""), /Gitee 或 GitHub/);
  });
});

describe("plan", () => {
  it("uploads one copy and deletes images that are gone", () => {
    const locals = [
      { sha256: "aaa", name: "a.png", ext: ".png", size: 10, width: 8, height: 4, tooBig: false },
      { sha256: "aaa", name: "a-copy.png", ext: ".png", size: 10, width: 8, height: 4, tooBig: false },
      { sha256: "bbb", name: "b.png", ext: ".png", size: 11, width: 3, height: 5, tooBig: false },
    ];
    const plan = buildPlan(locals, [], { imageDir: "imgs", preserveShas: new Set() });
    assert.equal(plan.uploads.length, 2);
    assert.equal(plan.uploads.find((item) => item.sha256 === "aaa").sources.length, 2);
    const again = buildPlan(
      locals.filter((item) => item.sha256 === "aaa"),
      plan.nextImages,
      { imageDir: "imgs", preserveShas: new Set() }
    );
    assert.equal(again.uploads.length, 0);
    assert.equal(again.reuses.length, 1);
    assert.equal(again.deletes.length, 1);
    assert.equal(again.deletes[0].sha256, "bbb");
  });

  it("keeps an oversized file that is already in the bed", () => {
    const images = [{ sha256: "abc", name: "big.png", path: "imgs/ab/abc.png", size: 1, width: 1, height: 1, ext: ".png" }];
    const kept = buildPlan([{ tooBig: true, name: "big.png", size: 99 }], images, {
      imageDir: "imgs",
      preserveShas: new Set(["abc"]),
    });
    assert.equal(kept.deletes.length, 0);
    assert.equal(kept.nextImages.length, 1);
    const dropped = buildPlan([{ tooBig: true, name: "big.png", size: 99 }], images, {
      imageDir: "imgs",
      preserveShas: new Set(),
    });
    assert.equal(dropped.deletes.length, 1);
  });
});

describe("links", () => {
  function decide(map, { missing = new Set(), bedPrefix = "", known = new Set() } = {}) {
    return createDecider({
      fileDir: "D:/docs",
      bedPrefix,
      knownUrls: known,
      clearMissing: true,
      classifyFile(abs) {
        const key = abs.replace(/\\/g, "/").toLowerCase();
        if (map.has(key)) return { url: map.get(key) };
        if (missing.has(key)) return { missing: true };
        return null;
      },
    });
  }

  it("rewrites local images, keeps fences, and clears missing files", () => {
    const url = "https://gitee.com/demo/pics/raw/master/imgs/a.png";
    const map = new Map([["d:/pics/a.png", url]]);
    const missing = new Set(["d:/pics/gone.png"]);
    const content = [
      "see ![a](../pics/a.png) please",
      "![gone](../pics/gone.png)",
      "```",
      "![a](../pics/a.png)",
      "```",
      "`![a](../pics/a.png)`",
      '<img alt="a" src="../pics/a.png">',
      "![web](https://example.com/a.png)",
    ].join("\n");
    const next = rewriteContent(content, decide(map, { missing }));
    assert.match(next, new RegExp(`see !\\[a\\]\\(${url}\\) please`));
    assert.equal(next.includes("gone.png"), false);
    assert.match(next, /```\n!\[a\]\(\.\.\/pics\/a\.png\)\n```/);
    assert.match(next, /`!\[a\]\(\.\.\/pics\/a\.png\)`/);
    assert.match(next, new RegExp(`src="${url}"`));
    assert.match(next, /https:\/\/example\.com\/a\.png/);
    const again = rewriteContent(next, decide(map, { missing }));
    assert.equal(again, next);
  });

  it("clears a bed url that is no longer in the index", () => {
    const prefix = "https://gitee.com/demo/pics/raw/master/";
    const next = rewriteContent("![old](https://gitee.com/demo/pics/raw/master/imgs/old.png)\n", decide(new Map(), { bedPrefix: prefix }));
    assert.equal(next.includes("old.png"), false);
  });

  it("rewrites a gitee.com raw link to the direct image host", () => {
    const nextUrl = "https://raw.giteeusercontent.com/demo/pics/raw/master/imgs/a.png";
    const decide = createDecider({
      fileDir: "D:/docs",
      bedPrefixes: ["https://raw.giteeusercontent.com/demo/pics/raw/master/", "https://gitee.com/demo/pics/raw/master/"],
      knownUrls: new Set([nextUrl]),
      urlByPath: new Map([["imgs/a.png", nextUrl]]),
      clearMissing: true,
      classifyFile() {
        return null;
      },
    });
    const next = rewriteContent("![a](https://gitee.com/demo/pics/raw/master/imgs/a.png)\n", decide);
    assert.equal(next, `![a](${nextUrl})\n`);
  });
});

describe("store", () => {
  it("keeps folder ids and schedule gate", () => {
    const app = path.join(tmp(), "app");
    const store = createStore(app);
    const saved = store.saveConfig({
      provider: "gitee",
      repoPath: app,
      branch: "master",
      watchText: path.join(app, "pics"),
      docText: "",
      schedule: { enabled: true, intervalMinutes: 15 },
      push: { branch: "", directory: "", message: "docs: 更新图床链接" },
    });
    const again = store.loadConfig();
    assert.equal(again.watchFolders[0].id, saved.watchFolders[0].id);
    assert.equal(scheduleDue({ nextRunAt: 10 }, again, 10), true);
    assert.equal(scheduleDue({ nextRunAt: 10 }, { schedule: { enabled: false } }, 20), false);
    assert.throws(() => store.saveConfig({ ...saved, branch: "../main" }), /分支名不合法/);
  });
});

describe("git sync", () => {
  it("dedups, deletes missing images, rewrites links, and does not touch the other host", async () => {
    const root = tmp();
    const app = path.join(root, "app");
    const repo = path.join(root, "repo");
    const pics = path.join(root, "pics");
    const docs = path.join(root, "docs");
    fs.mkdirSync(pics, { recursive: true });
    fs.mkdirSync(docs, { recursive: true });
    fs.writeFileSync(path.join(pics, "a.png"), solidPng(8, 4, 1));
    fs.writeFileSync(path.join(pics, "a-copy.png"), solidPng(8, 4, 1));
    fs.writeFileSync(path.join(pics, "b.png"), solidPng(3, 5, 2));
    fs.writeFileSync(path.join(docs, "note.md"), "![a](../pics/a.png)\n![b](../pics/b.png)\n");
    await initRepo(repo);
    await git(repo, ["remote", "add", "origin", "https://gitee.com/demo/pics.git"]);
    const service = createService(app);
    const base = {
      provider: "gitee",
      repoPath: repo,
      remote: "origin",
      branch: "master",
      imageDir: "imgs",
      watchFolders: [{ id: "pics", path: pics }],
      docFolders: [{ id: "docs", path: docs }],
      clearMissingLinks: true,
      schedule: { enabled: false, intervalMinutes: 30 },
      push: { branch: "", directory: "", message: "docs: 更新图床链接" },
    };
    service.store.saveConfig({ ...base, provider: "github" });
    await assert.rejects(() => service.sync({ log() {} }), /Gitee/);
    assert.equal(fs.existsSync(path.join(repo, "imgs")), false);

    service.store.saveConfig(base);
    const preview = await service.sync({ dryRun: true, log() {} });
    assert.equal(preview.upload, 2);
    assert.equal(fs.existsSync(path.join(repo, "imgs", "picbed.json")), false);
    const synced = await service.sync({ log() {} });
    assert.equal(synced.upload, 2);
    assert.equal(synced.committed, true);
    const manifest = JSON.parse(fs.readFileSync(path.join(repo, "imgs", "picbed.json"), "utf8"));
    assert.equal(manifest.images.length, 2);
    const listed = (await git(repo, ["ls-files"])).stdout.trim().split(/\r?\n/);
    assert.equal(listed.length, 3);

    await service.updateLinks({ log() {} });
    const linked = fs.readFileSync(path.join(docs, "note.md"), "utf8");
    assert.equal(linked.split("https://raw.giteeusercontent.com/demo/pics/raw/master/imgs/").length - 1, 2);

    fs.rmSync(path.join(pics, "b.png"));
    const removed = await service.sync({ log() {} });
    assert.equal(removed.remove, 1);
    await service.updateLinks({ log() {} });
    const cleared = fs.readFileSync(path.join(docs, "note.md"), "utf8");
    assert.equal(cleared.split("![").length - 1, 1);
    const left = JSON.parse(fs.readFileSync(path.join(repo, "imgs", "picbed.json"), "utf8"));
    assert.equal(left.images.length, 1);
    assert.equal(left.images[0].name === "b.png", false);
  });

  it("pushes updated docs into the chosen directory and branch", async () => {
    const root = tmp();
    const repo = path.join(root, "repo");
    const bare = path.join(root, "bare.git");
    const note = path.join(root, "note.md");
    await initRepo(repo);
    await git(root, ["init", "--bare", "-b", "master", bare]);
    await git(repo, ["remote", "add", "origin", bare]);
    await git(repo, ["commit", "--allow-empty", "-m", "init"]);
    await git(repo, ["push", "-u", "origin", "master"]);
    fs.writeFileSync(path.join(repo, "dirty.txt"), "x");
    await assert.rejects(
      () =>
        publishFiles({
          repoPath: repo,
          branch: "docs",
          directory: "site",
          remote: "origin",
          files: [{ abs: note, rel: "note.md" }],
          message: "docs: 更新图床链接",
          doPush: false,
        }),
      /未提交改动/
    );
    assert.equal((await git(repo, ["branch", "--show-current"])).stdout.trim(), "master");
    fs.rmSync(path.join(repo, "dirty.txt"));
    fs.writeFileSync(note, "hello image\n");
    const published = await publishFiles({
      repoPath: repo,
      branch: "docs",
      directory: "site",
      remote: "origin",
      files: [{ abs: note, rel: "note.md" }],
      message: "docs: 更新图床链接",
      doPush: true,
    });
    assert.equal(published.committed, true);
    assert.equal(published.pushed, true);
    const check = path.join(root, "check");
    await git(root, ["clone", bare, check]);
    await git(check, ["checkout", "docs"]);
    assert.equal(fs.readFileSync(path.join(check, "site", "note.md"), "utf8").replace(/\r\n/g, "\n"), "hello image\n");
  });

  it("keeps a local image link until that file is on the remote", async () => {
    const root = tmp();
    const app = path.join(root, "app");
    const repo = path.join(root, "repo");
    const bare = path.join(root, "bare.git");
    const pics = path.join(root, "pics");
    const note = path.join(repo, "note.md");
    await initRepo(repo);
    await git(root, ["init", "--bare", "-b", "master", bare]);
    const barePosix = bare.replace(/\\/g, "/");
    await git(repo, ["remote", "add", "origin", "https://gitee.com/demo/pics.git"]);
    await git(repo, ["config", `url.${barePosix}/.insteadOf`, "https://gitee.com/demo/pics.git"]);
    await git(repo, ["commit", "--allow-empty", "-m", "init"]);
    await git(repo, ["push", "-u", "origin", "master"]);
    fs.mkdirSync(pics, { recursive: true });
    fs.writeFileSync(path.join(pics, "a.png"), solidPng(8, 4, 3));
    fs.writeFileSync(note, "![a](../pics/a.png)\n");
    const service = createService(app);
    service.store.saveConfig({
      provider: "gitee",
      repoPath: repo,
      remote: "origin",
      branch: "master",
      imageDir: "imgs",
      watchFolders: [{ id: "pics", path: pics }],
      docFolders: [{ id: "repo", path: repo }],
      clearMissingLinks: true,
      schedule: { enabled: false, intervalMinutes: 30 },
      push: { branch: "", directory: "", message: "docs: 更新图床链接" },
    });
    await service.sync({ log() {} });
    await service.updateLinks({ log() {} });
    assert.equal(fs.readFileSync(note, "utf8").includes("../pics/a.png"), true);
    await service.push(() => {});
    assert.equal(fs.readFileSync(note, "utf8").includes("https://raw.giteeusercontent.com/"), true);
  });

  it("publishes a new image and rewrites the note", async () => {
    const root = tmp();
    const app = path.join(root, "app");
    const repo = path.join(root, "repo");
    const bare = path.join(root, "bare.git");
    const pics = path.join(root, "pics");
    const note = path.join(repo, "note.md");
    await initRepo(repo);
    await git(root, ["init", "--bare", "-b", "master", bare]);
    const barePosix = bare.replace(/\\/g, "/");
    await git(repo, ["remote", "add", "origin", "https://gitee.com/demo/pics.git"]);
    await git(repo, ["config", `url.${barePosix}/.insteadOf`, "https://gitee.com/demo/pics.git"]);
    await git(repo, ["commit", "--allow-empty", "-m", "init"]);
    await git(repo, ["push", "-u", "origin", "master"]);
    fs.mkdirSync(pics, { recursive: true });
    fs.writeFileSync(path.join(pics, "a.png"), solidPng(6, 4, 4));
    fs.writeFileSync(note, "![a](../pics/a.png)\n");
    const service = createService(app);
    service.store.saveConfig({
      provider: "gitee",
      repoPath: repo,
      remote: "origin",
      branch: "master",
      imageDir: "imgs",
      watchFolders: [{ id: "pics", path: pics }],
      docFolders: [{ id: "repo", path: repo }],
      clearMissingLinks: true,
      schedule: { enabled: true, intervalMinutes: 30 },
      push: { branch: "", directory: "", message: "docs: 更新图床链接" },
    });
    await service.publish({ log() {} });
    assert.equal(fs.readFileSync(note, "utf8").includes("https://raw.giteeusercontent.com/"), true);
  });

  it("puts images on another branch without checking out the notes", async () => {
    const root = tmp();
    const app = path.join(root, "app");
    const repo = path.join(root, "repo");
    const bare = path.join(root, "bare.git");
    const pics = path.join(root, "pics");
    const note = path.join(repo, "note.md");
    await initRepo(repo);
    await git(root, ["init", "--bare", "-b", "master", bare]);
    const barePosix = bare.replace(/\\/g, "/");
    await git(repo, ["remote", "add", "origin", "https://gitee.com/demo/pics.git"]);
    await git(repo, ["config", `url.${barePosix}/.insteadOf`, "https://gitee.com/demo/pics.git"]);
    await git(repo, ["commit", "--allow-empty", "-m", "init"]);
    await git(repo, ["push", "-u", "origin", "master"]);
    fs.mkdirSync(pics, { recursive: true });
    fs.writeFileSync(path.join(pics, "a.png"), solidPng(6, 4, 5));
    fs.writeFileSync(path.join(repo, "draft.txt"), "keep me");
    fs.writeFileSync(note, "![a](../pics/a.png)\n");
    const service = createService(app);
    service.store.saveConfig({
      provider: "gitee",
      repoPath: repo,
      remote: "origin",
      branch: "pics",
      imageDir: "imgs",
      watchFolders: [{ id: "pics", path: pics }],
      docFolders: [{ id: "repo", path: repo }],
      clearMissingLinks: true,
      schedule: { enabled: false, intervalMinutes: 30 },
      push: { branch: "", directory: "", message: "docs: 更新图床链接" },
    });
    await service.publish({ log() {} });
    assert.equal((await git(repo, ["branch", "--show-current"])).stdout.trim(), "master");
    assert.equal(fs.existsSync(path.join(repo, "draft.txt")), true);
    const worktree = path.join(root, "repo-pics");
    assert.equal((await git(worktree, ["branch", "--show-current"])).stdout.trim(), "pics");
    assert.equal(fs.existsSync(path.join(worktree, "imgs", "picbed.json")), true);
    const linked = fs.readFileSync(note, "utf8");
    assert.equal(linked.includes("/raw/pics/"), true);
    fs.writeFileSync(note, linked.replace("/raw/pics/", "/raw/master/"));
    await service.updateLinks({ log() {} });
    assert.equal(fs.readFileSync(note, "utf8").includes("/raw/pics/"), true);
    assert.equal((await git(repo, ["branch", "--show-current"])).stdout.trim(), "master");
  });
});
