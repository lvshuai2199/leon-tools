"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { branchTree, decorateRefs, parseTrack } = require("../lib/branches");

function checkouts(nodes, out = []) {
  for (const node of nodes || []) {
    if (node.checkout) out.push(node.checkout);
    if (node.children) checkouts(node.children, out);
  }
  return out;
}

function findName(nodes, name) {
  for (const node of nodes || []) {
    if (node.name === name) return node;
    const child = findName(node.children, name);
    if (child) return child;
  }
  return null;
}

test("当前分支在 HEAD，本地斜杠再分层", () => {
  const tree = branchTree([
    { name: "main", remote: false, current: true, ahead: 1, behind: 2 },
    { name: "feature/foo", remote: false, current: false },
    { name: "feature/bar", remote: false, current: false },
  ]);
  assert.deepEqual(
    tree.groups.map((group) => group.id),
    ["head", "local", "remote"]
  );
  assert.equal(tree.groups[0].label, "HEAD");
  assert.equal(tree.groups[0].nodes[0].checkout, "main");
  assert.equal(tree.groups[0].nodes[0].current, true);
  assert.equal(tree.groups[0].nodes[0].ahead, 1);
  const feature = findName(tree.groups[1].nodes, "feature");
  assert.equal(feature.type, "folder");
  const foo = findName(feature.children, "foo");
  assert.equal(foo.type, "leaf");
  assert.equal(foo.checkout, "feature/foo");
  assert.equal(foo.remote, false);
  assert.equal(foo.kind, "local");
  assert.equal(findName(feature.children, "bar").checkout, "feature/bar");
  assert.ok(checkouts(tree.groups[1].nodes).includes("main"));
});

test("远程按 remote 分组，并跳过 origin/HEAD", () => {
  const tree = branchTree(
    [
      { name: "main", remote: false, current: true },
      { name: "origin/main", remote: true },
      { name: "origin/feature/foo", remote: true },
      { name: "origin/HEAD", remote: true },
      { name: "upstream/topic", remote: true },
      { name: "upstream/HEAD", remote: true },
    ],
    { current: "main" }
  );
  const remote = tree.groups[2];
  assert.deepEqual(
    remote.nodes.map((node) => node.name),
    ["origin", "upstream"]
  );
  const names = checkouts(remote.nodes);
  assert.deepEqual(names.filter((name) => name.startsWith("origin/")).sort(), ["origin/feature/foo", "origin/main"]);
  assert.equal(findName(remote.nodes, "foo").checkout, "origin/feature/foo");
  assert.equal(findName(remote.nodes, "topic").checkout, "upstream/topic");
  assert.equal(names.includes("origin/HEAD"), false);
  assert.equal(names.includes("upstream/HEAD"), false);
  assert.equal(findName(remote.nodes, "topic").kind, "remote");
});

test("游离 HEAD 单独放在 HEAD 组", () => {
  const tree = branchTree([{ name: "main", remote: false, current: false }], { detached: true });
  assert.equal(tree.groups[0].nodes[0].detached, true);
  assert.equal(tree.groups[0].nodes[0].name, "游离 HEAD");
  assert.equal(tree.groups[0].nodes[0].checkout, "");
});

test("装饰用已知 remote 判断，斜杠本地分支不是远程", () => {
  const refs = decorateRefs("HEAD -> feature/foo, origin/feature/foo, tag: v1, upstream/HEAD", ["origin", "upstream"]);
  assert.deepEqual(refs, [
    { name: "feature/foo", kind: "local" },
    { name: "origin/feature/foo", kind: "remote" },
    { name: "v1", kind: "tag" },
  ]);
});

test("没有上游时 ahead/behind 留空", () => {
  assert.deepEqual(parseTrack("", "[ahead 1]"), { ahead: null, behind: null });
  assert.deepEqual(parseTrack("origin/main", "[ahead 2, behind 1]"), { ahead: 2, behind: 1 });
  assert.deepEqual(parseTrack("origin/main", ""), { ahead: 0, behind: 0 });
});
