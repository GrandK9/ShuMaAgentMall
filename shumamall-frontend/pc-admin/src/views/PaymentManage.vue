<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getPaymentPage, refundPayment } from '../api';
import type { PaymentRecord } from '../types';

const loading = ref(false);
const records = ref<PaymentRecord[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const orderNo = ref('');

function statusType(s: number): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  switch (s) {
    case 0: return 'warning';
    case 1: return 'success';
    case 2: return 'danger';
    case 3: return 'info';
    default: return 'info';
  }
}

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getPaymentPage({ page: pageNo, size, orderNo: orderNo.value || undefined });
    records.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`支付记录加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function onRefund(row: PaymentRecord): Promise<void> {
  await ElMessageBox.confirm(`确定为支付流水 ${row.paymentNo} 发起退款吗？`, '提示', { type: 'warning' });
  try {
    await refundPayment(row.id);
    ElMessage.success('退款已发起');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`退款失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>支付记录</h2>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-input v-model="orderNo" placeholder="订单号" clearable style="width: 260px;" @keyup.enter="load(1)" />
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && records.length === 0" description="暂无支付记录" />
      <el-table v-else :data="records">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="paymentNo" label="支付流水号" min-width="180" />
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="userId" label="用户 ID" width="90" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="支付方式" width="110">
          <template #default="{ row }">{{ row.paymentMethod === 1 ? '微信' : row.paymentMethod === 2 ? '支付宝' : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="thirdPartyNo" label="第三方流水号" min-width="160" />
        <el-table-column prop="paidAt" label="支付时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" v-permission="'order:refund'" size="small" type="danger" link @click="onRefund(row)">退款</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page" @current-change="load" />
      </div>
    </el-card>
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
