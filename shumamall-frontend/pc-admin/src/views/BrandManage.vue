<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createBrand, deleteBrand, getBrandPage, updateBrand } from '../api';
import type { Brand } from '../types';

const loading = ref(false);
const brands = ref<Brand[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const keyword = ref('');

const formVisible = ref(false);
const formTitle = ref('新增品牌');
const saving = ref(false);
const form = reactive<Partial<Brand>>({
  id: undefined,
  name: '',
  logo: '',
  description: '',
  sortOrder: 0,
  status: 1,
});

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getBrandPage({ page: pageNo, size, keyword: keyword.value || undefined });
    brands.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`品牌加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  formTitle.value = '新增品牌';
  Object.assign(form, { id: undefined, name: '', logo: '', description: '', sortOrder: 0, status: 1 });
  formVisible.value = true;
}

function openEdit(b: Brand): void {
  formTitle.value = '编辑品牌';
  Object.assign(form, { id: b.id, name: b.name, logo: b.logo, description: b.description, sortOrder: b.sortOrder, status: b.status });
  formVisible.value = true;
}

async function save(): Promise<void> {
  if (!form.name) {
    ElMessage.warning('请填写品牌名称');
    return;
  }
  saving.value = true;
  try {
    if (form.id) {
      await updateBrand(form);
      ElMessage.success('品牌已更新');
    } else {
      await createBrand(form);
      ElMessage.success('品牌创建成功');
    }
    formVisible.value = false;
    void load(1);
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function onDelete(b: Brand): Promise<void> {
  await ElMessageBox.confirm(`确定删除品牌「${b.name}」吗？`, '提示', { type: 'warning' });
  try {
    await deleteBrand(b.id);
    ElMessage.success('已删除');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>品牌管理</h2>
      <el-button v-permission="'brand:create'" type="primary" @click="openCreate">新增品牌</el-button>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-input v-model="keyword" placeholder="品牌名称" clearable style="width: 220px;" @keyup.enter="load(1)" />
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && brands.length === 0" description="暂无品牌" />
      <el-table v-else :data="brands">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="品牌名称" min-width="160" />
        <el-table-column label="Logo" min-width="120">
          <template #default="{ row }">
            <el-image v-if="row.logo" :src="row.logo" style="width: 60px; height: 60px;" fit="contain" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '显示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'brand:edit'" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'brand:delete'" size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page" @current-change="load" />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="formTitle" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="品牌名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="Logo URL">
          <el-input v-model="form.logo" placeholder="可选" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
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
.filter-bar {
  margin-bottom: 16px;
  padding: 16px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
