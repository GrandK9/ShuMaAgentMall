<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createSeckillActivity,
  listSeckillActivities,
  offlineSeckillActivity,
  onlineSeckillActivity,
} from '../api';
import type { SeckillActivity } from '../types';

const loading = ref(false);
const activities = ref<SeckillActivity[]>([]);
const statusFilter = ref<number | undefined>(undefined);

const formVisible = ref(false);
const saving = ref(false);
const form = ref({
  name: '',
  skuId: undefined as number | undefined,
  productId: undefined as number | undefined,
  seckillPrice: 0.01,
  totalStock: 10,
  perUserLimit: 1,
  startTime: '',
  endTime: '',
});

const statusOptions = [
  { label: '全部', value: undefined },
  { label: '未上线', value: 0 },
  { label: '已上线', value: 1 },
  { label: '已结束', value: 2 },
];

function statusTagType(s: number): 'info' | 'success' | 'warning' {
  if (s === 1) return 'success';
  if (s === 0) return 'warning';
  return 'info';
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    activities.value = await listSeckillActivities(statusFilter.value);
  } catch (e) {
    ElMessage.error(`加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  form.value = {
    name: '',
    skuId: undefined,
    productId: undefined,
    seckillPrice: 0.01,
    totalStock: 10,
    perUserLimit: 1,
    startTime: '',
    endTime: '',
  };
  formVisible.value = true;
}

async function save(): Promise<void> {
  const f = form.value;
  if (!f.name || f.skuId == null || f.productId == null || !f.startTime || !f.endTime) {
    ElMessage.warning('请填写完整活动信息');
    return;
  }
  saving.value = true;
  try {
    await createSeckillActivity({
      name: f.name,
      skuId: f.skuId,
      productId: f.productId,
      seckillPrice: f.seckillPrice,
      totalStock: f.totalStock,
      perUserLimit: f.perUserLimit,
      startTime: f.startTime,
      endTime: f.endTime,
    });
    ElMessage.success('活动已创建（需上线后才会预热库存）');
    formVisible.value = false;
    void load();
  } catch (e) {
    ElMessage.error(`创建失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function doOnline(row: SeckillActivity): Promise<void> {
  try {
    await ElMessageBox.confirm(`上线活动「${row.name}」并预热 Redis 库存？`, '上线', { type: 'warning' });
    await onlineSeckillActivity(row.id);
    ElMessage.success('已上线');
    void load();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`操作失败：${(e as Error).message}`);
  }
}

async function doOffline(row: SeckillActivity): Promise<void> {
  try {
    await ElMessageBox.confirm(`下线活动「${row.name}」？`, '下线', { type: 'warning' });
    await offlineSeckillActivity(row.id);
    ElMessage.success('已下线');
    void load();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`操作失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>秒杀活动</h2>
      <div>
        <router-link to="/order"><el-button link>← 返回订单</el-button></router-link>
        <el-button v-permission="'order:edit'" type="primary" style="margin-left: 8px;" @click="openCreate">新建活动</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-select v-model="statusFilter" placeholder="状态" style="width: 160px;" @change="load">
        <el-option v-for="opt in statusOptions" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px;" @click="load">刷新</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && activities.length === 0" description="暂无秒杀活动" />
      <el-table v-else :data="activities" row-key="id">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="活动名称" min-width="160" />
        <el-table-column prop="productId" label="商品 ID" width="90" />
        <el-table-column prop="skuId" label="SKU ID" width="90" />
        <el-table-column label="秒杀价" width="100">
          <template #default="{ row }">¥{{ row.seckillPrice }}</template>
        </el-table-column>
        <el-table-column prop="totalStock" label="活动库存" width="90" />
        <el-table-column label="剩余" width="90">
          <template #default="{ row }">
            <span v-if="row.remainingStock == null">—</span>
            <span v-else>{{ row.remainingStock }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始" min-width="160" />
        <el-table-column prop="endTime" label="结束" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.statusLabel || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="'order:edit'" size="small" type="success" @click="doOnline(row)">上线</el-button>
            <el-button v-if="row.status === 1" v-permission="'order:edit'" size="small" type="warning" @click="doOffline(row)">下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" title="新建秒杀活动" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="商品 ID" required>
          <el-input-number v-model="form.productId" :min="1" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="SKU ID" required>
          <el-input-number v-model="form.skuId" :min="1" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="秒杀价" required>
          <el-input-number v-model="form.seckillPrice" :min="0.01" :precision="2" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="活动库存" required>
          <el-input-number v-model="form.totalStock" :min="1" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number v-model="form.perUserLimit" :min="1" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
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
</style>
