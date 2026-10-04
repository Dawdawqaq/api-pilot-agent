import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// 可通过启动环境变量切换 IDEA 或打包服务地址，不在源码写入凭据。
const backendUrl = process.env.DOCHELPER_BACKEND_URL || 'http://127.0.0.1:18081'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true
      },
      '/actuator': {
        target: backendUrl,
        changeOrigin: true
      }
    }
  }
})
