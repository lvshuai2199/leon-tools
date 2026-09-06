import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

export default defineConfig({
	plugins: [uni()],
	server: {
		proxy: {
			"/prod-api": {
				target: "http://127.0.0.1:8089",
				changeOrigin: true,
				rewrite: (path) => path.replace(/^\/prod-api/, ""),
			},
		},
	},
});
