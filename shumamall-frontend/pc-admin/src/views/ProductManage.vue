<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createProduct,
  deleteProduct,
  getProductDetail,
  getProductPage,
  updateProduct,
  updateProductStatus,
  updateProductStock,
} from '../api';
import type { Product } from '../types';

const loading = ref(false);
const products = ref<Product[]>([]);
const total = ref(0);
const page = ref(1);
const size = 50;
const keyword = ref('');
const statusFilter = ref<number | undefined>(undefined);

/* 弹窗状态 */
const formVisible = ref(false);
const formTitle = ref('新增商品');
const saving = ref(false);
const form = reactive<Partial<Product>>({
  id: undefined,
  name: '',
  subtitle: '',
  price: 0,
  stock: 100,
  status: 1,
  skuList: [{ skuCode: '', price: 0, stock: 100, specs: '{"spec":"默认规格"}' }],
});

const stockVisible = ref(false);
const stockTarget = ref<Product | null>(null);
const newStock = ref(100);

const detailVisible = ref(false);
const detail = ref<Product | null>(null);

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getProductPage({
      page: pageNo,
      size,
      keyword: keyword.value || undefined,
      status: statusFilter.value,
    });
    products.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`商品加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  formTitle.value = '新增商品';
  Object.assign(form, {
    id: undefined,
    name: '',
    subtitle: '',
    price: 0,
    stock: 100,
    status: 1,
    skuList: [{ skuCode: '', price: 0, stock: 100, specs: '{"spec":"默认规格"}' }],
  });
  formVisible.value = true;
}

async function openEdit(p: Product): Promise<void> {
  formTitle.value = '编辑商品';
  try {
    const data = await getProductDetail(p.id);
    Object.assign(form, {
      id: data.id,
      name: data.name,
      subtitle: data.subtitle || '',
      price: data.price,
      stock: data.stock ?? 0,
      status: data.status ?? 1,
      categoryId: data.categoryId,
      brandId: data.brandId,
      skuList: data.skuList?.length
        ? data.skuList
        : [{ skuCode: '', price: data.price, stock: data.stock ?? 0, specs: '{"spec":"默认规格"}' }],
    });
    formVisible.value = true;
  } catch (e) {
    ElMessage.error(`加载详情失败：${(e as Error).message}`);
  }
}

async function save(): Promise<void> {
  if (!form.name || !form.price) {
    ElMessage.warning('请填写商品名称和价格');
    return;
  }
  saving.value = true;
  const sku = form.skuList?.[0];
  if (sku && !sku.skuCode) {
    sku.skuCode = `SKU-${Date.now().toString(36).toUpperCase()}`;
  }
  const dto: Partial<Product> = {
    categoryId: form.categoryId || 1,
    brandId: form.brandId,
    name: form.name,
    subtitle: form.subtitle || undefined,
    price: form.price,
    stock: form.stock,
    status: form.status,
    skuList: form.skuList,
  };
  try {
    if (form.id) {
      await updateProduct(form.id, dto);
      ElMessage.success('商品已更新');
    } else {
      await createProduct(dto);
      ElMessage.success('商品创建成功');
    }
    formVisible.value = false;
    void load(1);
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function onDelete(p: Product): Promise<void> {
  await ElMessageBox.confirm(`确定删除商品「${p.name}」吗？`, '提示', { type: 'warning' });
  try {
    await deleteProduct(p.id);
    ElMessage.success('已删除');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

async function onToggleStatus(p: Product): Promise<void> {
  const target = p.status === 1 ? 0 : 1;
  try {
    await updateProductStatus(p.id, target);
    ElMessage.success(target === 1 ? '已上架' : '已下架');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`操作失败：${(e as Error).message}`);
  }
}

function openStock(p: Product): void {
  stockTarget.value = p;
  newStock.value = p.stock ?? 100;
  stockVisible.value = true;
}

async function saveStock(): Promise<void> {
  if (!stockTarget.value) return;
  try {
    await updateProductStock(stockTarget.value.id, newStock.value);
    ElMessage.success('库存已更新');
    stockVisible.value = false;
    void load(page.value);
  } catch (e) {
    ElMessage.error(`更新库存失败：${(e as Error).message}`);
  }
}

async function openDetail(p: Product): Promise<void> {
  try {
    detail.value = await getProductDetail(p.id);
    detailVisible.value = true;
  } catch (e) {
    ElMessage.error(`加载详情失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>商品管理</h2>
      <el-button v-permission="'product:create'" type="primary" @click="openCreate">新增商品</el-button>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-input v-model="keyword" placeholder="商品名称" clearable style="width: 220px;" @keyup.enter="load(1)" />
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px; margin-left: 8px;" @change="load(1)">
        <el-option label="全部" :value="undefined" />
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && products.length === 0" description="暂无商品" />
      <el-table v-else :data="products">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="商品名称" min-width="200" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="100" />
        <el-table-column prop="salesVolume" label="销量" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已上架' : '已下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="SKU 数" width="90">
          <template #default="{ row }">{{ row.skuList?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'product:edit'" size="small" @click="openStock(row)">改库存</el-button>
            <el-button v-permission="'product:edit'" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-permission="'product:publish'"
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="onToggleStatus(row)"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button v-permission="'product:delete'" size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page" @current-change="load" />
      </div>
    </el-card>

    <!-- 商品表单弹窗 -->
    <el-dialog v-model="formVisible" :title="formTitle" width="600px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.name" placeholder="如：iPhone 16 Pro" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" placeholder="如：A18 Pro 芯片 · 钛金属" />
        </el-form-item>
        <el-form-item label="分类 ID">
          <el-input-number v-model="form.categoryId" :min="1" />
        </el-form-item>
        <el-form-item label="品牌 ID">
          <el-input-number v-model="form.brandId" :min="1" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="100" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="SKU 编码">
          <el-input v-model="form.skuList![0].skuCode" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="SKU 规格">
          <el-input v-model="form.skuList![0].specs" placeholder="JSON 规格描述" />
        </el-form-item>
        <el-form-item label="立即上架">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 改库存弹窗 -->
    <el-dialog v-model="stockVisible" title="修改库存" width="360px">
      <p class="tip">商品：{{ stockTarget?.name }}<br />当前库存：{{ stockTarget?.stock }}</p>
      <el-input-number v-model="newStock" :min="0" :step="10" />
      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStock">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="商品详情" width="560px">
      <el-descriptions :column="1" border v-if="detail">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="副标题">{{ detail.subtitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="价格">¥{{ Number(detail.price).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="库存">{{ detail.stock }}</el-descriptions-item>
        <el-descriptions-item label="销量">{{ detail.salesVolume }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status === 1 ? '已上架' : '已下架' }}</el-descriptions-item>
        <el-descriptions-item label="SKU 列表">
          <div v-for="sku in detail.skuList" :key="sku.id">{{ sku.skuCode }} / ¥{{ sku.price }} / 库存 {{ sku.stock }}</div>
        </el-descriptions-item>
      </el-descriptions>
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
.tip {
  margin: 0 0 12px;
  line-height: 1.9;
  color: #606266;
}
</style>
