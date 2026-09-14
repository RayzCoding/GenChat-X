import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import type { IncomingMessage, ServerResponse } from 'node:http'

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
        selfHandleResponse: true,
        configure(proxy) {
          proxy.on('error', (err, _req, res) => {
            const socket = res as ServerResponse | undefined
            if (socket && !socket.headersSent) {
              socket.writeHead(502)
              socket.end(String(err.message ?? 'Bad gateway'))
            }
          })
          proxy.on('proxyRes', (proxyRes: IncomingMessage, req, res: ServerResponse) => {
            const contentType = String(proxyRes.headers['content-type'] ?? '')
            const url = req.url ?? ''
            const isSse = contentType.includes('text/event-stream') || url.includes('/stream')
            const headers = { ...proxyRes.headers }
            if (isSse) {
              headers['cache-control'] = 'no-cache, no-transform'
              headers['x-accel-buffering'] = 'no'
              headers['connection'] = 'keep-alive'
              delete headers['content-length']
              delete headers['content-encoding']
            }

            res.writeHead(proxyRes.statusCode ?? 200, headers)
            if (isSse) {
              proxyRes.on('data', (chunk: Buffer) => {
                res.write(chunk)
                const flushable = res as ServerResponse & { flush?: () => void }
                flushable.flush?.()
              })
              proxyRes.on('end', () => res.end())
              proxyRes.on('error', () => res.end())
            } else {
              proxyRes.pipe(res)
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
