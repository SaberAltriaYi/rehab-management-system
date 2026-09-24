import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.REHAB_DEV_PROXY_TARGET
  return {
    plugins: [uni()],
    // Keep the vulnerable build-time dev server local-only by default.
    server: {
      host: '127.0.0.1',
      proxy: proxyTarget ? {
        '/admin-api': { target: proxyTarget, changeOrigin: true, secure: true },
        '/app-api': { target: proxyTarget, changeOrigin: true, secure: true },
      } : undefined,
    },
  }
})
