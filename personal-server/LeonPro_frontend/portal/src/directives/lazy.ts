/**
 * v-lazy="src"：图片进入视口（含预加载边距）后再设置 src。
 * 配合 <img loading="lazy">，不支持 IntersectionObserver 时直接加载。
 * 加载完成后给元素加上 is-loaded 类（用于淡入）。
 */
import type { Directive } from 'vue'

type LazyEl = HTMLImageElement & { __lazySrc?: string }

let observer: IntersectionObserver | null = null

function load(el: LazyEl) {
  const src = el.__lazySrc
  if (!src) return
  if (el.getAttribute('src') !== src) el.src = src
}

function getObserver(): IntersectionObserver | null {
  if (typeof IntersectionObserver === 'undefined') return null
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            load(e.target as LazyEl)
            observer!.unobserve(e.target)
          }
        }
      },
      { rootMargin: '300px 0px' },
    )
  }
  return observer
}

function onLoad(this: HTMLImageElement) {
  this.classList.add('is-loaded')
}

function setup(el: LazyEl, src: string | undefined) {
  el.__lazySrc = src
  el.classList.remove('is-loaded')
  const io = getObserver()
  if (io) {
    io.unobserve(el)
    io.observe(el)
  } else {
    load(el)
  }
}

export const vLazy: Directive<LazyEl, string | undefined> = {
  mounted(el, binding) {
    el.addEventListener('load', onLoad)
    el.addEventListener('error', onLoad)
    setup(el, binding.value)
  },
  updated(el, binding) {
    if (binding.value !== binding.oldValue) setup(el, binding.value)
  },
  beforeUnmount(el) {
    observer?.unobserve(el)
    el.removeEventListener('load', onLoad)
    el.removeEventListener('error', onLoad)
  },
}
