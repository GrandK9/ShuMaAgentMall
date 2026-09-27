<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createPermission, deletePermission, getPermissionList, updatePermission } from '../api';
import type { Permission } from '../types';

const loading = ref(false);
const permissions = ref<Permission[]>([]);

const formVisible = ref(false);
const formTitle = ref('新增权限点');
const saving = ref(false);
const form = reactive<Partial<Permission>>({
  id: undefined,
  code: '',
  name: '',
  type: 0,
  pathPrefix: '',
  parentId: 0,
  sortOrder: 0,
  status: 1,
});

const typeOptions = [
  { label: 'MENU', value: 0 },
  { label: 'BUTTON', value: 1 },
  { label: 'API', value: 2 },
];

async function load(): Promise<void> {
  loading.value = true;
  try {
    permissions.value = await getPermissionList();
  } catch (e) {
    ElMessage.error(`权限加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  formTitle.value = '新增权限点';
  Object.assign(form, { id: undefined, code: '', name: '', type: 0, pathPrefix: '', parentId: 0, sortOrder: 0, status: 1 });
  formVisible.value = true;
}

function openEdit(p: Permission): void {
  formTitle.value = '编辑权限点';
  Object.assign(form, {
    id: p.id,
    code: p.code,
    name: p.name,
    type: p.type,
    pathPrefix: p.pathPrefix,
    parentId: p.parentId ?? 0,
    sortOrder: p.sortOrder,
    status: p.status,
  });
  formVisible.value = true;
}

async function save(): Promise<void> {
  if (!form.code || !form.name || form.type === undefined) {
    ElMessage.warning('请填写权限编码、名称和类型');
    return;
  }
  saving.value = true;
  try {
    const dto: Partial<Permission> = {
      code: form.code,
      name: form.name,
      type: form.type,
      pathPrefix: form.pathPrefix,
      parentId: form.parentId ?? 0,
      sortOrder: form.sortOrder,
      status: form.status,
    };
    if (form.id) {
      await updatePermission(form.id, dto);
      ElMessage.success('权限点已更新');
    } else {
      await createPermission(dto);
      ElMessage.success('权限点创建成功');
    }
    formVisible.value = false;
    void load();
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function onDelete(p: Permission): Promise<void> {
  await ElMessageBox.confirm(`确定删除权限点「${p.name}」吗？`, '提示', { type: 'warning' });
  try {
    await deletePermission(p.id);
    ElMessage.success('已删除');
    void load();
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>权限点管理</h2>
      <el-button v-permission="'permission:create'" type="primary" @click="openCreate">新增权限点</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && permissions.length === 0" description="暂无权限点" />
      <el-table v-else :data="permissions" row-key="id">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="权限编码" min-width="180" />
        <el-table-column prop="name" label="权限名称" min-width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.type === 0 ? 'MENU' : row.type === 1 ? 'BUTTON' : 'API' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pathPrefix" label="路由前缀" min-width="140" />
        <el-table-column prop="parentId" label="父 ID" width="80" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'permission:edit'" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'permission:delete'" size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="formTitle" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="权限编码" required>
          <el-input v-model="form.code" placeholder="如 product:edit" />
        </el-form-item>
        <el-form-item label="权限名称" required>
          <el-input v-model="form.name" placeholder="如 商品编辑" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width: 100%;">
            <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由前缀">
          <el-input v-model="form.pathPrefix" placeholder="可选" />
        </el-form-item>
        <el-form-item label="父权限 ID">
          <el-input-number v-model="form.parentId" :min="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.manage-page {
  padding-bottom: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
</style>
