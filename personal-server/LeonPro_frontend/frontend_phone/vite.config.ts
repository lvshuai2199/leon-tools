import { defineConfig, loadEnv, type ProxyOptions } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { fileURLToPath, URL } from "node:url";

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  // 优先级：VITE_API_TARGET（.env.local 或环境变量）> 启动台传入的 VITE_APP_API_URL / UNI_API_URL > 本机 8089
  const target = env.VITE_API_TARGET || env.VITE_APP_API_URL || env.UNI_API_URL || "http://127.0.0.1:8089";
  const prefix = (env.VITE_API_PREFIX || "/dev-api").replace(/\/+$/, "");

  // /dev-api/auth/me -> /auth/me，/dev-api/uploads/x.jpg -> /uploads/x.jpg
  const proxy: Record<string, ProxyOptions> = {
    [prefix]: {
      target,
      changeOrigin: true,
      rewrite: (p) => p.replace(new RegExp(`^${escapeRe(prefix)}`), ""),
    },
  };

  return {
    // 部署在站点根路径 /，路由用 history 模式（nginx 需把未知路径回落到 index.html）
    base: "/",
    plugins: [
      vue(),
      // Element Plus 按需引入：模板里用到的 <el-xxx> 自动 import 组件和样式
      Components({
        dirs: [],
        dts: "src/types/components.d.ts",
        resolvers: [ElementPlusResolver({ importStyle: "css" })],
      }),
    ],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
        "@shared": fileURLToPath(new URL("../shared", import.meta.url)),
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          // 共用主题：每个 scss（含 .vue 里的 <style lang="scss">）都能直接用 lp.$primary、@include lp.mobile 等
          // theme.scss 本身不输出 CSS，注入多次不会重复
          additionalData: '@use "@shared/theme" as lp;\n',
        },
      },
    },
    server: {
      host: "0.0.0.0",
      port: 5173,
      strictPort: true,
      proxy,
      fs: { allow: [".."] },
    },
    preview: { port: 4173, proxy },
    build: {
      outDir: "dist",
      sourcemap: false,
      chunkSizeWarningLimit: 1200,
    },
  };
});
