<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getVideoPlayInfo, getVideoProgress, reportVideoProgress } from '../api';
import type { VideoPlayInfo } from '../types';
import { isLoggedIn } from '../store';
import HlsPlayer from '../components/HlsPlayer.vue';

/**
 * 视频播放页：HLS 签名播放 + 断点续播。
 *
 * 播放：`GET /video/play/{videoId}` 返回签名 playlist 与每个分片的签名 URL，
 * 由 HlsPlayer 的自定义 loader 在 m3u8 解析前把相对路径换成签名 URL
 * （video-hls 是私有 bucket，不带签名直接取分片会 403）。
 *
 * 断点续播：进度按「用户 + 视频」维度存在服务端（Redis 高频写 + 定时落 MongoDB），
 * 因此必须登录才有续播；未登录时只播放、不上报。
 */

/** 距结尾不足这个秒数视为「已看完」，再进来从头播——否则一进去就播完，反而像坏了 */
const NEAR_END_SECONDS = 3;

/** 两次上报之间的最小位置差（秒）：暂停/播完会额外触发一次上报，用它挡掉重复请求 */
const MIN_REPORT_GAP_SECONDS = 1;

const route = useRoute();
const router = useRouter();

const inputId = ref('');
const loading = ref(false);
const playInfo = ref<VideoPlayInfo | null>(null);
const startPosition = ref(0);
/** 续播说明（展示「上次看到 xx 秒，已自动续播」） */
const resumeHint = ref('');
/** 最近一次已上报的位置，用于去掉暂停带来的重复上报 */
const lastReportedPosition = ref(-1);

const videoId = computed(() => String(route.params.videoId ?? ''));

/** 加载播放信息：先取续播位置再渲染播放器（播放器只在挂载时读一次 startPosition） */
async function load(): Promise<void> {
  playInfo.value = null;
  startPosition.value = 0;
  resumeHint.value = '';
  lastReportedPosition.value = -1;

  const id = videoId.value;
  if (!id) {
    return;
  }
  loading.value = true;
  try {
    const info = await getVideoPlayInfo(id);
    if (isLoggedIn.value && info.status === 'transcoded') {
      try {
        const progress = await getVideoProgress(id);
        const total = info.duration ?? 0;
        if (progress.position > 0 && total > 0 && total - progress.position > NEAR_END_SECONDS) {
          startPosition.value = progress.position;
          resumeHint.value = `上次播放到 ${progress.position.toFixed(1)}s，已自动续播`;
        }
      } catch {
        // 进度查询失败不影响播放本身
      }
    }
    playInfo.value = info;
  } catch (e) {
    ElMessage.error(`获取播放地址失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

/** 手动输入视频 ID 播放（演示用入口：评论区视频链路未接入，暂以 ID 直达） */
function onPlayInput(): void {
  const id = inputId.value.trim();
  if (!id) {
    ElMessage.warning('请输入视频 ID');
    return;
  }
  void router.push(`/video/${id}`);
}

/**
 * 播放进度回调（HlsPlayer 已按 10 秒节流，暂停/播完会补报一次）。
 *
 * @param position 当前播放位置（秒）
 * @param duration 视频总时长（秒）
 */
async function onProgress(position: number, duration: number): Promise<void> {
  if (!isLoggedIn.value || !videoId.value) {
    return;
  }
  if (lastReportedPosition.value >= 0 && Math.abs(position - lastReportedPosition.value) < MIN_REPORT_GAP_SECONDS) {
    return;
  }
  lastReportedPosition.value = position;
  try {
    await reportVideoProgress(videoId.value, position, duration);
  } catch {
    // 进度上报是尽力而为：失败不打断播放
  }
}

watch(() => route.params.videoId, load, { immediate: true });
</script>

<template>
  <div class="play-page">
    <div class="page-header">
      <h2>视频播放</h2>
      <span class="hint">HLS 签名播放 · 断点续播（进度按用户维度保存在服务端）</span>
    </div>

    <el-card shadow="never" class="id-bar">
      <el-input
        v-model="inputId"
        placeholder="输入视频 ID（19 位雪花 ID）"
        clearable
        style="width: 320px;"
        @keyup.enter="onPlayInput"
      />
      <el-button type="primary" style="margin-left: 8px;" @click="onPlayInput">播放</el-button>
      <el-tag v-if="!isLoggedIn" type="warning" style="margin-left: 12px;">
        未登录：可播放，但不记录播放进度
      </el-tag>
    </el-card>

    <el-card v-if="!videoId" shadow="never">
      <el-empty description="请输入视频 ID 后播放" />
    </el-card>

    <el-card v-else shadow="never" v-loading="loading">
      <el-alert v-if="resumeHint" type="success" :closable="false" :title="resumeHint" style="margin-bottom: 12px;" />

      <HlsPlayer
        v-if="playInfo?.playlistUrl"
        :playlist-url="playInfo.playlistUrl"
        :segment-urls="playInfo.segmentUrls"
        :start-position="startPosition"
        :poster="playInfo.thumbnailUrl"
        @progress="onProgress"
      />

      <el-alert
        v-else-if="playInfo && !loading"
        type="warning"
        :closable="false"
        :title="`当前状态不可播放：${playInfo.status}${playInfo.failReason ? ` / ${playInfo.failReason}` : ''}`"
      />

      <div v-if="playInfo" class="meta">
        <span>视频 ID：{{ playInfo.videoId }}</span>
        <span>时长：{{ playInfo.duration ? `${playInfo.duration.toFixed(1)}s` : '-' }}</span>
        <span>分片：{{ playInfo.segmentUrls?.length ?? 0 }} 个</span>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.play-page {
  padding: 16px 20px 32px;
}
.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.id-bar {
  margin-bottom: 16px;
  padding: 16px;
}
.meta {
  margin-top: 12px;
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #909399;
}
</style>
