<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createDict, deleteDict, getDictPage, getDictTypes, updateDict } from '../api';
import type { DictItem } from '../types';

const loading = ref(false);
const items = ref<DictItem[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const typeFilter = ref('');
const statusFilter = ref<number | undefined>(undefined);
const typeOptions = ref<DictItem[]>([]);

const formVisible = ref(false);
const formTitle = ref('新增字典项');
const saving = ref(false);
const editingId = ref<string | number | null>(null);
const form = ref({
  type: '',
  typeLabel: '',
  code: '',
  label: '',
  value: '',
  parentCode: '',
  sortOrder: 0,
  status: 1,
  remark: '',
});

async function loadTypes(): Promise<void> {
  try {
    typeOptions.value = await getDictTypes();
  } catch {
    typeOptions.value = [];
  }
}

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getDictPage({
      page: pageNo,
      size,
      type: typeFilter.value || undefined,
      status: statusFilter.value,
    });
    items.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function resetForm(): void {
  form.value = {
    type: '',
    typeLabel: '',
    code: '',
    label: '',
    value: '',
    parentCode: '',
    sortOrder: 0,
    status: 1,
    remark: '',
  };
  editingId.value = null;
}

function openCreate(): void {
  resetForm();
  formTitle.value = '新增字典项';
  formVisible.value = true;
}

function openEdit(row: DictItem): void {
  editingId.value = row.id;
  formTitle.value = '编辑字典项';
  form.value = {
    type: row.type,
    typeLabel: row.typeLabel,
    code: row.code,
    label: row.label,
    value: row.value ?? '',
    parentCode: row.parentCode ?? '',
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1,
    remark: row.remark ?? '',
  };
  formVisible.value = true;
}

function onTypePick(type: string): void {
  const hit = typeOptions.value.find((t) => t.type === type);
  if (hit?.typeLabel) form.value.typeLabel = hit.typeLabel;
}

async function save(): Promise<void> {
  if (!form.value.type || !form.value.typeLabel || !form.value.code || !form.value.label) {
    ElMessage.warning('请填写类型、类型名称、编码与显示名');
    return;
  }
  saving.value = true;
  try {
    const payload = { ...form.value, parentCode: form.value.parentCode || undefined };
    if (editingId.value != null) {
      await updateDict(editingId.value, payload);
      ElMessage.success('已更新');
    } else {
      await createDict(payload);
      ElMessage.success('已创建');
    }
    formVisible.value = false;
    void loadTypes();
    void load(page.value);
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function onDelete(row: DictItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${row.label}」(${row.code})？`, '删除字典项', { type: 'warning' });
    await deleteDict(row.id);
    ElMessage.success('已删除');
    void load(page.value);
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void loadTypes();
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>系统码表</h2>
      <el-button type="primary" @click="openCreate">新增字典项</el-button>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-select v-model="typeFilter" placeholder="字典类型" clearable filterable style="width: 200px;" @change="load(1)">
        <el-option v-for="t in typeOptions" :key="t.type" :label="`${t.typeLabel} (${t.type})`" :value="t.type" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px; margin-left: 8px;" @change="load(1)">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && items.length === 0" description="暂无字典项" />
      <el-table v-else :data="items" row-key="id">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="type" label="类型" width="140" />
        <el-table-column prop="typeLabel" label="类型名称" width="120" />
        <el-table-column prop="code" label="编码" min-width="140" />
        <el-table-column prop="label" label="显示名" min-width="120" />
        <el-table-column prop="value" label="值" min-width="100" show-overflow-tooltip />
        <el-table-column prop="parentCode" label="父编码" width="120" />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page" @current-change="load" />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="formTitle" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="类型编码" required>
          <el-input v-model="form.type" placeholder="如 payment_method" @change="onTypePick(form.type)" />
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="form.typeLabel" placeholder="如 支付方式" />
        </el-form-item>
        <el-form-item label="项编码" required>
          <el-input v-model="form.code" :disabled="editingId != null" />
        </el-form-item>
        <el-form-item label="显示名" required>
          <el-input v-model="form.label" />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-model="form.value" placeholder="可选" />
        </el-form-item>
        <el-form-item label="父编码">
          <el-input v-model="form.parentCode" placeholder="树形结构时使用" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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
