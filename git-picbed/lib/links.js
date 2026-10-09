"use strict";

const path = require("path");

function splitFences(content) {
  const parts = [];
  const regex = /(```[\s\S]*?```|~~~[\s\S]*?~~~)/g;
  let last = 0;
  for (const match of content.matchAll(regex)) {
    if (match.index > last) parts.push({ code: false, text: content.slice(last, match.index) });
    parts.push({ code: true, text: match[0] });
    last = match.index + match[0].length;
  }
  if (last < content.length || !parts.length) parts.push({ code: false, text: content.slice(last) });
  return parts;
}

function replaceMatches(text, regex, replacer) {
  regex.lastIndex = 0;
  let out = "";
  let cursor = 0;
  for (const match of text.matchAll(regex)) {
    const at = match.index;
    const insert = replacer(match);
    if (insert == null) continue;
    let from = at;
    let to = at + match[0].length;
    let next = insert;
    if (insert === "") {
      const start = text.lastIndexOf("\n", at - 1) + 1;
      let end = text.indexOf("\n", at);
      if (end < 0) end = text.length;
      const before = text.slice(start, at);
      const after = text.slice(to, end);
      if (!before.trim() && !after.trim()) {
        from = start;
        to = end < text.length ? end + 1 : end;
        next = "";
      }
    }
    if (from < cursor) continue;
    out += text.slice(cursor, from) + next;
    cursor = to;
  }
  return out + text.slice(cursor);
}

const MD_IMG = /!\[([^\]]*)\]\(\s*(<[^>\n]+>|[^)\n]+)\)/g;
const HTML_IMG = /<img\b[^>]*?\bsrc\s*=\s*(["'])([\s\S]*?)\1[^>]*>/gi;

function rawUrlOf(token) {
  let value = token.trim();
  if (value.startsWith("<") && value.endsWith(">")) value = value.slice(1, -1).trim();
  const titled = value.match(/^(.*?)\s+(?:"[^"]*"|'[^']*')$/);
  if (titled) value = titled[1].trim();
  return value;
}

function replaceHtmlSrc(full, src, quote, newUrl) {
  const token = quote + src + quote;
  const pos = full.toLowerCase().indexOf("src");
  const rel = full.indexOf(token, Math.max(0, pos));
  if (rel < 0) return full;
  return full.slice(0, rel) + quote + newUrl + quote + full.slice(rel + token.length);
}

function rewriteSegment(text, decide) {
  const markdown = replaceMatches(text, MD_IMG, (match) => {
    const decision = decide(rawUrlOf(match[2]));
    if (!decision || decision.type === "keep") return null;
    if (decision.type === "clear") return "";
    return `![${match[1]}](${decision.url})`;
  });
  return replaceMatches(markdown, HTML_IMG, (match) => {
    const decision = decide(match[2]);
    if (!decision || decision.type === "keep") return null;
    if (decision.type === "clear") return "";
    return replaceHtmlSrc(match[0], match[2], match[1], decision.url);
  });
}

function rewriteContent(content, decide) {
  return splitFences(content)
    .map((part) => {
      if (part.code) return part.text;
      const bits = part.text.split(/(`[^`\n]*`)/g);
      return bits.map((bit, index) => (index % 2 ? bit : rewriteSegment(bit, decide))).join("");
    })
    .join("");
}

function localAbs(fileDir, url) {
  const clean = String(url || "").split("#")[0].split("?")[0];
  let decoded = clean;
  try {
    decoded = decodeURIComponent(clean);
  } catch {
    decoded = clean;
  }
  return path.resolve(fileDir, decoded);
}

function bedFilePath(url, prefixes) {
  for (const prefix of prefixes) {
    if (prefix && url.startsWith(prefix)) return url.slice(prefix.length);
  }
  return null;
}

function createDecider({ fileDir, bedPrefix, bedPrefixes, knownUrls, urlByPath, clearMissing, classifyFile }) {
  const prefixes = bedPrefixes || (bedPrefix ? [bedPrefix] : []);
  return (url) => {
    if (!url || url.startsWith("data:")) return { type: "keep" };
    if (/^https?:\/\//i.test(url)) {
      const clean = url.split("#")[0].split("?")[0];
      if (knownUrls.has(clean)) return { type: "keep" };
      const rel = bedFilePath(clean, prefixes);
      if (rel != null) {
        let decoded = rel;
        try {
          decoded = decodeURIComponent(rel);
        } catch {
          decoded = rel;
        }
        const next = urlByPath && urlByPath.get(decoded);
        if (next && next !== clean) return { type: "replace", url: next };
        if (next) return { type: "keep" };
        return { type: "clear" };
      }
      return { type: "keep" };
    }
    const found = classifyFile(localAbs(fileDir, url));
    if (found && found.url) return { type: "replace", url: found.url };
    if (found && found.missing && clearMissing) return { type: "clear" };
    return { type: "keep" };
  };
}

module.exports = { rewriteContent, createDecider, localAbs };
