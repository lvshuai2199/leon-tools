import { defineConfig, loadEnv, type ProxyOptions } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_API_TARGET || 'http://localhost:8089'
  // dev: /dev-api；preview(production): /prod-api
  const prefix = (env.VITE_API_PREFIX || '/dev-api').replace(/\/+$/, '')

  const proxy: Record<string, ProxyOptions> = {
    [prefix]: {
      target,
      changeOrigin: true,
      // /dev-api/extern/wallpaper/groups -> /extern/wallpaper/groups
      // /dev-api/uploads/wallpaper/x.jpg -> /uploads/wallpaper/x.jpg
      rewrite: (p) => p.replace(new RegExp(`^${prefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`), ''),
    },
  }

  return {
    // 部署在站点的 /portal/ 子路径下
    base: '/portal/',
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: { port: 5174, host: true, proxy },
    // pnpm preview 使用 production 模式（/prod-api），同样代理到后端，模拟线上
    preview: { port: 4174, proxy },
    build: {
      outDir: 'dist',
      sourcemap: false,
    },
  }
})
