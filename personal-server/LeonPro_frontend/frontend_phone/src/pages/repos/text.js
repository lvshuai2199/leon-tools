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

/** 相对默认分支的合并情况。比不出来就返回空字符串。 */
export function mergeText(branch, defaultBranch) {
  if (!branch) return ""
  const kind = branch.name === defaultBranch ? "default" : branch.merge || ""
  const ahead = Number(branch.ahead) || 0
  const behind = Number(branch.behind) || 0
  if (kind === "default") return "默认分支"
  if (kind === "identical") return "与默认分支一致"
  if (kind === "merged") return behind > 0 ? `已合并，落后 ${behind}` : "已合并"
  if (kind === "ahead") return `未合并，超前 ${ahead}`
  if (kind === "diverged") return `未合并，超前 ${ahead}，落后 ${behind}`
  if (kind === "unmerged") return ahead > 0 ? `未合并，还有 ${ahead} 个提交` : "未合并"
  return ""
}

export function mergeTone(branch, defaultBranch) {
  const kind = branch && branch.name === defaultBranch ? "default" : branch?.merge || ""
  if (kind === "default") return "base"
  if (kind === "identical" || kind === "merged") return "ok"
  if (kind === "ahead" || kind === "diverged" || kind === "unmerged") return "open"
  return ""
}

/**
 * 分支相对默认分支的小图。
 * 空心点是默认分支顶端，实心点是这条分支。
 * 实心点在左：已合并，只是落后。在右：还有提交没进去。抬高：两边都有新提交。
 */
export function mergeLane(branch, defaultBranch) {
  if (!branch) return null
  const kind = branch.name === defaultBranch ? "default" : String(branch.merge || "")
  if (!kind) return null
  const ahead = Math.max(0, Number(branch.ahead) || 0)
  const behind = Math.max(0, Number(branch.behind) || 0)
  const y = 22
  const tip = 58
  const left = 14
  const right = 108
  if (kind === "default" || kind === "identical") {
    return {
      kind,
      lines: [{ x1: 12, y1: y, x2: tip, y2: y, dash: false }],
      nodes: [{ role: "head", x: tip, y }],
      labels: [],
    }
  }
  if (kind === "merged") {
    return {
      kind,
      lines: [{ x1: left, y1: y, x2: tip, y2: y, dash: false }],
      nodes: [
        { role: "head", x: left, y },
        { role: "base", x: tip, y },
      ],
      labels: behind > 0 ? [{ text: countLabel(behind), x: (left + tip) / 2, y: 15 }] : [],
    }
  }
  if (kind === "ahead") {
    return {
      kind,
      lines: [{ x1: tip, y1: y, x2: right, y2: y, dash: false }],
      nodes: [
        { role: "base", x: tip, y },
        { role: "head", x: right, y },
      ],
      labels: ahead > 0 ? [{ text: countLabel(ahead), x: (tip + right) / 2, y: 15 }] : [],
    }
  }
  if (kind === "diverged") {
    return {
      kind,
      lines: [
        { x1: left, y1: y, x2: tip, y2: y, dash: false },
        { x1: tip, y1: y, x2: right, y2: 10, dash: false },
      ],
      nodes: [
        { role: "base", x: tip, y },
        { role: "head", x: right, y: 10 },
      ],
      labels: [
        ...(behind > 0 ? [{ text: countLabel(behind), x: (left + tip) / 2, y: 15 }] : []),
        ...(ahead > 0 ? [{ text: countLabel(ahead), x: 82, y: 12 }] : []),
      ],
    }
  }
  if (kind === "unmerged") {
    return {
      kind,
      lines: [{ x1: tip, y1: y, x2: right, y2: y, dash: true }],
      nodes: [
        { role: "base", x: tip, y },
        { role: "head", x: right, y },
      ],
      labels: ahead > 0 ? [{ text: countLabel(ahead), x: (tip + right) / 2, y: 15 }] : [],
    }
  }
  return null
}

function countLabel(n) {
  return n > 99 ? "99+" : String(n)
}

function absolute(t, now) {
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, "0")
  const md = `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return d.getFullYear() === new Date(now).getFullYear() ? md : `${d.getFullYear()}-${md}`
}
