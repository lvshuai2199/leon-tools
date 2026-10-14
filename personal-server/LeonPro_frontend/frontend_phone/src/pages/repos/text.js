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

const LANE_COLORS = ["#0969da", "#1a7f37", "#9a6700", "#8250df", "#cf222e", "#0e7490", "#bf3989", "#6e7781"]

/** 还能合并进默认分支：超前、分叉，或只知道还有提交没进去。 */
export function canMerge(branch, defaultBranch) {
  if (!branch || !defaultBranch || branch.name === defaultBranch) return false
  const kind = branch.merge || ""
  return kind === "ahead" || kind === "diverged" || kind === "unmerged"
}

/**
 * 把带父提交的列表画成 Git 图。新的在上。
 * 同一列的父子是直线，分叉再汇合是曲线。
 */
export function layoutGraph(commits) {
  const list = (Array.isArray(commits) ? commits : []).filter((commit) => commit && commit.sha)
  list.sort((a, b) => {
    const ta = Date.parse(a.date || "") || 0
    const tb = Date.parse(b.date || "") || 0
    if (ta === tb) return 0
    return tb - ta
  })
  const columns = []
  const placed = []
  for (const commit of list) {
    const sha = String(commit.sha)
    const hits = []
    for (let i = 0; i < columns.length; i++) {
      if (columns[i] === sha) hits.push(i)
    }
    let col
    if (hits.length) col = hits[0]
    else {
      col = columns.findIndex((item) => item == null)
      if (col < 0) {
        col = columns.length
        columns.push(null)
      }
    }
    const parents = (commit.parents || []).map(String).filter(Boolean).slice(0, 2)
    const next = columns.slice()
    while (next.length <= col) next.push(null)
    for (const extra of hits.slice(1)) next[extra] = null
    if (!parents.length) next[col] = null
    else {
      next[col] = parents[0]
      for (let p = 1; p < parents.length; p++) {
        let parentCol = next.indexOf(parents[p])
        if (parentCol < 0) {
          parentCol = next.findIndex((item, idx) => item == null && idx !== col)
          if (parentCol < 0) next.push(parents[p])
          else next[parentCol] = parents[p]
        }
      }
    }
    for (let i = 0; i < next.length; i++) {
      if (i !== col && next[i] === sha) next[i] = null
    }
    placed.push({ commit, col, parents })
    columns.splice(0, columns.length, ...next)
  }

  const index = new Map(placed.map((row, i) => [String(row.commit.sha), i]))
  const rowHeight = 32
  const colWidth = 22
  const pad = 12
  const xOf = (col) => pad + col * colWidth
  const yOf = (i) => i * rowHeight + rowHeight / 2
  let maxCol = 0
  const lines = []
  const rows = placed.map((row, i) => {
    maxCol = Math.max(maxCol, row.col)
    const color = LANE_COLORS[row.col % LANE_COLORS.length]
    const x = xOf(row.col)
    const y = yOf(i)
    for (const parentSha of row.parents) {
      const parentIndex = index.get(parentSha)
      if (parentIndex == null || parentIndex <= i) {
        lines.push({ d: `M ${x} ${y} L ${x} ${y + 16}`, color })
        continue
      }
      const parent = placed[parentIndex]
      const x2 = xOf(parent.col)
      const y2 = yOf(parentIndex)
      if (parent.col === row.col) lines.push({ d: `M ${x} ${y} L ${x2} ${y2}`, color })
      else {
        const mid = Math.min(y + 18, (y + y2) / 2)
        lines.push({ d: `M ${x} ${y} C ${x} ${mid}, ${x2} ${y2 - 16}, ${x2} ${y2}`, color })
      }
    }
    return {
      sha: String(row.commit.sha),
      shortSha: row.commit.shortSha || String(row.commit.sha).slice(0, 7),
      message: row.commit.message || "",
      author: row.commit.author || "",
      date: row.commit.date || "",
      branches: Array.isArray(row.commit.branches) ? row.commit.branches : [],
      col: row.col,
      color,
      x,
      y,
    }
  })
  return {
    rowHeight,
    width: pad * 2 + Math.max(1, maxCol + 1) * colWidth,
    height: Math.max(rowHeight, rows.length * rowHeight),
    rows,
    lines,
  }
}
