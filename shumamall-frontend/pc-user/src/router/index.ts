import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/product/:id', name: 'product-detail', component: () => import('../views/ProductDetailView.vue') },
  { path: '/cart', name: 'cart', component: () => import('../views/CartView.vue') },
  { path: '/checkout', name: 'checkout', component: () => import('../views/CheckoutView.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/OrderListView.vue') },
  { path: '/address', name: 'address', component: () => import('../views/AddressView.vue') },
  { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
  { path: '/chat', name: 'chat', component: () => import('../views/ChatView.vue') },
  { path: '/video/:videoId?', name: 'video-play', component: () => import('../views/VideoPlayView.vue') },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
