import vue from "@vitejs/plugin-vue";
import { type ConfigEnv, loadEnv, defineConfig } from "vite";

import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

import UnoCSS from "unocss/vite";
import { resolve } from "path";
import { copyFileSync, cpSync, existsSync, mkdirSync } from "fs";
import { name, version, engines, dependencies, devDependencies } from "./package.json" with { type: "json" };

const __APP_INFO__ = {
  pkg: { name, version, engines, dependencies, devDependencies },
  buildTimestamp: Date.now(),
};

const pathSrc = resolve(import.meta.dirname, "src");

export default defineConfig(({ mode }: ConfigEnv) => {
  const env = loadEnv(mode, process.cwd());
  return {
    resolve: {
      alias: {
        "@": pathSrc,
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          api: "modern-compiler",
          additionalData: `
            @use "@/styles/variables.scss" as *;
          `,
        },
      },
    },
    server: {
      host: "0.0.0.0",
      port: +env.VITE_APP_PORT,
      open: true,
      proxy: {
        [env.VITE_APP_BASE_API]: {
          changeOrigin: true,
          target: process.env.VITE_APP_API_URL || env.VITE_APP_API_URL,
          rewrite: (path) => path.replace(new RegExp("^" + env.VITE_APP_BASE_API), ""),
          configure: (proxy) => {
            proxy.on("proxyRes", (proxyRes, req) => {
              if (req.url?.includes("/public/mindmap/")) {
                proxyRes.headers["cache-control"] = "no-store, no-cache, max-age=0, must-revalidate";
                proxyRes.headers["pragma"] = "no-cache";
                proxyRes.headers["expires"] = "0";
              }
            });
          },
        },
      },
    },
    plugins: [
      vue(),
      UnoCSS(),
      {
        name: "copy-plotly",
        buildStart() {
          const src = resolve(import.meta.dirname, "node_modules/plotly.js-dist-min/plotly.min.js");
          const destDir = resolve(import.meta.dirname, "public/lib");
          mkdirSync(destDir, { recursive: true });
          copyFileSync(src, resolve(destDir, "plotly.min.js"));

          const eliteSrc = resolve(import.meta.dirname, "../elite-task");
          const eliteDest = resolve(import.meta.dirname, "public/elite-task");
          if (existsSync(eliteSrc)) {
            cpSync(eliteSrc, eliteDest, { recursive: true });
          }
        },
      },
      AutoImport({
        imports: ["vue", "@vueuse/core", "pinia", "vue-router", "vue-i18n"],
        resolvers: [ElementPlusResolver()],
        eslintrc: {
          enabled: false,
          filepath: "./.eslintrc-auto-import.json",
          globalsPropValue: true,
        },
        vueTemplate: true,
        dts: false,
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dirs: ["src/components", "src/**/components"],
        dts: false,
      }),
    ],
    optimizeDeps: {
      include: [
        "vue",
        "vue-router",
        "element-plus",
        "pinia",
        "axios",
        "@vueuse/core",
        "path-to-regexp",
        "vue-i18n",
        "nprogress",
        "qs",
        "path-browserify",
        "@element-plus/icons-vue",
        "element-plus/es/locale/lang/zh-cn",
        "element-plus/es/locale/lang/en",
      ],
    },
    esbuild: {
      drop: ["console", "debugger"],
      legalComments: "none",
    },
    build: {
      chunkSizeWarningLimit: 2000,
      minify: "esbuild",
      reportCompressedSize: false,
      sourcemap: false,
      rolldownOptions: {
        output: {
          entryFileNames: "js/[name].[hash].js",
          chunkFileNames: "js/[name].[hash].js",
          assetFileNames: (assetInfo: any) => {
            const info = assetInfo.name.split(".");
            let extType = info[info.length - 1];
            if (/\.(mp4|webm|ogg|mp3|wav|flac|aac)(\?.*)?$/i.test(assetInfo.name)) {
              extType = "media";
            } else if (/\.(png|jpe?g|gif|svg)(\?.*)?$/.test(assetInfo.name)) {
              extType = "img";
            } else if (/\.(woff2?|eot|ttf|otf)(\?.*)?$/i.test(assetInfo.name)) {
              extType = "fonts";
            }
            return `${extType}/[name].[hash].[ext]`;
          },
        },
      },
    },
    define: {
      __APP_INFO__: JSON.stringify(__APP_INFO__),
    },
  };
});
