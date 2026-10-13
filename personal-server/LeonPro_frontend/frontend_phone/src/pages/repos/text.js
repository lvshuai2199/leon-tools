/** 仓库页上的时间、提交标题、文件状态。纯函数。 */

export function formatWhen(iso, now = Date.now()) {
  if (!iso) return ""
  const t = new Date(iso).getTime()
  if (!Number.isFinite(t)) return ""
  const diff = now - t
  if (diff < 60_000) return diff >= -60_000 ? "刚刚" : absolute(t, now)
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`
  return absolute(t, now)
}

export function headline(message) {
  const line = String(message || "")
    .split("\n")
    .map((s) => s.trim())
    .find(Boolean)
  return line || "（没有说明）"
}

export function fileStatusText(status) {
  const s = String(status || "").toLowerCase()
  if (s === "added" || s === "add") return "新增"
  if (s === "removed" || s === "deleted" || s === "delete") return "删除"
  if (s === "renamed" || s === "rename") return "重命名"
  if (s === "copied" || s === "copy") return "复制"
  return "修改"
}

export function providerText(provider) {
  return provider === "gitee" ? "Gitee" : "GitHub"
}

function absolute(t, now) {
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, "0")
  const md = `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return d.getFullYear() === new Date(now).getFullYear() ? md : `${d.getFullYear()}-${md}`
}
