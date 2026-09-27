<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getSearchIndexStatus, rebuildSearchIndex } from '../api';
import type { SearchIndexStatus } from '../types';

const loading = ref(false);
const rebuilding = ref(false);
const status = ref<SearchIndexStatus | null>(null);
/** 上次重建写入的商品数：仅在本次会话触发过重建时有值 */
const lastWritten = ref<number | null>(null);

async function loadStatus(): Promise<void> {
  loading.value = true;
  try {
    status.value = await getSearchIndexStatus();
  } catch (e) {
    ElMessage.error(`索引状态查询失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

/**
 * 全量重建：删除索引 → 重建 mapping → 拉取全部商品 → 批量写入 ES。
 * 重建期间搜索会短暂命中空索引（前端已有「无结果回退热门商品」兜底）。
 */
async function onRebuild(): Promise<void> {
  await ElMessageBox.confirm(
    '将删除并重建商品索引，随后从商品服务全量拉取数据写入 ES。确定继续吗？',
    '提示',
    { type: 'warning' },
  );
  rebuilding.value = true;
  try {
    lastWritten.value = await rebuildSearchIndex();
    ElMessage.success(`索引重建完成，写入 ${lastWritten.value} 条商品文档`);
    await loadStatus();
  } catch (e) {
    ElMessage.error(`索引重建失败：${(e as Error).message}`);
  } finally {
    rebuilding.value = false;
  }
}

onMounted(() => {
  void loadStatus();
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>搜索索引管理</h2>
      <div>
        <el-button @click="loadStatus">刷新状态</el-button>
        <el-button type="primary" :loading="rebuilding" @click="onRebuild">全量重建</el-button>
      </div>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="索引名">{{ status?.index ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="索引是否存在">
          <el-tag :type="status?.exists ? 'success' : 'danger'">
            {{ status?.exists ? '存在' : '不存在' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文档数">{{ status?.docCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="本次会话重建写入">{{ lastWritten ?? '未触发' }}</el-descriptions-item>
      </el-descriptions>
      <p class="hint">
        说明：索引由商品服务的增量同步（RabbitMQ 消息）实时维护，正常情况下无需手工重建；
        本页用于排查「搜索不到商品」时确认索引是否为空，或在 mapping 变更后做一次全量回填。
      </p>
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
.hint {
  margin: 16px 0 0;
  color: #909399;
  font-size: 13px;
  line-height: 1.8;
}
</style>
