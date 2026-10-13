"use strict";

function sections(patch) {
  const lines = String(patch || "").replace(/\r\n/g, "\n").split("\n");
  const out = [];
  let current = null;
  for (const line of lines) {
    if (line.startsWith("diff --git ")) {
      if (current) out.push(`${current.join("\n")}\n`);
      current = [];
    }
    if (current) current.push(line);
  }
  if (current) out.push(`${current.join("\n")}\n`);
  return out;
}

function gitPaths(rest) {
  const out = [];
  let i = 0;
  while (i < rest.length) {
    const ch = rest[i];
    if (ch === '"') {
      const end = rest.indexOf('"', i + 1);
      if (end < 0) break;
      out.push(rest.slice(i + 1, end));
      i = end + 1;
    } else if (ch === " ") {
      i += 1;
    } else {
      let end = rest.indexOf(" ", i);
      if (end < 0) end = rest.length;
      out.push(rest.slice(i, end));
      i = end;
    }
  }
  return out;
}

function cleanPath(raw) {
  let value = String(raw || "").trim();
  if (value.startsWith("a/") || value.startsWith("b/")) value = value.slice(2);
  return value.replace(/\\/g, "/");
}

function pathOf(section) {
  let plus = "";
  let minus = "";
  let git = "";
  for (const line of section.split("\n")) {
    if (line.startsWith("diff --git ")) git = line;
    else if (line.startsWith("+++ ")) plus = line.slice(4).trim();
    else if (line.startsWith("--- ")) minus = line.slice(4).trim();
    else if (line.startsWith("@@")) break;
  }
  if (plus && plus !== "/dev/null") return cleanPath(plus);
  if (minus && minus !== "/dev/null") return cleanPath(minus);
  const paths = gitPaths(git.slice("diff --git ".length).trim());
  if (paths.length >= 2) return cleanPath(paths[1]);
  return paths.length ? cleanPath(paths[0]) : "";
}

function norm(lines) {
  return lines.map((line) => line.replace(/\s+/g, " ").trim());
}

function sameList(a, b) {
  return a.length === b.length && a.every((item, i) => item === b[i]);
}

function hunkHint(additions, deletions, whitespace) {
  if (whitespace) return "这一段只改了空白";
  if (additions > 0 && deletions === 0) return `新增 ${additions} 行`;
  if (deletions > 0 && additions === 0) return `删除 ${deletions} 行`;
  return `修改，新增 ${additions} 行，删除 ${deletions} 行`;
}

function parseHunk(index, block) {
  const lines = block.split("\n");
  const header = lines[0] || "";
  const body = [];
  const added = [];
  const removed = [];
  for (const line of lines.slice(1)) {
    if (!line) continue;
    if (line.startsWith("\\")) body.push({ kind: "meta", text: line });
    else if (line.startsWith("+")) {
      const text = line.slice(1);
      added.push(text);
      body.push({ kind: "add", text });
    } else if (line.startsWith("-")) {
      const text = line.slice(1);
      removed.push(text);
      body.push({ kind: "del", text });
    } else if (line.startsWith(" ")) body.push({ kind: "ctx", text: line.slice(1) });
    else body.push({ kind: "ctx", text: line });
  }
  const whitespace = (added.length > 0 || removed.length > 0) && sameList(norm(added), norm(removed));
  return {
    index,
    header,
    hint: hunkHint(added.length, removed.length, whitespace),
    additions: added.length,
    deletions: removed.length,
    whitespaceOnly: whitespace,
    lines: body,
  };
}

function hunkBlocks(section) {
  const blocks = [];
  let current = null;
  for (const line of section.split("\n")) {
    if (line.startsWith("@@")) {
      if (current) blocks.push(current.join("\n"));
      current = [];
    }
    if (current && line) current.push(line);
  }
  if (current) blocks.push(current.join("\n"));
  return blocks;
}

function parseSection(section) {
  const filePath = pathOf(section);
  const binary = section.includes("\nGIT binary patch") || section.includes("\nBinary files ") || section.startsWith("GIT binary patch") || section.startsWith("Binary files ");
  let status = "M";
  if (section.includes("\nnew file mode ") || section.startsWith("new file mode ")) status = "A";
  else if (section.includes("\ndeleted file mode ") || section.startsWith("deleted file mode ")) status = "D";
  else if (section.includes("\nrename from ") || section.startsWith("rename from ")) status = "R";
  const hunks = binary ? [] : hunkBlocks(section).map((block, index) => parseHunk(index, block));
  const additions = hunks.reduce((sum, hunk) => sum + hunk.additions, 0);
  const deletions = hunks.reduce((sum, hunk) => sum + hunk.deletions, 0);
  const hints = [];
  if (binary) hints.push("二进制文件，只能整文件拣选");
  else if (status === "A") hints.push(hunks.length > 1 ? "新文件，只拣选一部分时可能对不上" : "新文件");
  else if (status === "D") hints.push("删除文件");
  else if (status === "R") hints.push("重命名");
  return { path: filePath, status, binary, additions, deletions, hunks, hints };
}

