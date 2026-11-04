<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getUserInfo, updateUserInfo } from '../api';
import type { UserInfo } from '../types';

const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const user = reactive<Partial<UserInfo>>({});
const form = reactive({ nickname: '', phone: '', email: '', gender: 0 });

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await getUserInfo();
    Object.assign(user, data);
    form.nickname = data.nickname ?? '';
    form.phone = data.phone ?? '';
    form.email = data.email ?? '';
    form.gender = data.gender ?? 0;
  } catch (e) {
    ElMessage.error(`加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function save(): Promise<void> {
  saving.value = true;
  try {
    const data = await updateUserInfo({
      nickname: form.nickname || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined,
      gender: form.gender,
    });
    Object.assign(user, data);
    ElMessage.success('保存成功');
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="profile-page">
    <h2 class="page-title">个人中心</h2>
    <div class="shortcut-row">
      <el-card shadow="never" class="shortcut" @click="router.push('/orders')">
        <span class="shortcut-label">订单</span>
        <span>我的订单</span>
      </el-card>
      <el-card shadow="never" class="shortcut" @click="router.push('/address')">
        <span class="shortcut-label">地址</span>
        <span>收货地址</span>
      </el-card>
      <el-card shadow="never" class="shortcut" @click="router.push('/cart')">
        <span class="shortcut-label">购物车</span>
        <span>查看购物车</span>
      </el-card>
    </div>
    <el-card v-loading="loading" shadow="never" class="profile-card">
      <el-form label-width="80px" style="max-width: 480px;">
        <el-form-item label="用户名">
          <el-input :model-value="user.username" disabled />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="0">保密</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 var(--space-5);
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--gray-900);
}
.shortcut-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-5);
  max-width: 720px;
}
.shortcut {
  cursor: pointer;
  text-align: center;
  padding: var(--space-4) var(--space-3);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--gray-700);
}
.shortcut-label {
  display: block;
  font-family: var(--font-display);
  font-size: var(--text-xs);
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--muted);
  margin-bottom: var(--space-2);
}
.profile-card {
  max-width: 560px;
}
</style>
