"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { parse, select, summarize } = require("../lib/diff");

const PATCH = `diff --git a/a.txt b/a.txt
index 111..222 100644
--- a/a.txt
+++ b/a.txt
@@ -1,3 +1,3 @@
 a
-b
+B
 c
@@ -10,3 +10,3 @@
 x
-y
+Y
 z
diff --git a/b.txt b/b.txt
index 333..444 100644
--- a/b.txt
+++ b/b.txt
@@ -1 +1 @@
-old
+new
`;

test("差异提示标出文件数、空白和未提交", () => {
  const files = parse(PATCH);
  assert.equal(files.length, 2);
  assert.equal(files[0].hunks[0].hint, "修改，新增 1 行，删除 1 行");
  const hints = summarize(files, { parents: 2, dirty: true, onTarget: true });
  assert.match(hints[0], /2 个文件/);
  assert.ok(hints.some((hint) => hint.includes("合并提交")));
  assert.ok(hints.some((hint) => hint.includes("未提交")));
  assert.ok(hints.some((hint) => hint.includes("已经包含")));
});

test("只改空白会单独提示", () => {
  const patch = `diff --git a/a.txt b/a.txt
--- a/a.txt
+++ b/a.txt
@@ -1 +1 @@
-hello world
+hello  world
`;
  const hunk = parse(patch)[0].hunks[0];
  assert.equal(hunk.whitespaceOnly, true);
  assert.equal(hunk.hint, "这一段只改了空白");
});

test("左右对比能区分上下文、纯增、纯删和同一处替换", () => {
  const { sideBySide } = require("../lib/diff");
  const patch = `diff --git a/a.txt b/a.txt
--- a/a.txt
+++ b/a.txt
@@ -1,7 +1,7 @@
 ctx
-old
+new
 keep
-gone
 tail
+extra
`;
  const rows = sideBySide(parse(patch)[0]).rows.filter((row) => row.kind !== "hunk");
  assert.ok(rows.some((row) => row.kind === "ctx" && row.left === "ctx" && row.right === "ctx"));
  assert.ok(rows.some((row) => row.kind === "replace" && row.left === "old" && row.right === "new"));
  assert.ok(rows.some((row) => row.kind === "del" && row.left === "gone" && row.right == null));
  assert.ok(rows.some((row) => row.kind === "add" && row.left == null && row.right === "extra"));
});

test("新文件、删除和二进制不编造行", () => {
  const { sideBySide } = require("../lib/diff");
  const added = parse(`diff --git a/n.txt b/n.txt
new file mode 100644
--- /dev/null
+++ b/n.txt
@@ -0,0 +1 @@
+hi
`)[0];
  const addSide = sideBySide(added);
  assert.equal(addSide.leftNote, "HEAD 没有");
  assert.equal(addSide.binary, false);
  const removed = parse(`diff --git a/n.txt b/n.txt
deleted file mode 100644
--- a/n.txt
+++ /dev/null
@@ -1 +0,0 @@
-hi
`)[0];
  assert.equal(sideBySide(removed).rightNote, "工作区已删除");
  const binary = parse(`diff --git a/a.bin b/a.bin
new file mode 100644
Binary files /dev/null and b/a.bin differ
`)[0];
  const binarySide = sideBySide(binary);
  assert.equal(binarySide.binary, true);
  assert.equal(binarySide.rows.length, 0);
});

test("拣选只保留勾选的片段", () => {
  const selected = select(PATCH, [{ file: "a.txt", index: 1 }]);
  assert.match(selected, /\+Y/);
  assert.doesNotMatch(selected, /\+B/);
  assert.doesNotMatch(selected, /b\.txt/);
});
