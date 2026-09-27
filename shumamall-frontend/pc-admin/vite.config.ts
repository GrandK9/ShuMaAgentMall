import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 管理端 Vite dev proxy：按前缀转发到各后端微服务
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api/v1/auth': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/admin/product': { target: 'http://127.0.0.1:8083', changeOrigin: true },
      '/api/v1/admin/category': { target: 'http://127.0.0.1:8083', changeOrigin: true },
      '/api/v1/admin/brand': { target: 'http://127.0.0.1:8083', changeOrigin: true },
      '/api/v1/categories': { target: 'http://127.0.0.1:8083', changeOrigin: true },
      '/api/v1/admin/orders': { target: 'http://127.0.0.1:8084', changeOrigin: true },
      '/api/v1/admin/payment': { target: 'http://127.0.0.1:8085', changeOrigin: true },
      '/api/v1/admin/permission': { target: 'http://127.0.0.1:8086', changeOrigin: true },
      '/api/v1/admin/role': { target: 'http://127.0.0.1:8086', changeOrigin: true },
      '/api/v1/admin/audit': { target: 'http://127.0.0.1:8086', changeOrigin: true },
      // 权限服务自述接口：/permission/menus（菜单树）、/permission/my-permissions（权限编码集）
      '/permission': { target: 'http://127.0.0.1:8086', changeOrigin: true },
      '/api/v1/admin/dashboard': { target: 'http://127.0.0.1:8090', changeOrigin: true },
      '/api/v1/admin/upload': { target: 'http://127.0.0.1:8090', changeOrigin: true },
      // comment 服务（8089）：管理端评论分页/隐藏/恢复
      '/api/v1/admin/comment': { target: 'http://127.0.0.1:8089', changeOrigin: true },
      // search 服务（8087）：索引状态查询与全量重建（/api/v1/search/index/**）
      '/api/v1/search': { target: 'http://127.0.0.1:8087', changeOrigin: true },
      // video 服务（8092）：管理端视频列表/删除/重转码 + 分片上传与播放签名 URL
      '/api/v1/admin/video': { target: 'http://127.0.0.1:8092', changeOrigin: true },
      '/api/v1/video': { target: 'http://127.0.0.1:8092', changeOrigin: true },
    },
  },
});
