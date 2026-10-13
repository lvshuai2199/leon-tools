"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { layout } = require("../lib/graph");

test("直线提交留在同一列", () => {
  const rows = layout([
    { sha: "c3", parents: ["c2"] },
    { sha: "c2", parents: ["c1"] },
    { sha: "c1", parents: [] },
  ]);
  assert.deepEqual(
    rows.map((row) => row.lane),
    [0, 0, 0]
  );
  assert.ok(rows.every((row) => row.color === 0));
  assert.equal(rows[0].links.length, 1);
  assert.equal(rows[0].links[0].kind, "straight");
  assert.equal(rows[0].links[0].from, 0);
  assert.equal(rows[0].links[0].to, 0);
  assert.equal(rows[2].links.length, 0);
});

test("分叉后另一条线换列，再汇回父提交", () => {
  const rows = layout([
    { sha: "c3", parents: ["c1"] },
    { sha: "c2", parents: ["c1"] },
    { sha: "c1", parents: [] },
  ]);
  assert.equal(rows[0].lane, 0);
  assert.equal(rows[1].lane, 1);
  assert.notEqual(rows[0].lane, rows[1].lane);
  assert.ok(rows[1].links.some((link) => link.kind === "merge" && link.from === 1 && link.to === 0));
  assert.ok(rows[1].links.some((link) => link.kind === "straight" && link.from === 0 && link.to === 0));
  assert.equal(rows[2].lane, 0);
});

test("合并两个 parent 后再汇合", () => {
  const rows = layout([
    { sha: "m", parents: ["a", "b"] },
    { sha: "a", parents: ["c"] },
    { sha: "b", parents: ["c"] },
    { sha: "c", parents: [] },
  ]);
  assert.equal(rows[0].lane, 0);
  assert.ok(rows[0].links.some((link) => link.kind === "straight" && link.to === 0));
  assert.ok(rows[0].links.some((link) => link.kind === "fork" && link.from === 0 && link.to === 1));
  assert.equal(rows[1].lane, 0);
  assert.equal(rows[2].lane, 1);
  assert.ok(rows[2].links.some((link) => link.kind === "merge" && link.from === 1 && link.to === 0));
  assert.equal(rows[3].lane, 0);
  assert.equal(rows[3].links.length, 0);
});

test("两个不相连的起点各占一列", () => {
  const rows = layout([
    { sha: "a", parents: ["a1"] },
    { sha: "b", parents: ["b1"] },
    { sha: "a1", parents: [] },
    { sha: "b1", parents: [] },
  ]);
  assert.equal(rows[0].lane, 0);
  assert.equal(rows[1].lane, 1);
  const aLine = rows[0].links.find((link) => link.kind === "straight" && link.from === 0);
  const bLine = rows[1].links.find((link) => link.kind === "straight" && link.from === 1);
  assert.equal(aLine.to, 0);
  assert.equal(bLine.to, 1);
  assert.ok(rows[1].links.some((link) => link.from === 0 && link.to === 0));
});

test("父提交不在列表里时线停在当行底部", () => {
  const rows = layout([
    { sha: "a", parents: ["missing"] },
    { sha: "b", parents: [] },
  ]);
  assert.ok(rows[0].links.some((link) => link.kind === "bottom" && link.from === 0));
  assert.equal(
    rows[0].links.some((link) => link.kind === "straight"),
    false
  );
  assert.equal(rows[1].lane, 0);
  assert.equal(rows[1].links.length, 0);
});
