import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [vue()],
  base: "/h5/",
  resolve: {
    alias: {
      "@": root,
    },
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    strictPort: true,
    proxy: {
      "/prod-api": {
        target: process.env.UNI_API_URL || process.env.VITE_APP_API_URL || "http://127.0.0.1:8089",
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/prod-api/, ""),
      },
    },
  },
});
