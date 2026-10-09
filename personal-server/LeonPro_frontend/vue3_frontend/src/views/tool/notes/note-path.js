/** 仓库内相对路径。给 Markdown 链接和目录树共用。 */

export function resolveNotePath(docPath, href) {
  if (!href) return null
  const raw = String(href).trim()
  if (!raw || raw.startsWith('#') || /^(https?:|mailto:|data:|blob:|note-)/i.test(raw)) return null
  const noHash = raw.split('#')[0].split('?')[0]
  if (!noHash || noHash.startsWith('/')) return null
  const stack = String(docPath || '')
    .split('/')
    .slice(0, -1)
    .filter(Boolean)
  for (const part of noHash.split('/')) {
    if (!part || part === '.') continue
    if (part === '..') {
      if (!stack.length) return null
      stack.pop()
      continue
    }
    if (part === '.git') return null
    stack.push(part)
  }
  return stack.length ? stack.join('/') : null
}

export function buildTree(docs, keyword) {
  const kw = String(keyword || '').trim().toLowerCase()
  const filtered = !kw
    ? docs || []
    : (docs || []).filter((d) => {
        const path = String(d.path || '').toLowerCase()
        const title = String(d.title || '').toLowerCase()
        return path.includes(kw) || title.includes(kw)
      })
  const root = []
  for (const doc of filtered) {
    const parts = String(doc.path || '').split('/').filter(Boolean)
    let level = root
    for (let i = 0; i < parts.length; i++) {
      const name = parts[i]
      const isFile = i === parts.length - 1
      const path = parts.slice(0, i + 1).join('/')
      const type = isFile ? 'file' : 'dir'
      let node = level.find((n) => n.path === path && n.type === type)
      if (!node) {
        node = isFile
          ? { type, name, path, title: doc.title || name }
          : { type, name, path, children: [] }
        level.push(node)
      }
      if (!isFile) level = node.children
    }
  }
  sortLevel(root)
  return root
}

function sortLevel(nodes) {
  nodes.sort((a, b) => {
    if (a.type !== b.type) return a.type === 'dir' ? -1 : 1
    return a.name.localeCompare(b.name, 'zh')
  })
  for (const n of nodes) {
    if (n.children) sortLevel(n.children)
  }
}
