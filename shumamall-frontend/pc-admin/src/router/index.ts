import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/product', name: 'product', component: () => import('../views/ProductManage.vue') },
  { path: '/category', name: 'category', component: () => import('../views/CategoryManage.vue') },
  { path: '/brand', name: 'brand', component: () => import('../views/BrandManage.vue') },
  { path: '/order', name: 'order', component: () => import('../views/OrderManage.vue') },
  { path: '/payment', name: 'payment', component: () => import('../views/PaymentManage.vue') },
  { path: '/role', name: 'role', component: () => import('../views/RoleManage.vue') },
  { path: '/permission', name: 'permission', component: () => import('../views/PermissionManage.vue') },
  { path: '/audit', name: 'audit', component: () => import('../views/AuditLogView.vue') },
  { path: '/video', name: 'video', component: () => import('../views/VideoManage.vue') },
  { path: '/comment', name: 'comment', component: () => import('../views/CommentManage.vue') },
  { path: '/search-index', name: 'searchIndex', component: () => import('../views/SearchIndexView.vue') },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
