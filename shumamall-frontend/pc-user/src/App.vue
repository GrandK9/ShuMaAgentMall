<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { login, register } from './api';
import { clearAuth, isLoggedIn, setAuth, store } from './store';

const router = useRouter();
const route = useRoute();

const loginVisible = ref(false);
const registerVisible = ref(false);
const loginName = ref('');
const loginPwd = ref('');
const regForm = ref({ username: '', password: '', phone: '', email: '' });

/** 顶栏主导航（浏览型入口，不含账号类页面） */
const navItems = [
  { path: '/', label: '首页' },
  { path: '/seckill', label: '限时秒杀' },
  { path: '/chat', label: 'AI 导购' },
  { path: '/video', label: '视频' },
];

function navActive(path: string): boolean {
  if (path === '/') return route.path === '/';
  return route.path === path || route.path.startsWith(`${path}/`);
}

async function doLogin(): Promise<void> {
  if (!loginName.value || !loginPwd.value) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  try {
    const resp = await login(loginName.value, loginPwd.value);
    setAuth(resp.token, resp.username, resp.signSecret);
    loginName.value = '';
    loginPwd.value = '';
    loginVisible.value = false;
    ElMessage.success(`登录成功：${resp.username}`);
  } catch (e) {
    ElMessage.error(`登录失败：${(e as Error).message}`);
  }
}

async function doRegister(): Promise<void> {
  if (!regForm.value.username || !regForm.value.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  try {
    const resp = await register(regForm.value);
    setAuth(resp.token, resp.username, resp.signSecret);
    regForm.value = { username: '', password: '', phone: '', email: '' };
    registerVisible.value = false;
    ElMessage.success('注册并登录成功');
  } catch (e) {
    ElMessage.error(`注册失败：${(e as Error).message}`);
  }
}

function doLogout(): void {
  clearAuth();
  ElMessage.info('已退出登录');
  void router.push('/');
}

function newSession(): void {
  store.newSessionReq += 1;
  void router.push('/chat');
}

function goProfile(): void {
  void router.push('/profile');
}

function goOrders(): void {
  void router.push('/orders');
}

function goAddress(): void {
  void router.push('/address');
}

const displayName = computed(() => store.username || '我的账户');

/** 沉浸式任务页：占满 header 以下区域，无内边距/页脚 */
const isImmersiveMain = computed(
  () => route.path === '/chat' || route.path.startsWith('/chat/')
);
/** 任务列表页：与画布同宽铺面，避免单卡片浮在 ground 上 */
const isFlushMain = computed(() => route.path === '/seckill');
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="header-inner">
        <router-link to="/" class="brand" aria-label="数码商城首页">
          <span class="brand-mark" aria-hidden="true">SM</span>
          <span class="brand-name">数码商城</span>
        </router-link>

        <nav class="main-nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-link"
            :class="{ active: navActive(item.path) }"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="header-actions">
          <router-link to="/cart" class="action-link cart-link" :class="{ active: navActive('/cart') }">
            <svg class="header-cart-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path
                fill="currentColor"
                d="M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zm10 0c-1.1 0-1.99.9-1.99 2S15.9 22 17 22s2-.9 2-2-.9-2-2-2zM7.16 14h9.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49a1 1 0 0 0-.87-1.48H5.21L4.27 2H1v2h2l3.6 7.59-1.35 2.44C4.52 15.37 5.48 17 7 17h12v-2H7.16l1.1-2z"
              />
            </svg>
            <span>购物车</span>
          </router-link>

          <template v-if="!isLoggedIn">
            <el-button link type="primary" @click="loginVisible = true">登录</el-button>
            <el-button type="primary" round @click="registerVisible = true">注册</el-button>
          </template>
          <template v-else>
            <el-dropdown trigger="click" popper-class="user-dropdown-popper">
              <button type="button" class="user-trigger" aria-haspopup="menu">
                <span class="user-avatar">{{ displayName.charAt(0).toUpperCase() }}</span>
                <span class="user-name">{{ displayName }}</span>
                <span class="user-caret" aria-hidden="true">▾</span>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goProfile">个人中心</el-dropdown-item>
                  <el-dropdown-item @click="goOrders">我的订单</el-dropdown-item>
                  <el-dropdown-item @click="goAddress">收货地址</el-dropdown-item>
                  <el-dropdown-item divided @click="newSession">AI 导购 · 新会话</el-dropdown-item>
                  <el-dropdown-item divided @click="doLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </div>
    </header>

    <main class="site-main">
      <div
        class="view-body-inner"
        :class="{
          'view-body-inner--immersive': isImmersiveMain,
          'view-body-inner--flush': isFlushMain,
        }"
      >
        <router-view />
      </div>
    </main>

    <footer v-if="!isImmersiveMain" class="site-footer">
      <span>数码商城 · Spring 微服务演示 · AI 导购</span>
    </footer>
  </div>

  <el-dialog v-model="loginVisible" title="登录" width="400px" :close-on-click-modal="false">
    <el-form label-width="70px">
      <el-form-item label="用户名">
        <el-input v-model="loginName" placeholder="用户名" @keyup.enter="doLogin" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="loginPwd" type="password" placeholder="密码" show-password @keyup.enter="doLogin" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="loginVisible = false">取消</el-button>
      <el-button type="primary" @click="doLogin">登录</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="registerVisible" title="注册" width="400px" :close-on-click-modal="false">
    <el-form label-width="70px">
      <el-form-item label="用户名">
        <el-input v-model="regForm.username" placeholder="3-50 个字符" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="regForm.password" type="password" placeholder="6-50 个字符" show-password />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="regForm.phone" placeholder="选填" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="regForm.email" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="registerVisible = false">取消</el-button>
      <el-button type="primary" @click="doRegister">注册</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--ground);
}

