import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  // Relative base: the app works under any path prefix (e.g. /home-dashboard/ behind the central proxy)
  base: './',
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    hmr: {
      clientPort: 8080,
    },
  },
})
