/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口前缀：开发 /dev-api，线上 /prod-api */
  readonly VITE_API_PREFIX?: string
  /** 示例数据开关（只在开发模式生效）：true / false / 逗号分隔的接口前缀 */
  readonly VITE_USE_MOCK?: string
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

declare module '*.js'
