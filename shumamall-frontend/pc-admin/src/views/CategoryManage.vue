<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createCategory, deleteCategory, getCategoryTree, updateCategory } from '../api';
import type { Category } from '../types';

const loading = ref(false);
const tree = ref<Category[]>([]);
const flat = ref<Category[]>([]);

const formVisible = ref(false);
const formTitle = ref('新增分类');
const saving = ref(false);
const form = reactive<Partial<Category>>({
  id: undefined,
  parentId: 0,
  name: '',
  icon: '',
  sortOrder: 0,
  status: 1,
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    tree.value = await getCategoryTree();
    flat.value = flatten(tree.value);
  } catch (e) {
    ElMessage.error(`分类加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function flatten(list: Category[], depth = 0): Category[] {
  const result: Category[] = [];
  for (const c of list) {
    result.push({ ...c, name: '  '.repeat(depth) + c.name });
    if (c.children?.length) result.push(...flatten(c.children, depth + 1));
  }
  return result;
}

function openCreate(parentId = 0): void {
  formTitle.value = '新增分类';
  Object.assign(form, { id: undefined, parentId, name: '', icon: '', sortOrder: 0, status: 1 });
  formVisible.value = true;
}

function openEdit(c: Category): void {
  formTitle.value = '编辑分类';
  Object.assign(form, { id: c.id, parentId: c.parentId ?? 0, name: c.name, icon: c.icon, sortOrder: c.sortOrder, status: c.status });
  formVisible.value = true;
}

async function save(): Promise<void> {
  if (!form.name) {
    ElMessage.warning('请填写分类名称');
    return;
  }
  saving.value = true;
  try {
    const dto: Partial<Category> = {
      parentId: form.parentId ?? 0,
      name: form.name,
      icon: form.icon,
      sortOrder: form.sortOrder,
      status: form.status,
    };
    if (form.id) {
      await updateCategory(form.id, dto);
      ElMessage.success('分类已更新');
    } else {
      await createCategory(dto);
      ElMessage.success('分类创建成功');
    }
    formVisible.value = false;
    void load();
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function onDelete(c: Category): Promise<void> {
  await ElMessageBox.confirm(`确定删除分类「${c.name}」吗？子分类将一并处理。`, '提示', { type: 'warning' });
  try {
    await deleteCategory(c.id);
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
      <h2>分类管理</h2>
      <el-button v-permission="'category:create'" type="primary" @click="openCreate(0)">新增顶级分类</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && tree.length === 0" description="暂无分类" />
      <el-table v-else :data="flat" row-key="id" default-expand-all>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" min-width="220" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '显示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'category:create'" size="small" @click="openCreate(row.id)">新增子类</el-button>
            <el-button v-permission="'category:edit'" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'category:delete'" size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="formTitle" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="父分类">
          <el-select v-model="form.parentId" placeholder="0 为顶级分类" style="width: 100%;">
            <el-option :value="0" label="顶级分类" />
            <el-option v-for="c in flat" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="图标 URL">
          <el-input v-model="form.icon" placeholder="可选" />
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
