"use strict";

const fs = require("fs");
const path = require("path");
const { cloneRoot } = require("./paths");

const DEFAULTS = { cloneRoot: "", githubToken: "", giteeToken: "", port: 3792 };

function createStore(rootDir) {
  const file = path.join(rootDir, "config.json");

  function read() {
    if (!fs.existsSync(file)) return { ...DEFAULTS };
    try {
      const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
      return {
        cloneRoot: typeof parsed.cloneRoot === "string" ? parsed.cloneRoot : "",
        githubToken: typeof parsed.githubToken === "string" ? parsed.githubToken : "",
        giteeToken: typeof parsed.giteeToken === "string" ? parsed.giteeToken : "",
        port: Number(parsed.port) > 0 ? Number(parsed.port) : DEFAULTS.port,
      };
    } catch {
      return { ...DEFAULTS };
    }
  }

  function write(next) {
    const tmp = `${file}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(next, null, 2));
    fs.renameSync(tmp, file);
  }

  return {
    file,
    read,
    hint(token) {
      const text = String(token || "");
      return text.length >= 4 ? text.slice(-4) : "";
    },
    save(patch) {
      const current = read();
      const next = { ...current };
      if (Object.prototype.hasOwnProperty.call(patch, "cloneRoot")) {
        next.cloneRoot = patch.cloneRoot ? cloneRoot(patch.cloneRoot) : "";
      }
      if (patch.githubToken) next.githubToken = String(patch.githubToken).trim();
      if (patch.clearGithub) next.githubToken = "";
      if (patch.giteeToken) next.giteeToken = String(patch.giteeToken).trim();
      if (patch.clearGitee) next.giteeToken = "";
      for (const token of [next.githubToken, next.giteeToken]) {
        if (!token) continue;
        if (token.length < 8 || token.length > 400 || [...token].some((ch) => ch.charCodeAt(0) < 33 || ch.charCodeAt(0) > 126)) {
          throw new Error("令牌格式不对");
        }
      }
      write(next);
      return next;
    },
  };
}

module.exports = { createStore };
