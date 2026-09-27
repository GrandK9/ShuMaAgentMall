<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import Hls from 'hls.js';
import type { HlsConfig, LoaderCallbacks, LoaderConfiguration, LoaderContext } from 'hls.js';
import type { VideoSegmentUrl } from '../types';

/**
 * HLS 播放器。
 *
 * 为什么需要自定义 loader：
 * HLS bucket（video-hls）是私有的，服务端签发的只有 index.m3u8 的签名 URL，
 * 而 m3u8 内部写的是相对路径（segment_000.ts）。播放器直接请求这些相对路径会 403。
 * 因此服务端在 `GET /video/play/{videoId}` 里把**每个分片**也一并签了名（同效期），
 * 这里用 hls.js 的 playlist loader 在响应落地前把相对路径按出现顺序替换为签名 URL。
 *
 * 为什么改 response.data 而不是拦截分片请求：
 * m3u8 解析发生在 loader 之后，此时改写一次，hls.js 内部得到的就全是绝对签名 URL，
 * 后续的分片加载、码率切换、seek 重取都能自然复用，无需再改 fLoader。
 */
const props = defineProps<{
  /** 签名后的 m3u8 地址（1 小时有效） */
  playlistUrl: string;
  /** 各 .ts 分片的签名 URL；按 index 升序后与 m3u8 中分片出现顺序一一对应 */
  segmentUrls?: VideoSegmentUrl[];
  /** 续播起始位置（秒） */
  startPosition?: number;
  /** 首帧前的封面（缩略图签名 URL） */
  poster?: string;
}>();

const emit = defineEmits<{
  /** 播放进度（秒），用于断点续播上报 */
  progress: [position: number, duration: number];
}>();

const videoRef = ref<HTMLVideoElement | null>(null);
const errorMsg = ref('');

let hls: Hls | null = null;
/** 上次上报进度的时间戳，避免 timeupdate（约 4 次/秒）把上报打成高频写 */
let lastReportAt = 0;

/** 进度上报间隔（毫秒），与后端「每 10 秒上报一次」的约定一致 */
const REPORT_INTERVAL_MS = 10_000;

/**
 * 把 m3u8 中的相对分片路径替换为签名 URL。
 *
 * @param text   原始 m3u8 文本
 * @param signed 已按 index 升序排列的分片签名 URL
 * @returns 改写后的 m3u8 文本
 */
function rewritePlaylist(text: string, signed: string[]): string {
  let cursor = 0;
  return text
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim();
      // 空行与标签行（#EXTINF / #EXT-X-...）原样保留
      if (!trimmed || trimmed.startsWith('#')) {
        return line;
      }
      // 只替换 .ts 分片；当前切片产物是单码率点播，不含变体 .m3u8
      if (!/\.ts(\?|$)/i.test(trimmed)) {
        return line;
      }
      const url = signed[cursor];
      cursor += 1;
      return url ?? line;
    })
    .join('\n');
}

/** hls.js 期望的 playlist loader 构造器类型（`pLoader` 的取值类型） */
type PlaylistLoaderCtor = NonNullable<HlsConfig['pLoader']>;

/**
 * hls.js 的 `DefaultConfig.loader` 在类型定义里是「返回 Loader<LoaderContext> 的构造签名」，
 * 不带泛型参数，直接 `extends` 无法给子类指定 context 类型，故先断言成可继承的构造器类型。
 */
const BasePlaylistLoader = Hls.DefaultConfig.loader as unknown as new (config: HlsConfig) => {
  load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void;
};

/**
 * 生成「分片路径改写」playlist loader。
 *
 * @param signedUrls 分片签名 URL（按 index 升序）
 * @returns 可用于 Hls 配置 `pLoader` 的 loader 构造器
 */
