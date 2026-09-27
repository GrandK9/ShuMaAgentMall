<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { cancelOrder, getOrders, payOrder, receiveOrder } from '../api';
import { isLoggedIn } from '../store';
import type { Order } from '../types';

const loading = ref(false);
const orders = ref<Order[]>([]);
const total = ref(0);
const page = ref(1);
const size = 10;
/** 状态筛选。-1 表示「全部」；不用 null 是因为 element-plus 把 null/undefined 视为未传 value，会告警 */
const statusFilter = ref<number>(-1);

/* 支付弹窗 */
const payVisible = ref(false);
const payMethod = ref(1);
const payingOrder = ref<Order | null>(null);

/** 商品摘要：明细里的商品名 × 数量 拼接 */
function itemSummary(items: Order['items']): string {
  return items?.map((i) => `${i.productName}×${i.quantity}`).join('、') || '-';
}

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getOrders(pageNo, size, statusFilter.value >= 0 ? statusFilter.value : undefined);
    orders.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`订单加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function openPay(order: Order): void {
  payingOrder.value = order;
  payMethod.value = 1;
  payVisible.value = true;
}

/** 直调支付接口：一次调用完成流水 + 订单状态 0→1（演示用，不接第三方） */
async function confirmPay(): Promise<void> {
  if (!payingOrder.value) return;
  const order = payingOrder.value;
  try {
    const resp = await payOrder(order.orderNo, payMethod.value, order.payAmount);
    ElMessage.success(`支付成功：${resp.paymentNo}（${resp.statusLabel}）`);
    payVisible.value = false;
    void load(page.value);
  } catch (e) {
    ElMessage.error(`支付失败：${(e as Error).message}`);
  }
}

async function onCancel(order: Order): Promise<void> {
  await ElMessageBox.confirm(`确定取消订单 ${order.orderNo} 吗？`, '提示', { type: 'warning' });
  try {
    await cancelOrder(order.id);
    ElMessage.success('订单已取消');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`取消失败：${(e as Error).message}`);
  }
}

/** 确认收货：一次调用完成订单状态 2→3（待收货 → 已完成） */
async function onReceive(order: Order): Promise<void> {
  await ElMessageBox.confirm(`确认已收到订单 ${order.orderNo} 的商品吗？`, '提示', { type: 'info' });
  try {
    await receiveOrder(order.id);
    ElMessage.success('已确认收货');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`确认收货失败：${(e as Error).message}`);
  }
}

const statusType = (s: number): 'primary' | 'success' | 'info' | 'warning' | 'danger' => {
  switch (s) {
    case 0:
      return 'warning'; // 待付款
    case 1:
      return 'success'; // 已支付/待发货
    case 2:
      return 'primary'; // 待收货
    case 3:
      return 'info'; // 已完成
    case 4:
      return 'danger'; // 已取消
    default:
      return 'info';
  }
};

onMounted(() => {
  if (isLoggedIn.value) {
    void load(1);
  }
});
</script>

<template>
  <div class="order-page">
    <el-empty v-if="!isLoggedIn" description="请先登录，再查看订单" />
    <template v-else>
      <el-card shadow="never">
        <div class="status-tabs">
          <el-radio-group v-model="statusFilter" @change="load(1)">
            <el-radio-button :value="-1">全部</el-radio-button>
            <el-radio-button :value="0">待付款</el-radio-button>
            <el-radio-button :value="1">待发货</el-radio-button>
            <el-radio-button :value="2">待收货</el-radio-button>
            <el-radio-button :value="3">已完成</el-radio-button>
            <el-radio-button :value="4">已取消</el-radio-button>
          </el-radio-group>
        </div>
        <div v-loading="loading">
          <el-empty v-if="!loading && orders.length === 0" description="暂无订单，去商品页下一单吧" />
          <el-table v-else :data="orders" row-key="id">
            <el-table-column prop="orderNo" label="订单号" width="230" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="130">
              <template #default="{ row }">
                <span class="amount">¥{{ row.payAmount.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="下单时间" width="180" />
            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">
                <span>{{ itemSummary(row.items) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 0">
                  <el-button type="primary" size="small" @click="openPay(row)">去支付</el-button>
                  <el-button size="small" @click="onCancel(row)">取消订单</el-button>
                </template>
                <el-button v-else-if="row.status === 2" type="primary" size="small" @click="onReceive(row)">
                  确认收货
                </el-button>
                <span v-else class="noop">—</span>
              </template>
            </el-table-column>

            <!-- 明细展开 -->
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="detail">
                  <el-table :data="row.items ?? []" size="small">
                    <el-table-column prop="productName" label="商品" min-width="200" />
                    <el-table-column prop="skuSpecs" label="规格" min-width="120" />
                    <el-table-column label="单价" width="120">
                      <template #default="{ row: it }">¥{{ it.price.toFixed(2) }}</template>
                    </el-table-column>
                    <el-table-column prop="quantity" label="数量" width="90" />
                    <el-table-column label="小计" width="130">
                      <template #default="{ row: it }">¥{{ it.subtotal.toFixed(2) }}</template>
                    </el-table-column>
                  </el-table>
                  <p v-if="row.remark" class="remark">备注：{{ row.remark }}</p>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pager">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="load"
          />
        </div>
      </el-card>
    </template>

    <!-- 支付弹窗（模拟支付，直调后端接口） -->
    <el-dialog v-model="payVisible" title="模拟支付" width="420px">
      <p class="pay-tip">
        订单号：{{ payingOrder?.orderNo }}<br />
        应付金额：<span class="amount">¥{{ payingOrder?.payAmount.toFixed(2) }}</span>
      </p>
      <el-radio-group v-model="payMethod">
        <el-radio :value="1">微信支付（模拟）</el-radio>
        <el-radio :value="2">支付宝（模拟）</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPay">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-page {
  padding: 16px 24px 24px;
}
.status-tabs {
  margin-bottom: 16px;
}
.amount {
  color: #f56c6c;
  font-weight: 600;
}
.detail {
  padding: 8px 20px;
}
.remark {
  margin: 8px 0 0;
  font-size: 13px;
  color: #909399;
}
.pay-tip {
  margin: 0 0 12px;
  line-height: 1.9;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
.noop {
  color: #c0c4cc;
}
</style>
