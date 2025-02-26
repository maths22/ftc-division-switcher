import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 8888,
    proxy: {
      "/api": {
        target: 'http://localhost:8887',
        ws: true,
      },
      "/oauth": {
        target: 'http://localhost:8887',
      }
    }
  },
  build: {
    outDir: '../../../build/resources/main/serve',
    emptyOutDir: true
  }
})