.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--surface);
  border-bottom: 1px solid var(--border-subtle);
}

.header-inner {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 var(--space-6);
  height: 64px;
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  text-decoration: none;
  flex-shrink: 0;
}
.brand-mark {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--accent-ink);
  background: var(--accent);
  border-radius: var(--radius-md);
}
.brand-name {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink);
}

.main-nav {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 1;
  min-width: 0;
}
.nav-link {
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--muted);
  text-decoration: none;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color var(--duration-fast) var(--ease-out), border-color var(--duration-fast) var(--ease-out);
}
.nav-link:hover {
  color: var(--ink);
}
.nav-link.active {
  color: var(--ink);
  border-bottom-color: var(--link);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}

.action-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--gray-700);
  text-decoration: none;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  transition: color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}

.header-cart-icon {
  flex-shrink: 0;
  opacity: 0.85;
}
.action-link:hover,
.action-link.active {
  color: var(--ink);
  background: var(--surface-hover);
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 10px 4px 4px;
  border: 1px solid var(--gray-200);
  border-radius: 999px;
  background: var(--surface-card);
  cursor: pointer;
  font: inherit;
  color: var(--gray-700);
  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
}
.user-trigger:hover {
  border-color: var(--muted);
}
.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  font-weight: 500;
}
.user-caret {
  font-size: 10px;
  color: var(--gray-500);
}

.site-main {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.site-footer {
  border-top: 1px solid var(--border-subtle);
  background: var(--surface);
  text-align: center;
  padding: var(--space-5) var(--space-4);
  font-size: var(--text-xs);
  line-height: 1.5;
  color: var(--muted);
  letter-spacing: 0.02em;
}

@media (max-width: 768px) {
  .header-inner {
    flex-wrap: wrap;
    height: auto;
    padding-top: var(--space-3);
    padding-bottom: var(--space-3);
  }
  .main-nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
    padding-bottom: 2px;
  }
  .user-name {
    display: none;
  }
}
</style>
