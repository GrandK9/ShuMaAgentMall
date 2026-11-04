<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getAddresses, getSeckillResult, listSeckillActivities, submitSeckill } from '../api';
import type { Address, SeckillActivity, SeckillResult } from '../types';
import { isLoggedIn } from '../store';

const loading = ref(false);
const activities = ref<SeckillActivity[]>([]);
const addresses = ref<Address[]>([]);

const grabVisible = ref(false);
const grabTarget = ref<SeckillActivity | null>(null);
const addressId = ref<number | undefined>(undefined);
const remark = ref('');
const grabbing = ref(false);

const resultVisible = ref(false);
const pollResult = ref<SeckillResult | null>(null);

async function loadActivities(): Promise<void> {
  loading.value = true;
  try {
    activities.value = await listSeckillActivities();
  } catch (e) {
    ElMessage.error(`活动加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function ensureAddresses(): Promise<boolean> {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再抢购');
    return false;
  }
  try {
    addresses.value = await getAddresses();
    if (addresses.value.length === 0) {
      ElMessage.warning('请先添加收货地址');
      return false;
    }
    const def = addresses.value.find((a) => a.isDefault === 1);
    addressId.value = def?.id ?? addresses.value[0].id;
    return true;
  } catch (e) {
    ElMessage.error(`地址加载失败：${(e as Error).message}`);
    return false;
  }
}

async function openGrab(row: SeckillActivity): Promise<void> {
  grabTarget.value = row;
  remark.value = '';
  if (!(await ensureAddresses())) return;
  grabVisible.value = true;
}

async function pollUntilDone(requestId: string): Promise<void> {
  const max = 30;
  for (let i = 0; i < max; i++) {
    await new Promise((r) => setTimeout(r, 1500));
    try {
      const r = await getSeckillResult(requestId);
      pollResult.value = r;
      if (r.status !== 0 && r.status !== 4) return;
    } catch {
      /* 继续轮询 */
    }
  }
}

async function doGrab(): Promise<void> {
  if (!grabTarget.value || addressId.value == null) return;
  grabbing.value = true;
  try {
    const r = await submitSeckill({
      activityId: grabTarget.value.id,
      addressId: addressId.value,
      remark: remark.value || undefined,
    });
    grabVisible.value = false;
    pollResult.value = r;
    resultVisible.value = true;
    if (r.status === 0 || r.status === 4) {
      void pollUntilDone(r.requestId);
    }
    void loadActivities();
  } catch (e) {
    ElMessage.error(`抢购失败：${(e as Error).message}`);
  } finally {
    grabbing.value = false;
  }
}

function stockHint(row: SeckillActivity): string {
  if (row.remainingStock == null) return '未开始';
  if (row.remainingStock <= 0) return '已抢光';
  return `剩余 ${row.remainingStock}`;
}

onMounted(() => {
  void loadActivities();
});
</script>

<template>
  <div class="seckill-page">
    <header class="page-header">
      <h1 class="page-title">限时秒杀</h1>
      <el-button @click="loadActivities">刷新</el-button>
    </header>

    <section class="seckill-body" v-loading="loading">
      <el-empty v-if="!loading && activities.length === 0" description="当前没有进行中的秒杀活动" />
      <div v-else class="act-grid">
        <article v-for="act in activities" :key="act.id" class="act-card">
          <h2 class="act-name">{{ act.name }}</h2>
          <p class="price amount">秒杀价 ¥{{ act.seckillPrice }}</p>
          <p class="meta">商品 #{{ act.productId }} · SKU #{{ act.skuId }}</p>
          <p class="meta">{{ act.startTime }} ~ {{ act.endTime }}</p>
          <el-tag size="small">{{ stockHint(act) }}</el-tag>
          <div class="actions">
            <el-button
              type="danger"
              :disabled="act.remainingStock != null && act.remainingStock <= 0"
              @click="openGrab(act)"
            >
              立即抢购
            </el-button>
          </div>
        </article>
      </div>
    </section>

    <el-dialog v-model="grabVisible" title="确认抢购" width="440px">
      <p v-if="grabTarget">活动：{{ grabTarget.name }}（¥{{ grabTarget.seckillPrice }}）</p>
      <el-form label-width="90px">
        <el-form-item label="收货地址" required>
          <el-select v-model="addressId" placeholder="选择地址" style="width: 100%;">
            <el-option
              v-for="a in addresses"
              :key="a.id"
              :label="`${a.consignee} ${a.phone} ${a.province}${a.city}${a.detailAddress}`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grabVisible = false">取消</el-button>
        <el-button type="danger" :loading="grabbing" @click="doGrab">确认抢购</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="抢购结果" width="420px">
      <template v-if="pollResult">
        <p><strong>状态：</strong>{{ pollResult.statusLabel || pollResult.status }}</p>
        <p v-if="pollResult.message">{{ pollResult.message }}</p>
        <p v-if="pollResult.orderNo"><strong>订单号：</strong>{{ pollResult.orderNo }}</p>
        <p v-if="pollResult.requestId" class="muted">凭证：{{ pollResult.requestId }}</p>
      </template>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">知道了</el-button>
        <router-link v-if="pollResult?.orderNo" to="/orders"><el-button link>查看订单</el-button></router-link>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.seckill-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-6) var(--space-8) var(--space-8);
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--separator);
}
.page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ink);
  line-height: 1.15;
}
.seckill-body {
  flex: 1;
  min-height: 12rem;
}
.act-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-4);
}
.act-card {
  background: var(--surface-card);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  transition: background var(--duration-fast) var(--ease-out);
}
.act-card:hover {
  background: var(--surface-hover);
}
.act-name {
  margin: 0 0 var(--space-2);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--ink);
}
.price {
  color: var(--ink);
  font-size: var(--text-lg);
  font-weight: 700;
  margin: var(--space-2) 0;
  font-variant-numeric: tabular-nums;
}
.meta {
  color: var(--muted);
  font-size: var(--text-sm);
  margin: var(--space-1) 0;
}
.actions {
  margin-top: var(--space-3);
}
.muted {
  color: var(--muted);
  font-size: var(--text-xs);
}
</style>
