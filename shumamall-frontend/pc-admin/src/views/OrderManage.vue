<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getOrderDetail, getOrderPage, updateOrderStatus } from '../api';
import type { Order } from '../types';

const loading = ref(false);
const orders = ref<Order[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const statusFilter = ref<number | undefined>(undefined);

const statusOptions = [
  { label: '全部', value: undefined },
  { label: '待付款', value: 0 },
  { label: '待发货', value: 1 },
  { label: '待收货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 },
  { label: '售后中', value: 5 },
];

const detailVisible = ref(false);
const detail = ref<Order | null>(null);
const statusVisible = ref(false);
const statusTarget = ref<Order | null>(null);
const newStatus = ref<number>(0);
const remark = ref('');

function statusType(s: number): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  switch (s) {
    case 0: return 'warning';
    case 1: return 'success';
    case 2: return 'primary';
    case 3: return 'info';
    case 4: return 'danger';
    default: return 'info';
  }
}

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getOrderPage({ page: pageNo, size, status: statusFilter.value });
    orders.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`订单加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function openDetail(order: Order): Promise<void> {
  try {
    detail.value = await getOrderDetail(order.id);
    detailVisible.value = true;
  } catch (e) {
    ElMessage.error(`加载详情失败：${(e as Error).message}`);
  }
}

function openStatus(order: Order): void {
  statusTarget.value = order;
  newStatus.value = order.status;
  remark.value = '';
  statusVisible.value = true;
}

async function saveStatus(): Promise<void> {
  if (!statusTarget.value) return;
  try {
    await updateOrderStatus(statusTarget.value.id, newStatus.value, remark.value || undefined);
    ElMessage.success('状态已更新');
    statusVisible.value = false;
    void load(page.value);
  } catch (e) {
    ElMessage.error(`更新失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>订单管理</h2>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-select v-model="statusFilter" placeholder="订单状态" clearable style="width: 160px;" @change="load(1)">
        <el-option v-for="opt in statusOptions" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && orders.length === 0" description="暂无订单" />
      <el-table v-else :data="orders" row-key="id">
        <el-table-column prop="orderNo" label="订单号" min-width="200" />
        <el-table-column prop="userId" label="用户 ID" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">¥{{ Number(row.payAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'order:edit'" size="small" type="primary" @click="openStatus(row)">改状态</el-button>
          </template>
        </el-table-column>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <el-table :data="row.items ?? []" size="small">
                <el-table-column prop="productName" label="商品" min-width="200" />
                <el-table-column prop="skuSpecs" label="规格" min-width="120" />
                <el-table-column label="单价" width="120">
                  <template #default="{ row: it }">¥{{ Number(it.price).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="quantity" label="数量" width="80" />
                <el-table-column label="小计" width="120">
                  <template #default="{ row: it }">¥{{ Number(it.subtotal).toFixed(2) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page" @current-change="load" />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="订单详情" width="680px">
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ Number(detail.totalAmount).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">¥{{ Number(detail.payAmount).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="支付单号">{{ detail.paymentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail?.items ?? []" size="small" style="margin-top: 16px;">
        <el-table-column prop="productName" label="商品" min-width="200" />
        <el-table-column prop="skuSpecs" label="规格" min-width="120" />
        <el-table-column label="单价" width="120">
          <template #default="{ row: it }">¥{{ Number(it.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="小计" width="120">
          <template #default="{ row: it }">¥{{ Number(it.subtotal).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="statusVisible" title="修改订单状态" width="420px">
      <el-form label-width="80px">
        <el-form-item label="新状态">
          <el-select v-model="newStatus" style="width: 100%;">
            <el-option v-for="opt in statusOptions.filter((o) => o.value !== undefined)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStatus">保存</el-button>
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
.detail {
  padding: 8px 20px;
}
</style>
