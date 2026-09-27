<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { queryAuditLogs } from '../api';
import type { AuditLog } from '../types';

const loading = ref(false);
const logs = ref<AuditLog[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;

/** 结果标签颜色：越权被拒是预警而非失败，用 warning 与 FAILED 区分 */
const RESULT_TAG: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  SUCCESS: 'success',
  FORBIDDEN: 'warning',
  FAILED: 'danger',
};

/** 筛选条件；时间用 el-date-picker 的字符串数组承载，提交时拆成 startTime / endTime */
const filters = reactive<{
  adminUserId?: number;
  action: string;
  resource: string;
  result: string;
  timeRange: [string, string] | null;
}>({
  adminUserId: undefined,
  action: '',
  resource: '',
  result: '',
  timeRange: null,
});

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await queryAuditLogs({
      page: pageNo,
      size,
      adminUserId: filters.adminUserId,
      action: filters.action || undefined,
      resource: filters.resource || undefined,
      result: filters.result || undefined,
      startTime: filters.timeRange?.[0] || undefined,
      endTime: filters.timeRange?.[1] || undefined,
    });
    logs.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`审计日志加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function reset(): void {
  Object.assign(filters, {
    adminUserId: undefined,
    action: '',
    resource: '',
    result: '',
    timeRange: null,
  });
  void load(1);
}

onMounted(() => {
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>审计日志</h2>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-input-number
        v-model="filters.adminUserId"
        placeholder="操作人 ID"
        :min="1"
        :controls="false"
        style="width: 130px;"
      />
      <el-input
        v-model="filters.action"
        placeholder="权限编码（模糊）"
        clearable
        style="width: 170px; margin-left: 8px;"
        @keyup.enter="load(1)"
      />
      <el-input
        v-model="filters.resource"
        placeholder="请求路径（模糊）"
        clearable
        style="width: 200px; margin-left: 8px;"
        @keyup.enter="load(1)"
      />
      <el-select v-model="filters.result" placeholder="结果" clearable style="width: 130px; margin-left: 8px;">
        <el-option label="全部" value="" />
        <el-option label="SUCCESS" value="SUCCESS" />
        <el-option label="FORBIDDEN" value="FORBIDDEN" />
        <el-option label="FAILED" value="FAILED" />
      </el-select>
      <el-date-picker
        v-model="filters.timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        style="margin-left: 8px;"
      />
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
      <el-button style="margin-left: 8px;" @click="reset">重置</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && logs.length === 0" description="暂无审计记录" />
      <el-table v-else :data="logs" row-key="id">
        <el-table-column prop="id" label="ID" width="200" />
        <el-table-column prop="adminUserId" label="操作人" width="90" />
        <el-table-column prop="action" label="权限编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" width="90" />
        <el-table-column prop="resource" label="请求路径" min-width="220" show-overflow-tooltip />
        <el-table-column label="结果" width="120">
          <template #default="{ row }">
            <el-tag :type="RESULT_TAG[row.result] || 'info'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </el-table-column>
        <el-table-column prop="occurredAt" label="操作时间" width="170" />
        <el-table-column type="expand" label="入参">
          <template #default="{ row }">
            <div class="params-cell">{{ row.params || '（无入参）' }}</div>
          </template>
        </el-table-column>
      </el-table>

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
.params-cell {
  padding: 8px 16px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
