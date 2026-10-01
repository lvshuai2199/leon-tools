#!/usr/bin/env node
// 跑 src 下所有 *.test.js（node 内置测试）。不依赖 shell 通配符，Windows / Linux 一样用。
import { readdirSync } from "node:fs";
import { join, relative } from "node:path";
import { spawnSync } from "node:child_process";

function walk(dir, out = []) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith(".test.js")) out.push(relative(process.cwd(), p));
  }
  return out;
}

const files = walk("src").sort();
if (!files.length) {
  console.error("没有找到测试文件");
  process.exit(1);
}
const r = spawnSync(process.execPath, ["--test", ...files], { stdio: "inherit" });
process.exit(r.status ?? 1);