function parse(patch) {
  return sections(patch).map(parseSection);
}

function summarize(files, { parents = 0, dirty = false, onTarget = false, truncated = false } = {}) {
  const hints = [];
  let additions = 0;
  let deletions = 0;
  let binary = false;
  let whitespace = false;
  for (const file of files) {
    additions += file.additions;
    deletions += file.deletions;
    binary = binary || file.binary;
    whitespace = whitespace || file.hunks.some((hunk) => hunk.whitespaceOnly);
  }
  hints.push(files.length ? `${files.length} 个文件，新增 ${additions} 行，删除 ${deletions} 行` : "没有差异");
  if (parents > 1) hints.push("这是合并提交，对比的是第一父提交");
  if (binary) hints.push("含二进制文件，只能整文件拣选");
  if (whitespace) hints.push("有只改了空白的片段");
  if (dirty) hints.push("工作区还有未提交改动");
  if (onTarget) hints.push("目标分支已经包含这次提交");
  if (truncated) hints.push("差异过长，只显示前面一部分");
  return hints;
}

function select(patch, picks) {
  const wanted = new Map();
  for (const pick of picks || []) {
    if (!pick || !pick.file) continue;
    const file = String(pick.file).replace(/\\/g, "/");
    if (!wanted.has(file)) wanted.set(file, new Set());
    wanted.get(file).add(Number(pick.index));
  }
  let out = "";
  for (const section of sections(patch)) {
    const indexes = wanted.get(pathOf(section));
    if (!indexes || !indexes.size) continue;
    const whole = [...indexes].some((index) => index < 0) || !hunkBlocks(section).length;
    out += whole ? section : filterHunks(section, indexes);
  }
  return out;
}

function filterHunks(section, indexes) {
  const header = [];
  const hunks = [];
  let current = null;
  for (const line of section.split("\n")) {
    if (line.startsWith("@@")) {
      current = [line];
      hunks.push(current);
    } else if (!current) {
      if (line) header.push(line);
    } else if (line) current.push(line);
  }
  if (!hunks.some((_, index) => indexes.has(index))) return "";
  const lines = [...header];
  hunks.forEach((hunk, index) => {
    if (indexes.has(index)) lines.push(...hunk);
  });
  return `${lines.join("\n")}\n`;
}

function sideBySide(file) {
  if (!file || file.binary) return { binary: true, rows: [], leftNote: "", rightNote: "" };
  const rows = [];
  for (const hunk of file.hunks || []) {
    rows.push({ kind: "hunk", index: hunk.index, header: hunk.header, hint: hunk.hint });
    const lines = hunk.lines || [];
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      if (line.kind === "ctx") {
        rows.push({ kind: "ctx", left: line.text, right: line.text });
        i += 1;
      } else if (line.kind === "add") {
        rows.push({ kind: "add", left: null, right: line.text });
        i += 1;
      } else if (line.kind === "del") {
        const removed = [];
        const added = [];
        while (i < lines.length && lines[i].kind === "del") {
          removed.push(lines[i].text);
          i += 1;
        }
        while (i < lines.length && lines[i].kind === "add") {
          added.push(lines[i].text);
          i += 1;
        }
        const count = Math.max(removed.length, added.length);
        for (let k = 0; k < count; k++) {
          const left = k < removed.length ? removed[k] : null;
          const right = k < added.length ? added[k] : null;
          let kind = "replace";
          if (left == null) kind = "add";
          else if (right == null) kind = "del";
          rows.push({ kind, left, right });
        }
      } else i += 1;
    }
  }
  return {
    binary: false,
    rows,
    leftNote: file.status === "A" ? "HEAD 没有" : "",
    rightNote: file.status === "D" ? "工作区已删除" : "",
  };
}

function capFiles(files, truncated) {
  let budget = 800;
  const out = [];
  let cut = truncated;
  for (const file of files) {
    if (budget <= 0) {
      cut = true;
      break;
    }
    const hunks = [];
    for (const hunk of file.hunks) {
      let lines = hunk.lines;
      if (lines.length > budget) {
        lines = lines.slice(0, budget);
        cut = true;
        budget = 0;
      } else budget -= Math.max(lines.length, 1);
      hunks.push({ ...hunk, lines });
      if (budget <= 0) break;
    }
    out.push({ ...file, hunks });
  }
  return { files: out, truncated: cut };
}

module.exports = { parse, summarize, select, hunkHint, capFiles, sideBySide };
