<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getDashboard } from '../api';
import type { Dashboard } from '../types';

const loading = ref(false);
const data = ref<Partial<Dashboard>>({});

const trend = computed(() => data.value.salesTrend ?? []);
const topProducts = computed(() => data.value.topProducts ?? []);

/** 趋势柱状图的比例基准：取区间内最大销售额，全 0 时回退 1 避免除零 */
const trendMax = computed(() => Math.max(1, ...trend.value.map((t) => Number(t.salesAmount ?? 0))));

/** 柱高百分比（最小 2%，让 0 销售额的日期也能看到底座） */
function barHeight(amount: number): string {
  return `${Math.max(2, (Number(amount ?? 0) / trendMax.value) * 100)}%`;
}

/** yyyy-MM-dd → MM-DD */
function shortDate(date: string): string {
  return date?.length >= 10 ? date.slice(5) : date;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    data.value = await getDashboard();
  } catch (e) {
    ElMessage.error(`仪表盘加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="dashboard-page" v-loading="loading">
    <h2 class="page-title">仪表盘</h2>
    <el-row :gutter="16">
      <el-col :span="4" :xs="12">
        <el-card shadow="hover">
          <div class="stat-label">总订单数</div>
          <div class="stat-value">{{ data.totalOrders ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4" :xs="12">
        <el-card shadow="hover">
          <div class="stat-label">总商品数</div>
          <div class="stat-value">{{ data.totalProducts ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4" :xs="12">
        <el-card shadow="hover">
          <div class="stat-label">总用户数</div>
          <div class="stat-value">{{ data.totalUsers ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4" :xs="12">
        <el-card shadow="hover">
          <div class="stat-label">今日订单数</div>
          <div class="stat-value">{{ data.todayOrders ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4" :xs="12">
        <el-card shadow="hover">
          <div class="stat-label">今日销售额</div>
          <div class="stat-value">¥{{ Number(data.todaySales ?? 0).toFixed(2) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="second-row">
      <el-col :span="14" :xs="24">
        <el-card shadow="never">
          <template #header>近 7 天销售趋势</template>
          <el-empty v-if="trend.length === 0" description="暂无销售数据" :image-size="80" />
          <div v-else class="chart">
            <div v-for="item in trend" :key="item.date" class="chart-col">
              <div class="bar-wrap">
                <div class="bar" :style="{ height: barHeight(item.salesAmount) }">
                  <span class="bar-value">¥{{ Number(item.salesAmount).toFixed(0) }}</span>
                </div>
              </div>
              <div class="bar-label">{{ shortDate(item.date) }}</div>
              <div class="bar-sub">{{ item.orderCount }} 单</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10" :xs="24">
        <el-card shadow="never">
          <template #header>热销商品 Top 10</template>
          <el-empty v-if="topProducts.length === 0" description="暂无销量数据" :image-size="80" />
          <el-table v-else :data="topProducts" size="small">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
            <el-table-column prop="salesQuantity" label="销量" width="80" />
            <el-table-column label="销售额" width="110">
              <template #default="{ row }">¥{{ Number(row.salesAmount).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 16px;
  font-size: 20px;
}
.stat-label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 8px;
}
.stat-value {
  color: #303133;
  font-size: 24px;
  font-weight: bold;
}
.second-row {
  margin-top: 16px;
}
/* 无图表库，用等高列 + 百分比高度柱体做一个轻量柱状图（够看清趋势即可） */
.chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 12px;
  height: 240px;
}
.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.bar {
  position: relative;
  width: 60%;
  min-height: 2px;
  background: linear-gradient(180deg, #409eff 0%, #79bbff 100%);
  border-radius: 4px 4px 0 0;
}
.bar-value {
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}
.bar-label {
  margin-top: 6px;
  font-size: 12px;
  color: #303133;
}
.bar-sub {
  font-size: 12px;
  color: #909399;
}
</style>
