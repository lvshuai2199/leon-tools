import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { THEME, SCSS_KEYS, mixWhite } from "./theme-colors.js";

const scss = readFileSync(fileURLToPath(new URL("../../../shared/theme.scss", import.meta.url)), "utf8");

test("画布颜色常量与 shared/theme.scss 一致", () => {
  for (const [name, key] of Object.entries(SCSS_KEYS)) {
    const m = scss.match(new RegExp(`^\\$${name}:\\s*(#[0-9a-fA-F]{6})`, "m"));
    assert.ok(m, `theme.scss 里找不到 $${name}`);
    assert.equal(THEME[key].toLowerCase(), m[1].toLowerCase(), `$${name}`);
  }
});

test("主色 light-9 按 Element Plus 规则混色", () => {
  assert.equal(mixWhite(THEME.primary, 0.9), THEME.primaryLight9);
});
