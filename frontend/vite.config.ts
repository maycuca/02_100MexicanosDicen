import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080'

// plan.md §1: los celulares solo hablan con :5173; Vite hace proxy al backend.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // expone el dev server en la LAN
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
      '/actuator': { target: BACKEND, changeOrigin: true },
      '/ws': { target: BACKEND, ws: true, changeOrigin: true },
    },
  },
})
