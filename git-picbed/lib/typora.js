"use strict";

const { execFileSync } = require("child_process");

function isTyporaRunning() {
  if (process.platform !== "win32") return false;
  try {
    const out = execFileSync(
      "tasklist",
      ["/FI", "IMAGENAME eq Typora.exe", "/FO", "CSV", "/NH"],
      { encoding: "utf8", windowsHide: true, timeout: 5000 }
    );
    return /Typora\.exe/i.test(String(out || ""));
  } catch {
    return false;
  }
}

module.exports = { isTyporaRunning };
