<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { login, register } from './api';
import { clearAuth, isLoggedIn, setAuth, store } from './store';

const router = useRouter();

/* 登录/注册弹窗 */
const loginVisible = ref(false);
const registerVisible = ref(false);
const loginName = ref('');
const loginPwd = ref('');
const regForm = ref({ username: '', password: '', phone: '', email: '' });

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

/** 新会话：通知 ChatView 重置并跳转 */
function newSession(): void {
  store.newSessionReq += 1;
  void router.push('/chat');
}

const menuItems = [
  { index: '/chat', title: '🤖 AI 导购' },
  { index: '/', title: '🏠 商品首页' },
  { index: '/cart', title: '🛒 购物车' },
  { index: '/orders', title: '📦 我的订单' },
  { index: '/address', title: '📍 收货地址' },
  { index: '/video', title: '🎬 视频播放' },
  { index: '/profile', title: '👤 个人中心' },
];
</script>

<template>
  <div class="app-shell">
    <!-- 顶栏 -->
    <header class="app-header">
      <div class="app-title">
        <span class="logo">🛍</span>
        <div>
          <h1>数码商城 · AI 导购演示</h1>
          <p>Spring AI · DeepSeek · Skill / Memory / Planner / Tools 四模块 · 流式输出</p>
        </div>
      </div>

      <div class="header-right">
        <template v-if="!isLoggedIn">
          <el-button type="primary" @click="loginVisible = true">登录</el-button>
          <el-button @click="registerVisible = true">注册</el-button>
        </template>
        <template v-else>
          <el-tag type="success" effect="dark">{{ store.username }}</el-tag>
          <el-button size="default" @click="newSession">新会话</el-button>
          <el-button size="default" @click="doLogout">退出</el-button>
        </template>
      </div>
    </header>

    <!-- 主体 -->
    <div class="app-main">
      <el-menu
        :default-active="$route.path"
        router
        class="side-menu"
        background-color="#ffffff"
        text-color="#606266"
        active-text-color="#409eff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>

      <el-main class="view-body">
        <router-view />
      </el-main>
    </div>
  </div>

  <!-- 登录弹窗 -->
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

  <!-- 注册弹窗 -->
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
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  gap: 16px;
}
.app-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.app-title .logo {
  font-size: 28px;
}
.app-title h1 {
  margin: 0;
  font-size: 18px;
}
.app-title p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #909399;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.side-menu {
  width: 180px;
  flex-shrink: 0;
  border-right: 1px solid #e4e7ed;
}
.view-body {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}
</style>
