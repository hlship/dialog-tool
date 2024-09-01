import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  build: {
    outDir: resolve(__dirname, "dist", "public"),
    emptyOutDir: true
  },
  plugins: [svelte()],
})
