/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 是否使用本地 Mock 数据 */
  readonly VITE_USE_MOCK?: string
  /** 接口 & 相对资源前缀：开发 /dev-api，生产 /prod-api */
  readonly VITE_API_PREFIX?: string
  /** dev/preview 代理目标（仅 vite.config.ts 使用） */
  readonly VITE_API_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, any>
  export default component
}
