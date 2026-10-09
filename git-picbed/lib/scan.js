"use strict";

const fs = require("fs");
const path = require("path");
const { EXTS, MAX_BYTES, inspectFile } = require("./image-info");
const { toPosix } = require("./paths");

function walk(dir, visit) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (err) {
    throw new Error(`无法读取文件夹 ${dir}：${err.message}`);
  }
  for (const entry of entries) {
    if (entry.name === ".git" || entry.name === "node_modules") continue;
    const abs = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(abs, visit);
    else if (entry.isFile()) visit(abs);
  }
}

function scanFolders(folders, maxBytes = MAX_BYTES) {
  const files = [];
  for (const folder of folders) {
    if (!folder.path || !fs.existsSync(folder.path) || !fs.statSync(folder.path).isDirectory()) {
      throw new Error(`监听文件夹不存在：${folder.path || ""}`);
    }
    walk(folder.path, (abs) => {
      const ext = path.extname(abs).toLowerCase();
      if (!EXTS.has(ext)) return;
      const stat = fs.statSync(abs);
      const item = {
        rootId: folder.id,
        rel: toPosix(path.relative(folder.path, abs)),
        abs,
        name: path.basename(abs),
        ext,
        size: stat.size,
      };
      if (stat.size > maxBytes) {
        files.push({ ...item, tooBig: true });
        return;
      }
      files.push({ ...item, tooBig: false, ...inspectFile(abs) });
    });
  }
  return files;
}

module.exports = { walk, scanFolders };
