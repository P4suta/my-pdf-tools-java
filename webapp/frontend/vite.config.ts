import { svelte } from "@sveltejs/vite-plugin-svelte";
import { defineConfig } from "vite";

// Dev server proxies the API to the Spring Boot server on :8080, so the SPA and the API share an
// origin during development (no CORS). The production build (pnpm build) emits a static bundle to
// dist/, which can be served from the server's src/main/resources/static/.
export default defineConfig({
  plugins: [svelte()],
  server: {
    proxy: {
      "/api": "http://127.0.0.1:8080",
    },
  },
  build: {
    outDir: "dist",
  },
});
