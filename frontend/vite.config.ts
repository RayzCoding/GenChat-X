import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/agent': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        timeout: 0,
        proxyTimeout: 0,
        configure(proxy) {
          proxy.on('proxyRes', (proxyRes, req, res) => {
            const contentType = String(proxyRes.headers['content-type'] ?? '')
            const url = req.url ?? ''
            if (contentType.includes('text/event-stream') || url.includes('/stream')) {
              proxyRes.headers['cache-control'] = 'no-cache, no-transform'
              proxyRes.headers['x-accel-buffering'] = 'no'
              delete proxyRes.headers['content-length']
              delete proxyRes.headers['content-encoding']
              res.setHeader('Cache-Control', 'no-cache, no-transform')
              res.setHeader('X-Accel-Buffering', 'no')
              res.setHeader('Connection', 'keep-alive')
              if (typeof res.flushHeaders === 'function') {
                res.flushHeaders()
              }
            }
          })
        },
      },
      '/file': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        bypass(req) {
          // /file-qa is a frontend route and must not use the /file API proxy
          const url = req.url ?? ''
          if (url === '/file-qa' || url.startsWith('/file-qa?')) {
            return '/index.html'
          }
        },
      },
    },
  },
})
