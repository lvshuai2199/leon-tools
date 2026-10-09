import { Marked } from 'marked'
import { resolveNotePath } from './note-path.js'

const liveUrls = new Set<string>()

export function renderNoteMarkdown(markdown: string, docPath: string): string {
  const marked = new Marked({ gfm: true })
  marked.use({
    walkTokens(token: { type?: string; text?: string }) {
      if (token.type === 'html' || token.type === 'inlineHtml') token.text = ''
    },
    renderer: {
      image(token: { href?: string; text?: string }) {
        return imageHtml(docPath, token.href, token.text)
      },
      link(this: { parser: { parseInline: (tokens: unknown[]) => string } }, token: { href?: string; tokens?: unknown[] }) {
        const inner = this.parser.parseInline(token.tokens || [])
        return linkHtml(docPath, token.href, inner)
      },
    } as never,
  })
  return marked.parse(markdown || '', { async: false }) as string
}

function imageHtml(docPath: string, href: string | undefined, text: string | undefined) {
  const alt = escapeHtml(text || '')
  const rel = resolveNotePath(docPath, href || '')
  if (rel) return `<img data-note-path="${escapeHtml(rel)}" alt="${alt}">`
  if (href && /^https?:\/\//i.test(href)) return `<img src="${escapeHtml(href)}" alt="${alt}">`
  return ''
}

function linkHtml(docPath: string, href: string | undefined, inner: string) {
  const rel = resolveNotePath(docPath, href || '')
  if (rel && /\.(md|markdown)$/i.test(rel)) return `<a href="#" data-note-doc="${escapeHtml(rel)}">${inner}</a>`
  if (rel) return `<a href="#" data-note-file="${escapeHtml(rel)}">${inner}</a>`
  if (href && /^(https?:|mailto:)/i.test(href)) {
    return `<a href="${escapeHtml(href)}" target="_blank" rel="noopener noreferrer">${inner}</a>`
  }
  return inner
}

export function revokeNoteAssets() {
  liveUrls.forEach((url) => URL.revokeObjectURL(url))
  liveUrls.clear()
}

export async function hydrateNoteImages(root: ParentNode, fetchBlob: (path: string) => Promise<Blob>) {
  const imgs = [...root.querySelectorAll<HTMLImageElement>('img[data-note-path]')]
  await Promise.all(
    imgs.map(async (img) => {
      const path = img.getAttribute('data-note-path')
      if (!path || img.dataset.loaded === path) return
      try {
        const blob = await fetchBlob(path)
        const url = URL.createObjectURL(blob)
        liveUrls.add(url)
        img.src = url
        img.dataset.loaded = path
      } catch {
        img.alt = img.alt || '图片加载失败'
      }
    }),
  )
}

function escapeHtml(s: string) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] || c)
}
