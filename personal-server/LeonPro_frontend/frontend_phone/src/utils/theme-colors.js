/**
 * 画布（发货图）和脚本生成的 DOM 用的主题颜色常量。
 * 画布画出来的是图片，不能用 CSS 变量，所以把 shared/theme.scss 的浅色值抄在这里；
 * theme-colors.test.js 会读 shared/theme.scss 核对，主题改了测试会失败提醒同步。
 */
export const THEME = Object.freeze({
  primary: "#2563eb",
  /** 主色 light-9（Element Plus 混色规则：90% 白） */
  primaryLight9: "#e9effd",
  danger: "#dc2626",
  success: "#15803d",
  successBg: "#dcfce7",
  warning: "#b45309",
  warningBg: "#fef3c7",
  crabText: "#c2410c",
  textPrimary: "#1f2329",
  textRegular: "#4b5563",
  textSecondary: "#6b7280",
  textPlaceholder: "#a8abb2",
  border: "#dcdfe6",
  borderLighter: "#ebeef5",
  bgPage: "#f5f7fa",
  bg: "#ffffff",
  white: "#ffffff",
});

/** theme.scss 变量名 → THEME 键（测试用） */
export const SCSS_KEYS = Object.freeze({
  primary: "primary",
  danger: "danger",
  success: "success",
  "success-bg": "successBg",
  warning: "warning",
  "warning-bg": "warningBg",
  "crab-text": "crabText",
  "text-primary": "textPrimary",
  "text-regular": "textRegular",
  "text-secondary": "textSecondary",
  "text-placeholder": "textPlaceholder",
  border: "border",
  "border-lighter": "borderLighter",
  "bg-page": "bgPage",
  bg: "bg",
});

/** Element Plus 的 light-N 混色：base 与白色按 N*10% 混合 */
export function mixWhite(hex, weight) {
  const n = parseInt(hex.slice(1), 16);
  const ch = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((c) => Math.round(255 * weight + c * (1 - weight)));
  return `#${ch.map((c) => c.toString(16).padStart(2, "0")).join("")}`;
}
