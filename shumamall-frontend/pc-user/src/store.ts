import { computed, reactive } from 'vue';
import type { CartItem } from './types';

/**
 * 跨组件共享的轻量状态（不引入 pinia）：
 * - checkoutItems：结算商品（购物车勾选结算 / 商品页立即购买）
 * - token / username：登录态（App.vue 与 ChatView.vue 共享，避免各自初始化导致状态不同步）
 */
export const store = reactive({
  checkoutItems: [] as CartItem[],
  token: localStorage.getItem('agent_token') || '',
  username: localStorage.getItem('agent_username') || '',
  /**
   * 请求签名密钥（登录时由 auth 服务下发，与 token 同生命周期）。
   * 存 localStorage 只是为了刷新页面不丢；调用写接口时由 api.ts 读取并算出 X-Sign。
   */
  signSecret: localStorage.getItem('agent_sign_secret') || '',
  /** 新会话请求计数：App.vue「新会话」按钮自增，ChatView 监听后重置会话 */
  newSessionReq: 0,
});

/** 是否已登录 */
export const isLoggedIn = computed(() => store.token !== '');

/** 登录成功：写入内存态 + localStorage（api.ts 请求时从 localStorage 读 token 与签名密钥） */
export function setAuth(token: string, username: string, signSecret: string): void {
  store.token = token;
  store.username = username;
  store.signSecret = signSecret;
  localStorage.setItem('agent_token', token);
  localStorage.setItem('agent_username', username);
  localStorage.setItem('agent_sign_secret', signSecret);
}

/** 退出登录：清空内存态 + localStorage */
export function clearAuth(): void {
  store.token = '';
  store.username = '';
  store.signSecret = '';
  localStorage.removeItem('agent_token');
  localStorage.removeItem('agent_username');
  localStorage.removeItem('agent_sign_secret');
}
