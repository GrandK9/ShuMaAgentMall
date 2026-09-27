<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getMenuTree, login } from './api';
import { clearPermissions, loadPermissions, permissionsLoaded } from './permission';
import type { MenuItem } from './types';

const router = useRouter();
const loginVisible = ref(false);
const loginName = ref('');
const loginPwd = ref('');
const loading = ref(false);

/** 登录态。localStorage 不是响应式数据源，必须用 ref 承载才能驱动顶栏与菜单刷新 */
const token = ref(localStorage.getItem('admin_token') || '');
const username = ref(localStorage.getItem('admin_username') || '');
const isLoggedIn = computed(() => !!token.value);

async function doLogin(): Promise<void> {
  if (!loginName.value || !loginPwd.value) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  loading.value = true;
  try {
    const resp = await login(loginName.value, loginPwd.value);
    localStorage.setItem('admin_token', resp.token);
    localStorage.setItem('admin_username', resp.username);
    // 签名密钥与 token 同生命周期：改订单状态、退款等写接口需要它算 X-Sign
    localStorage.setItem('admin_sign_secret', resp.signSecret);
    token.value = resp.token;
    username.value = resp.username;
    loginName.value = '';
    loginPwd.value = '';
    loginVisible.value = false;
    ElMessage.success(`登录成功：${resp.username}`);
    await loadMenus();
    await loadPermissions();
  } catch (e) {
    ElMessage.error(`登录失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function doLogout(): void {
  localStorage.removeItem('admin_token');
  localStorage.removeItem('admin_username');
  localStorage.removeItem('admin_sign_secret');
  token.value = '';
  username.value = '';
  menus.value = [];
  clearPermissions();
  ElMessage.info('已退出登录');
  void router.push('/');
}

/** 权限点 code → 前端路由映射；权限表里没有对应页面的 code（如 user:view）在渲染时被跳过 */
const MENU_ROUTES: Record<string, { path: string; icon: string }> = {
  'dashboard:view': { path: '/', icon: '📊' },
  'product:view': { path: '/product', icon: '📦' },
  'category:view': { path: '/category', icon: '🗂️' },
  'brand:view': { path: '/brand', icon: '🏷️' },
  'order:view': { path: '/order', icon: '📑' },
  'payment:view': { path: '/payment', icon: '💳' },
  'role:view': { path: '/role', icon: '👥' },
  'permission:view': { path: '/permission', icon: '🔐' },
  'audit:view': { path: '/audit', icon: '📋' },
  'video:view': { path: '/video', icon: '🎬' },
  'comment:view': { path: '/comment', icon: '💬' },
  'search:view': { path: '/search-index', icon: '🔍' },
};

/** 后端菜单树（GET /permission/menus，已按当前登录用户的角色权限过滤） */
const menus = ref<MenuItem[]>([]);

/** 侧边栏项：由菜单树 + code → 路由映射派生，权限变更后刷新即可生效 */
const menuItems = computed(() =>
  menus.value.flatMap((menu) => {
    const route = MENU_ROUTES[menu.code];
    return route ? [{ index: route.path, title: `${route.icon} ${menu.name}` }] : [];
  }),
);

async function loadMenus(): Promise<void> {
  if (!isLoggedIn.value) {
    menus.value = [];
    return;
  }
  try {
    menus.value = await getMenuTree();
  } catch (e) {
    menus.value = [];
    ElMessage.error(`菜单加载失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void loadMenus();
  void loadPermissions();
});
</script>

<template>
  <div class="admin-shell">
    <!-- 顶栏 -->
    <header class="admin-header">
      <div class="brand">
        <span class="logo">🛠️</span>
        <h1>数码商城 · 管理后台</h1>
      </div>
      <div class="header-actions">
        <template v-if="!isLoggedIn">
          <el-button type="primary" @click="loginVisible = true">登录</el-button>
        </template>
        <template v-else>
          <el-tag type="success" effect="dark">{{ username }}</el-tag>
          <el-button size="default" @click="doLogout">退出</el-button>
        </template>
      </div>
    </header>

    <div class="admin-body">
      <!-- 左侧菜单 -->
      <el-menu
        :default-active="$route.path"
        router
        class="admin-menu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>

      <!-- 右侧内容：等权限就绪后再渲染页面，否则 v-permission 会因权限集为空而误删按钮 -->
      <main class="admin-main">
        <router-view v-if="permissionsLoaded" />
        <div v-else class="loading-hint">权限加载中…</div>
      </main>
    </div>
  </div>

  <!-- 登录弹窗 -->
  <el-dialog v-model="loginVisible" title="管理端登录" width="400px" :close-on-click-modal="false">
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
      <el-button type="primary" :loading="loading" @click="doLogin">登录</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.admin-shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}
.admin-header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand .logo {
  font-size: 24px;
}
.brand h1 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.admin-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.admin-menu {
  width: 200px;
  flex-shrink: 0;
  border-right: none;
}
.admin-main {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.loading-hint {
  padding: 32px;
  text-align: center;
  color: #909399;
  font-size: 14px;
}
</style>
