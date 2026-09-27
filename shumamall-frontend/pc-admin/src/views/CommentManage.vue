<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getCommentPage, hideComment, restoreComment } from '../api';
import type { CommentVO } from '../types';

const loading = ref(false);
const comments = ref<CommentVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = 10;
const productId = ref('');
const statusFilter = ref<number | null>(null);

function statusMeta(s: number): { type: 'success' | 'info' | 'danger'; label: string } {
  switch (s) {
    case 0:
      return { type: 'success', label: '正常' };
    case 1:
      return { type: 'info', label: '作者删除' };
    case 2:
      return { type: 'danger', label: '管理员隐藏' };
    default:
      return { type: 'info', label: `未知(${s})` };
  }
}

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getCommentPage({
      page: pageNo,
      size,
      productId: productId.value ? Number(productId.value) : undefined,
      status: statusFilter.value ?? undefined,
    });
    comments.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`评论加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

/** 隐藏评论（内容违规）；id 是雪花 ID，按字符串回传避免精度丢失 */
async function onHide(row: CommentVO): Promise<void> {
  await ElMessageBox.confirm('确定隐藏这条评论吗？隐藏后普通用户不再可见。', '提示', { type: 'warning' });
  try {
    await hideComment(row.id);
    ElMessage.success('评论已隐藏');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`隐藏失败：${(e as Error).message}`);
  }
}

async function onRestore(row: CommentVO): Promise<void> {
  try {
    await restoreComment(row.id);
    ElMessage.success('评论已恢复');
    void load(page.value);
  } catch (e) {
    ElMessage.error(`恢复失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>评论管理</h2>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-input v-model="productId" placeholder="商品 ID（精确匹配）" clearable style="width: 200px;" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 160px; margin-left: 8px;">
        <el-option :value="0" label="正常" />
        <el-option :value="1" label="作者删除" />
        <el-option :value="2" label="管理员隐藏" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && comments.length === 0" description="暂无评论" />
      <el-table v-else :data="comments" row-key="id">
        <el-table-column prop="id" label="评论 ID" width="190" />
        <el-table-column prop="productId" label="商品 ID" width="100" />
        <el-table-column label="评论人" width="130">
          <template #default="{ row }">
            <span>{{ row.username || `用户 ${row.userId ?? '-'}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="260">
          <template #default="{ row }">
            <span>{{ row.content }}</span>
            <el-tag v-if="row.parentId" size="small" type="info" class="tag-gap">回复</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="80">
          <template #default="{ row }">{{ row.rating ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="互动" width="150">
          <template #default="{ row }">
            <span class="muted">赞 {{ row.likeCount ?? 0 }} · 回复 {{ row.replyCount ?? 0 }} · 举报 {{ row.reportCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="评论时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 2" size="small" type="danger" link @click="onHide(row)">隐藏</el-button>
            <el-button v-else size="small" type="primary" link @click="onRestore(row)">恢复</el-button>
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
.tag-gap {
  margin-left: 6px;
}
.muted {
  color: #909399;
  font-size: 13px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
