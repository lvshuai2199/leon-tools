import test from "node:test"
import assert from "node:assert/strict"
import { fileStatusText, formatWhen, headline, mergeLane, mergeText, mergeTone } from "./text.js"

const now = Date.parse("2026-10-13T12:00:00Z")

test("相对时间", () => {
  assert.equal(formatWhen("", now), "")
  assert.equal(formatWhen("2026-10-13T11:59:30Z", now), "刚刚")
  assert.equal(formatWhen("2026-10-13T11:30:00Z", now), "30 分钟前")
  assert.equal(formatWhen("2026-10-13T09:00:00Z", now), "3 小时前")
  assert.equal(formatWhen("2026-10-11T12:00:00Z", now), "2 天前")
  const old = formatWhen("2020-01-02T00:00:00Z", now)
  assert.ok(old)
  assert.equal(old.includes("天前"), false)
})

test("提交标题只取第一行", () => {
  assert.equal(headline("fix deploy\n\nbody"), "fix deploy")
  assert.equal(headline("  "), "（没有说明）")
})

test("分支合并情况", () => {
  const base = { name: "main", merge: "default", ahead: 0, behind: 0 }
  assert.equal(mergeText(base, "main"), "默认分支")
  assert.equal(mergeText({ name: "same", merge: "identical", ahead: 0, behind: 0 }, "main"), "与默认分支一致")
  assert.equal(mergeText({ name: "old", merge: "merged", ahead: 0, behind: 4 }, "main"), "已合并，落后 4")
  assert.equal(mergeText({ name: "dev", merge: "ahead", ahead: 2, behind: 0 }, "main"), "未合并，超前 2")
  assert.equal(mergeText({ name: "feat", merge: "diverged", ahead: 3, behind: 1 }, "main"), "未合并，超前 3，落后 1")
  assert.equal(mergeText({ name: "wip", merge: "unmerged", ahead: 5, behind: 0 }, "main"), "未合并，还有 5 个提交")
  assert.equal(mergeText({ name: "x", merge: "", ahead: 0, behind: 0 }, "main"), "")
  assert.equal(mergeTone({ name: "old", merge: "merged" }, "main"), "ok")
  assert.equal(mergeTone({ name: "feat", merge: "diverged" }, "main"), "open")
})

test("分支小图：实心点相对空心点的位置", () => {
  const def = mergeLane({ name: "main", merge: "default", ahead: 0, behind: 0 }, "main")
  assert.equal(def.kind, "default")
  assert.equal(def.nodes.length, 1)
  assert.equal(def.nodes[0].role, "head")

  const merged = mergeLane({ name: "old", merge: "merged", ahead: 0, behind: 4 }, "main")
  const mergedHead = merged.nodes.find((n) => n.role === "head")
  const mergedBase = merged.nodes.find((n) => n.role === "base")
  assert.ok(mergedHead.x < mergedBase.x)
  assert.equal(merged.labels[0].text, "4")

  const ahead = mergeLane({ name: "dev", merge: "ahead", ahead: 2, behind: 0 }, "main")
  const aheadHead = ahead.nodes.find((n) => n.role === "head")
  const aheadBase = ahead.nodes.find((n) => n.role === "base")
  assert.ok(aheadHead.x > aheadBase.x)
  assert.equal(aheadHead.y, aheadBase.y)

  const fork = mergeLane({ name: "feat", merge: "diverged", ahead: 3, behind: 1 }, "main")
  const forkHead = fork.nodes.find((n) => n.role === "head")
  const forkBase = fork.nodes.find((n) => n.role === "base")
  assert.ok(forkHead.y < forkBase.y)
  assert.equal(fork.lines.length, 2)
  assert.deepEqual(fork.labels.map((l) => l.text), ["1", "3"])

  const unknown = mergeLane({ name: "wip", merge: "unmerged", ahead: 5, behind: 0 }, "main")
  assert.equal(unknown.lines[0].dash, true)
  assert.equal(mergeLane({ name: "x", merge: "", ahead: 0, behind: 0 }, "main"), null)

  const tipX = (lane) => (lane.nodes.find((n) => n.role === "base") || lane.nodes[0]).x
  assert.equal(tipX(def), tipX(merged))
  assert.equal(tipX(def), tipX(ahead))
  assert.equal(tipX(def), tipX(fork))
})

test("文件状态", () => {
  assert.equal(fileStatusText("added"), "新增")
  assert.equal(fileStatusText("removed"), "删除")
  assert.equal(fileStatusText("renamed"), "重命名")
  assert.equal(fileStatusText("modified"), "修改")
})