function createSignedPlaylistLoader(signedUrls: string[]): PlaylistLoaderCtor {
  class SignedPlaylistLoader extends BasePlaylistLoader {
    load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void {
      const onSuccess = callbacks.onSuccess;
      // 包一层 onSuccess：m3u8 原文在交给 hls.js 解析前完成路径替换
      callbacks.onSuccess = (response, stats, ctx, networkDetails) => {
        if (typeof response.data === 'string') {
          response.data = rewritePlaylist(response.data, signedUrls);
        }
        onSuccess(response, stats, ctx, networkDetails);
      };
      super.load(context, config, callbacks);
    }
  }
  // 基类断言丢失了 Loader 的实例成员类型，回填成 hls.js 期望的构造器类型
  return SignedPlaylistLoader as unknown as PlaylistLoaderCtor;
}

/** 销毁 hls 实例并释放媒体资源 */
function destroy(): void {
  if (hls) {
    hls.destroy();
    hls = null;
  }
  const el = videoRef.value;
  if (el) {
    el.removeAttribute('src');
    el.load();
  }
}

/** 播放器就绪后跳到续播位置并开始播放 */
function seekAndPlay(): void {
  const el = videoRef.value;
  if (!el) {
    return;
  }
  const start = props.startPosition ?? 0;
  if (start > 0) {
    el.currentTime = start;
  }
  // 浏览器自动播放策略可能拒绝（无用户手势），静默忽略——控件已交给用户手动播
  void el.play().catch(() => undefined);
}

/** 装配 hls.js（或回退到浏览器原生 HLS）并开始加载 */
function setup(): void {
  destroy();
  errorMsg.value = '';
  lastReportAt = 0;

  const el = videoRef.value;
  if (!el || !props.playlistUrl) {
    return;
  }

  const signed = (props.segmentUrls ?? [])
    .slice()
    .sort((a, b) => a.index - b.index)
    .map((s) => s.url);

  if (Hls.isSupported()) {
    hls = new Hls({ pLoader: createSignedPlaylistLoader(signed) });
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        errorMsg.value = `播放失败：${data.type} / ${data.details}`;
      }
    });
    hls.on(Hls.Events.MANIFEST_PARSED, seekAndPlay);
    hls.loadSource(props.playlistUrl);
    hls.attachMedia(el);
    return;
  }

  if (el.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari 原生播放 HLS 时无法改写分片 URL，私有 bucket 下的相对路径会 403
    el.src = props.playlistUrl;
    errorMsg.value = '当前浏览器使用原生 HLS 播放，无法为分片附加签名，请改用 Chrome / Edge';
    return;
  }

  errorMsg.value = '当前浏览器不支持 HLS（MediaSource 不可用）';
}

/** timeupdate 高频触发，按固定间隔节流后对外抛进度 */
function onTimeUpdate(): void {
  const el = videoRef.value;
  if (!el) {
    return;
  }
  const now = Date.now();
  if (now - lastReportAt < REPORT_INTERVAL_MS) {
    return;
  }
  lastReportAt = now;
  emit('progress', el.currentTime, el.duration || 0);
}

/** 暂停/播完时立即上报一次，避免最后一次进度丢失 */
function onPauseOrEnded(): void {
  const el = videoRef.value;
  if (el) {
    emit('progress', el.currentTime, el.duration || 0);
  }
}

onMounted(setup);
onBeforeUnmount(destroy);
watch(() => [props.playlistUrl, props.segmentUrls], setup);
</script>

<template>
  <div class="hls-player">
    <video
      ref="videoRef"
      :poster="poster"
      controls
      playsinline
      class="hls-video"
      @timeupdate="onTimeUpdate"
      @pause="onPauseOrEnded"
      @ended="onPauseOrEnded"
    ></video>
    <p v-if="errorMsg" class="hls-error">{{ errorMsg }}</p>
  </div>
</template>

<style scoped>
.hls-player {
  width: 100%;
}
.hls-video {
  width: 100%;
  max-height: 480px;
  background: #000;
  border-radius: 4px;
}
.hls-error {
  margin: 8px 0 0;
  color: #f56c6c;
  font-size: 13px;
}
</style>
