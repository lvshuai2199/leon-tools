/** 锁定 body 滚动（支持嵌套调用），并补偿滚动条宽度避免页面抖动 */
let lockCount = 0
let saved: { overflow: string; paddingRight: string } | null = null

export function lockBodyScroll() {
  if (lockCount++ > 0) return
  const body = document.body
  const scrollbar = window.innerWidth - document.documentElement.clientWidth
  saved = { overflow: body.style.overflow, paddingRight: body.style.paddingRight }
  body.style.overflow = 'hidden'
  if (scrollbar > 0) body.style.paddingRight = `${scrollbar}px`
}

export function unlockBodyScroll() {
  if (lockCount === 0 || --lockCount > 0) return
  if (saved) {
    document.body.style.overflow = saved.overflow
    document.body.style.paddingRight = saved.paddingRight
    saved = null
  }
}
