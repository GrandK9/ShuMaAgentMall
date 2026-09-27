<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { ElTree } from 'element-plus';
import { createRole, deleteRole, getPermissionList, getRolePage, getRolePermissions, updateRole } from '../api';
import type { Permission, Role } from '../types';

const loading = ref(false);
const roles = ref<Role[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;

const allPermissions = ref<Permission[]>([]);

const formVisible = ref(false);
const permVisible = ref(false);
const formTitle = ref('新增角色');
const saving = ref(false);
const form = reactive<Partial<Role>>({
  id: undefined,
  name: '',
  code: '',
  status: 1,
});
const permTarget = ref<Role | null>(null);
const selectedPermIds = ref<number[]>([]);
const permTree = ref<InstanceType<typeof ElTree> | null>(null);

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getRolePage({ page: pageNo, size });
    roles.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`角色加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function loadPermissions(): Promise<void> {
  try {
    allPermissions.value = await getPermissionList();
  } catch (e) {
    ElMessage.error(`权限加载失败：${(e as Error).message}`);
  }
}

function openCreate(): void {
  formTitle.value = '新增角色';
  Object.assign(form, { id: undefined, name: '', code: '', status: 1 });
  formVisible.value = true;
}

function openEdit(r: Role): void {
  formTitle.value = '编辑角色';
  Object.assign(form, { id: r.id, name: r.name, code: r.code, status: r.status });
  formVisible.value = true;
}

async function save(): Promise<void> {
  if (!form.name || !form.code) {
    ElMessage.warning('请填写角色名称和编码');
    return;
  }
  saving.value = true;
  try {
    if (form.id) {
      await updateRole(form.id, form);
      ElMessage.success('角色已更新');
    } else {
      await createRole(form);
      ElMessage.success('角色创建成功');
    }
    formVisible.value = false;
    void load(1);
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function onDelete(r: Role): Promise<void> {
  await ElMessageBox.confirm(`确定删除角色「${r.name}」吗？`, '提示', { type: 'warning' });
  try {
    await deleteRole(r.id);
    ElMessage.success('已删除');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

async function openPermissions(r: Role): Promise<void> {
  permTarget.value = r;
  try {
    selectedPermIds.value = await getRolePermissions(r.id);
    permVisible.value = true;
  } catch (e) {
    ElMessage.error(`加载权限失败：${(e as Error).message}`);
  }
}

async function savePermissions(): Promise<void> {
  if (!permTarget.value) return;
  const checked = (permTree.value?.getCheckedKeys(false) as number[]) ?? [];
  try {
    await updateRole(permTarget.value.id, { permissionIds: checked });
    ElMessage.success('权限绑定已更新');
    permVisible.value = false;
    void load(page.value);
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load(1);
  void loadPermissions();
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>角色管理</h2>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && roles.length === 0" description="暂无角色" />
      <el-table v-else :data="roles">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="角色名称" min-width="160" />
        <el-table-column prop="code" label="角色编码" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'permission:assign'" size="small" @click="openPermissions(row)">权限</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page" @current-change="load" />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="formTitle" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="form.code" />
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

    <el-dialog v-model="permVisible" title="绑定权限" width="520px">
      <el-tree
        v-if="allPermissions.length"
        :data="allPermissions"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        show-checkbox
        default-expand-all
        :default-checked-keys="selectedPermIds"
        ref="permTree"
      />
      <el-empty v-else description="暂无权限点" />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" @click="savePermissions">保存</el-button>
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
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
