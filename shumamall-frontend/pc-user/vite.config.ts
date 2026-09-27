import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Agent 演示前端：通过 Vite dev proxy 转发到后端微服务，避免 CORS
// 注意：Vite proxy 按 key 前缀匹配，更具体的路径必须放在前面（如 /api/v1/user/product 先于 /api/v1/user）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // auth 服务（8081）
      '/api/v1/auth': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
      },
      // agent 服务（8088）
      '/api/v1/agent': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true,
      },
      // product 服务（8083）：用户端商品 + 管理端商品
      '/api/v1/user/product': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true,
      },
      '/api/v1/admin/product': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true,
      },
      '/api/v1/categories': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true,
      },
      // user 服务（8082）：地址（先于 /api/v1/user 匹配）
      '/api/v1/user/address': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
      },
      '/api/v1/user': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
      },
      // order 服务（8084）：购物车 + 订单
      '/api/v1/cart': {
        target: 'http://127.0.0.1:8084',
        changeOrigin: true,
      },
      '/api/v1/orders': {
        target: 'http://127.0.0.1:8084',
        changeOrigin: true,
      },
      // payment 服务（8085）
      '/api/v1/payment': {
        target: 'http://127.0.0.1:8085',
        changeOrigin: true,
      },
      // comment 服务（8089）：热门评论等
      '/api/v1/comment': {
        target: 'http://127.0.0.1:8089',
        changeOrigin: true,
      },
      // video 服务（8092）：播放签名 URL + 播放进度（断点续播）
      '/api/v1/video': {
        target: 'http://127.0.0.1:8092',
        changeOrigin: true,
      },
      // search 服务（8087）：ES 全文检索
      '/api/v1/search': {
        target: 'http://127.0.0.1:8087',
        changeOrigin: true,
      },
    },
  },
});
