import assert from "node:assert/strict";
import test from "node:test";
import { buildTree, resolveNotePath } from "./note-path.js";

test("相对链接停在仓库里面", () => {
  assert.equal(resolveNotePath("notes/a.md", "./b.md"), "notes/b.md");
  assert.equal(resolveNotePath("notes/a.md", "../img/a.png"), "img/a.png");
  assert.equal(resolveNotePath("a.md", "../secret.md"), null);
  assert.equal(resolveNotePath("a.md", "https://example.com/a.png"), null);
  assert.equal(resolveNotePath("a.md", "#标题"), null);
  assert.equal(resolveNotePath("a.md", "dir/.git/config"), null);
});

test("目录树按文件夹在前、文件在后", () => {
  const tree = buildTree(
    [
      { path: "随手记/a.md", title: "甲" },
      { path: "README.md", title: "说明" },
      { path: "notes/design/方案.md", title: "方案" },
    ],
    "",
  );
  assert.ok(tree.findIndex((n) => n.type === "file") > tree.findIndex((n) => n.type === "dir"));
  assert.equal(tree.find((n) => n.name === "notes").children[0].children[0].title, "方案");
  assert.equal(tree.find((n) => n.name === "README.md").title, "说明");
});

test("关键字同时匹配路径和标题", () => {
  const tree = buildTree(
    [
      { path: "notes/a.md", title: "方案" },
      { path: "随手记/b.md", title: "日记" },
    ],
    "方案",
  );
  assert.equal(tree.length, 1);
  assert.equal(tree[0].name, "notes");
});
