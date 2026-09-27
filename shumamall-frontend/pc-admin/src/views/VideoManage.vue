<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  completeVideoUpload,
  deleteVideo,
  getVideoMeta,
  getVideoPage,
  getVideoPlayInfo,
  getVideoUploadSession,
  initVideoUpload,
  retranscodeVideo,
  uploadVideoChunk,
} from '../api';
import type { VideoMeta, VideoPlayInfo } from '../types';
import HlsPlayer from '../components/HlsPlayer.vue';

/** 管理端校验档位（与后端 VideoConstants 一致）：≤ 2GB / ≤ 30min / 长边 ≤ 4096、短边 ≤ 2160 */
const MAX_SIZE_ADMIN = 2 * 1024 * 1024 * 1024;
const MAX_DURATION_ADMIN = 1800;
const MAX_LONG_SIDE = 4096;
const MAX_SHORT_SIDE = 2160;

/** 浏览器端上传会话在 localStorage 的键：用于「刷新页面后继续未完成的上传」 */
const SESSION_STORAGE_KEY = 'admin_video_upload_session';

/** 后台处理轮询：最多等 60 次 × 2s */
const POLL_MAX_ROUNDS = 60;
const POLL_INTERVAL_MS = 2000;

const loading = ref(false);
const videos = ref<VideoMeta[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const statusFilter = ref('');
const uploaderFilter = ref('');

/* ---------------- 上传 ---------------- */

const fileInput = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
/** 上传阶段文案（预检 / 分片 / 合并 / 转码中） */
const uploadStage = ref('');
const uploadPercent = ref(0);
/** 本次上传跳过的已传分片数（>0 说明走了断点续传） */
const resumedChunks = ref(0);

/* ---------------- 预览 ---------------- */

const previewVisible = ref(false);
const previewLoading = ref(false);
const playInfo = ref<VideoPlayInfo | null>(null);

/** 本地上传会话记录（localStorage），文件与分片大小一起存，续传时无需重新 init */
interface SavedUpload {
  name: string;
  size: number;
  lastModified: number;
  sessionId: string;
  chunkSize: number;
}

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  try {
    const res = await getVideoPage({
      page: pageNo,
      size,
      status: statusFilter.value || undefined,
      uploaderType: uploaderFilter.value || undefined,
    });
    videos.value = res.records;
    total.value = res.total;
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`视频列表加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

/**
 * 浏览器端预检：用原生 video 元素探测时长/分辨率，并顺带确认浏览器能否解码。
 *
 * 只做「大小 / 时长 / 分辨率 / 可解码性」四项，**不做编码白名单判定**——
 * 浏览器能解 VP9/AV1，而服务端只放行 H.264/H.265，编码是否合规以服务端 ffprobe 为准。
 *
 * @param file 待上传文件
 * @returns 探测到的时长与分辨率
 */
function probeLocalFile(file: File): Promise<{ duration: number; width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const probe = document.createElement('video');
    probe.preload = 'metadata';
    probe.onloadedmetadata = () => {
      const info = { duration: probe.duration, width: probe.videoWidth, height: probe.videoHeight };
      URL.revokeObjectURL(objectUrl);
      resolve(info);
    };
    probe.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error('浏览器无法解码该文件（编码不受支持或文件损坏）'));
    };
    probe.src = objectUrl;
  });
}

/**
 * 上传前拦截：超限文件直接在浏览器侧拒绝，不浪费一次上传。
 *
 * @param file 待上传文件
 * @returns 探测到的时长与分辨率
 */
async function precheck(file: File): Promise<{ duration: number; width: number; height: number }> {
  if (file.size > MAX_SIZE_ADMIN) {
    throw new Error(`文件 ${formatSize(file.size)} 超过管理端上限 ${formatSize(MAX_SIZE_ADMIN)}`);
  }
  const info = await probeLocalFile(file);
  if (!Number.isFinite(info.duration) || info.duration <= 0) {
    throw new Error('无法读取视频时长，文件可能不是有效视频');
  }
  if (info.duration > MAX_DURATION_ADMIN) {
    throw new Error(`时长 ${info.duration.toFixed(1)}s 超过管理端上限 ${MAX_DURATION_ADMIN}s（30 分钟）`);
  }
  if (Math.max(info.width, info.height) > MAX_LONG_SIDE || Math.min(info.width, info.height) > MAX_SHORT_SIDE) {
    throw new Error(`分辨率 ${info.width}×${info.height} 超限（长边 ≤ ${MAX_LONG_SIDE}、短边 ≤ ${MAX_SHORT_SIDE}）`);
  }
  return info;
}

/** 读取本地上传会话记录 */
function readSavedUpload(): SavedUpload | null {
  const raw = localStorage.getItem(SESSION_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as SavedUpload;
  } catch {
    localStorage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
}

/**
 * 尝试复用上次未完成的上传会话（页面刷新 / 上传中断后续传）。
 *
 * @param file 当前选择的文件
 * @returns 可续传的会话记录；无匹配或会话已失效时返回 null
 */
async function findResumableUpload(file: File): Promise<SavedUpload | null> {
  const saved = readSavedUpload();
  // 只认同一个文件（名字 + 大小 + 修改时间三者一致），避免把别的文件的分片混进来
  if (!saved || saved.name !== file.name || saved.size !== file.size || saved.lastModified !== file.lastModified) {
    return null;
  }
  try {
    const session = await getVideoUploadSession(saved.sessionId);
    if (session && session.status === 'uploading') {
      return saved;
    }
  } catch {
    // 会话已过期（MongoDB TTL 24h 清理）或记录损坏 → 走全新上传
  }
  localStorage.removeItem(SESSION_STORAGE_KEY);
  return null;
}

/**
 * 分片上传并合并分片。
 *
 * @param file 待上传文件
 * @returns 会话 ID（后续轮询处理结果用）
 */
async function uploadByChunks(file: File): Promise<string> {
  const saved = await findResumableUpload(file);
  let { sessionId, chunkSize } = saved ?? { sessionId: '', chunkSize: 0 };
  let uploaded = new Set<number>();

  if (saved) {
    const session = await getVideoUploadSession(sessionId);
    uploaded = new Set(session.chunks ?? []);
    resumedChunks.value = uploaded.size;
    uploadStage.value = `检测到未完成会话，断点续传（已传 ${uploaded.size} 片）`;
  } else {
    const init = await initVideoUpload(file);
    sessionId = init.sessionId;
    chunkSize = init.chunkSize;
    resumedChunks.value = 0;
    const record: SavedUpload = {
      name: file.name,
      size: file.size,
      lastModified: file.lastModified,
      sessionId,
      chunkSize,
    };
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(record));
  }

  const chunkCount = Math.ceil(file.size / chunkSize);
  let done = uploaded.size;
  uploadPercent.value = Math.round((done / chunkCount) * 100);

  for (let index = 0; index < chunkCount; index += 1) {
    if (uploaded.has(index)) {
      continue;
    }
    const blob = file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize));
    await uploadVideoChunk(sessionId, index, blob);
    done += 1;
    uploadStage.value = `上传分片 ${done}/${chunkCount}`;
    uploadPercent.value = Math.round((done / chunkCount) * 100);
  }

  uploadStage.value = '分片合并中…';
  await completeVideoUpload(sessionId);
  // 合并成功即视为上传阶段结束，清掉本地记录；后续失败重试走服务端重转码，不再续传
  localStorage.removeItem(SESSION_STORAGE_KEY);
  return sessionId;
}

/**
 * 轮询后台处理结果：先等上传会话落到 processed 拿到 videoId，再等元数据落到 transcoded / failed。
 *
 * @param sessionId 上传会话 ID
 * @returns 最终元数据；超时返回 null
 */
async function pollProcessing(sessionId: string): Promise<VideoMeta | null> {
  // 雪花 ID 用字符串承载：19 位超出 JS 安全整数范围，转 number 会失真
  let videoId: string | undefined;
  for (let round = 0; round < POLL_MAX_ROUNDS; round += 1) {
    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
    if (!videoId) {
      const session = await getVideoUploadSession(sessionId);
      if (session.status === 'processed') {
        videoId = session.videoId;
      }
      continue;
    }
    const meta = await getVideoMeta(videoId);
    if (meta.status === 'transcoded' || meta.status === 'failed') {
      return meta;
    }
  }
  return null;
}

async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 立即清空 input，否则连续选同一个文件不会再触发 change
  input.value = '';
  if (!file) {
    return;
  }

  uploading.value = true;
  uploadPercent.value = 0;
  resumedChunks.value = 0;
  try {
    uploadStage.value = '浏览器端预检（大小 / 时长 / 分辨率）…';
    const info = await precheck(file);
    uploadStage.value = `预检通过：${info.width}×${info.height} / ${info.duration.toFixed(1)}s / ${formatSize(file.size)}`;

    const sessionId = await uploadByChunks(file);

    uploadStage.value = '服务端 ffprobe 校验 + HLS 切片中…';
    const meta = await pollProcessing(sessionId);
    if (!meta) {
      ElMessage.warning('处理超时，请稍后刷新列表查看结果');
    } else if (meta.status === 'transcoded') {
      ElMessage.success(`转码完成：${formatDuration(meta.duration)} / ${meta.hlsSegments?.length ?? 0} 个分片`);
    } else {
      ElMessage.error(`服务端校验未通过：${meta.failReason || '原因未知'}`);
    }
    await load(1);
  } catch (e) {
    localStorage.removeItem(SESSION_STORAGE_KEY);
    ElMessage.error(`上传失败：${(e as Error).message}`);
  } finally {
    uploading.value = false;
    uploadStage.value = '';
    uploadPercent.value = 0;
  }
}

async function onRetranscode(row: VideoMeta): Promise<void> {
  try {
    await retranscodeVideo(row.videoId);
    ElMessage.success('已触发重新转码，稍后刷新查看结果');
    await load(page.value);
  } catch (e) {
    ElMessage.error(`重新转码失败：${(e as Error).message}`);
  }
}

async function onDelete(row: VideoMeta): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除视频 #${row.videoId} 吗？将同时清理 MinIO 原始文件、HLS 分片与缩略图`,
    '提示',
    { type: 'warning' },
  );
  try {
    await deleteVideo(row.videoId);
    ElMessage.success('已删除');
    await load(page.value);
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

async function onPreview(row: VideoMeta): Promise<void> {
  previewVisible.value = true;
  previewLoading.value = true;
  playInfo.value = null;
  try {
    playInfo.value = await getVideoPlayInfo(row.videoId);
  } catch (e) {
    ElMessage.error(`获取播放地址失败：${(e as Error).message}`);
  } finally {
    previewLoading.value = false;
  }
}

/** 处理状态 → 标签颜色 */
function statusTagType(status: string): string {
  if (status === 'transcoded') return 'success';
  if (status === 'failed') return 'danger';
  return 'warning';
}

/** 处理状态 → 中文文案 */
function statusLabel(status: string): string {
  const map: Record<string, string> = {
    uploading: '上传中',
    validating: '校验中',
    transcoded: '已转码',
    failed: '失败',
  };
  return map[status] ?? status;
}

/** 字节数格式化 */
function formatSize(bytes?: number): string {
  if (bytes === undefined || bytes === null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/** 秒数格式化 */
function formatDuration(seconds?: number): string {
  if (seconds === undefined || seconds === null) return '-';
  const m = Math.floor(seconds / 60);
  const s = (seconds % 60).toFixed(1);
  return m > 0 ? `${m}m${s}s` : `${s}s`;
}

onMounted(() => {
  void load(1);
});
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h2>视频管理</h2>
      <div>
        <input
          ref="fileInput"
          type="file"
          accept="video/mp4,video/quicktime,video/*"
          style="display: none;"
          @change="onFilePicked"
        />
        <!-- 上传走 C 端共用的 /api/v1/video/** 接口，权限表无对应权限点，不做按钮级控制 -->
        <el-button type="primary" :loading="uploading" @click="fileInput?.click()">上传视频</el-button>
      </div>
    </div>

    <el-card v-if="uploading" shadow="never" class="upload-bar">
      <p class="upload-stage">{{ uploadStage }}</p>
      <el-progress :percentage="uploadPercent" :stroke-width="14" />
      <p v-if="resumedChunks > 0" class="upload-hint">
        断点续传：跳过已上传的 {{ resumedChunks }} 个分片（页面刷新后仍可继续）
      </p>
    </el-card>

    <el-card shadow="never" class="filter-bar">
      <el-select v-model="statusFilter" placeholder="处理状态" clearable style="width: 160px;">
        <el-option label="上传中" value="uploading" />
        <el-option label="校验中" value="validating" />
        <el-option label="已转码" value="transcoded" />
        <el-option label="失败" value="failed" />
      </el-select>
      <el-select v-model="uploaderFilter" placeholder="上传者" clearable style="width: 140px; margin-left: 8px;">
        <el-option label="管理端" value="admin" />
        <el-option label="用户端" value="user" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px;" @click="load(1)">查询</el-button>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!loading && videos.length === 0" description="暂无视频" />
      <el-table v-else :data="videos">
        <el-table-column prop="videoId" label="视频 ID" width="200" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="130">
          <template #default="{ row }">{{ row.uploaderType }} / {{ row.sourceType }}</template>
        </el-table-column>
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="分辨率" width="120">
          <template #default="{ row }">{{ row.width ? `${row.width}×${row.height}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="分片" width="80">
          <template #default="{ row }">{{ row.hlsSegments?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">{{ row.uploadedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'fail-reason': row.failReason }">{{ row.failReason || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.status !== 'transcoded'" @click="onPreview(row)">预览</el-button>
            <el-button v-permission="'video:retranscode'" size="small" @click="onRetranscode(row)">重转码</el-button>
            <el-button v-permission="'video:delete'" size="small" type="danger" link @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="previewVisible" title="视频预览" width="720px" destroy-on-close>
      <div v-loading="previewLoading">
        <HlsPlayer
          v-if="playInfo?.playlistUrl"
          :playlist-url="playInfo.playlistUrl"
          :segment-urls="playInfo.segmentUrls"
          :poster="playInfo.thumbnailUrl"
        />
        <el-alert
          v-else-if="playInfo && !previewLoading"
          type="warning"
          :closable="false"
          :title="`当前状态不可播放：${playInfo.status}${playInfo.failReason ? ` / ${playInfo.failReason}` : ''}`"
        />
      </div>
      <template #footer>
        <span v-if="playInfo" class="preview-meta">
          时长 {{ formatDuration(playInfo.duration) }} · 分片 {{ playInfo.segmentUrls?.length ?? 0 }} 个
        </span>
        <el-button @click="previewVisible = false">关闭</el-button>
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
.upload-bar {
  margin-bottom: 16px;
  padding: 16px;
}
.upload-stage {
  margin: 0 0 8px;
  font-size: 13px;
  color: #606266;
}
.upload-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #e6a23c;
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
.fail-reason {
  color: #f56c6c;
}
.preview-meta {
  margin-right: 12px;
  font-size: 13px;
  color: #909399;
}
</style>
