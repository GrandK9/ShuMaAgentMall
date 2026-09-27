<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  addCart,
  createComment,
  deleteComment,
  getProductDetail,
  getReplies,
  getRootComments,
  getVideoPlayInfo,
  likeComment,
  replyComment,
  reportComment,
  uploadCommentVideo,
} from '../api';
import { isLoggedIn, store } from '../store';
import type { CartItem, Comment, Product, VideoPlayInfo } from '../types';
import HlsPlayer from '../components/HlsPlayer.vue';

const route = useRoute();
const router = useRouter();
const product = ref<Product | null>(null);
const loading = ref(false);
/** 选中的 SKU 主键；element-plus 的 radio 只接受 String|Number|Boolean，所以绑 id 而不是整个对象 */
const selectedSkuId = ref<number | null>(null);
const selectedSku = computed(
  () => product.value?.skuList?.find((s) => s.id === selectedSkuId.value) ?? null,
);
const quantity = ref(1);

const maxStock = computed(() => selectedSku.value?.stock ?? 99);

/* ---------------- 评论区 ---------------- */

const comments = ref<Comment[]>([]);
const commentTotal = ref(0);
const commentPage = ref(1);
const commentSize = 10;
const commentSort = ref<'latest' | 'hot'>('latest');
const commentLoading = ref(false);
/** 新评论内容 */
const newComment = ref('');
/** 新评论评分（默认 5） */
const newRating = ref(5);
/** 已展开回复的根评论：rootId → 回复列表 */
const expandedReplies = ref<Record<string, Comment[]>>({});
/** 回复输入框状态（rootId + 被回复对象信息；ID 均为字符串雪花 ID） */
const replyBox = ref<{ rootId: string; parentId?: string; replyToUserId?: number; replyToUsername?: string } | null>(null);
const replyContent = ref('');

/* ---------------- 评论附带视频（评论区小文件：≤100MB / ≤60s） ---------------- */

/** 与后端 VideoConstants 的评论区档位保持一致，超限在浏览器侧直接拒绝 */
const VIDEO_MAX_SIZE = 100 * 1024 * 1024;
const VIDEO_MAX_DURATION = 60;
const VIDEO_MAX_LONG_SIDE = 4096;
const VIDEO_MAX_SHORT_SIDE = 2160;

const videoInput = ref<HTMLInputElement | null>(null);
/** 已上传、待随评论提交的视频（发表评论时带上它的 videoId） */
const commentVideo = ref<{ videoId: string; name: string; size: number; duration: number } | null>(null);
const videoUploading = ref(false);
/** 视频上传阶段文案（预检 / 上传+切片） */
const videoStage = ref('');
/** 评论视频的播放信息缓存：videoId → 播放信息（懒加载，点「查看视频」才取签名 URL） */
const commentPlayInfo = ref<Record<string, VideoPlayInfo>>({});
/** 正在加载播放信息的 videoId */
const videoLoadingId = ref('');

/** 从 JWT 解析当前用户 ID（claims: user_id），用于"删除自己的评论"按钮显示 */
function currentUserId(): number | null {
  const token = localStorage.getItem('agent_token');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const id = payload.user_id ?? payload.userId ?? payload.uid;
    return typeof id === 'number' ? id : null;
  } catch {
    return null;
  }
}

function requireLogin(): boolean {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录');
    return false;
  }
  return true;
}

/** 字节数 → 可读体积 */
function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${bytes} B`;
}

/**
 * 浏览器端预检：用原生 video 元素探测时长/分辨率。
 *
 * 只做「大小 / 时长 / 分辨率 / 可解码性」四项，**不判定编码白名单**——
 * 浏览器能解 VP9/AV1，而服务端只放行 H.264/H.265，编码是否合规以服务端 ffprobe 为准。
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

/** 评论区视频预检：超限在浏览器侧拒绝，省掉一次上传 */
async function precheckCommentVideo(file: File): Promise<{ duration: number; width: number; height: number }> {
  if (file.size > VIDEO_MAX_SIZE) {
    throw new Error(`文件 ${formatSize(file.size)} 超过评论区上限 ${formatSize(VIDEO_MAX_SIZE)}`);
  }
  const info = await probeLocalFile(file);
  if (!Number.isFinite(info.duration) || info.duration <= 0) {
    throw new Error('无法读取视频时长，文件可能不是有效视频');
  }
  if (info.duration > VIDEO_MAX_DURATION) {
    throw new Error(`时长 ${info.duration.toFixed(1)}s 超过评论区上限 ${VIDEO_MAX_DURATION}s`);
  }
  if (Math.max(info.width, info.height) > VIDEO_MAX_LONG_SIDE || Math.min(info.width, info.height) > VIDEO_MAX_SHORT_SIDE) {
    throw new Error(`分辨率 ${info.width}×${info.height} 超限（长边 ≤ ${VIDEO_MAX_LONG_SIDE}、短边 ≤ ${VIDEO_MAX_SHORT_SIDE}）`);
  }
  return info;
}

/** 选择视频文件 → 预检 → 直传（服务端同步 ffprobe 校验 + HLS 切片） */
async function onCommentVideoPicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 立即清空 input，否则连续选同一个文件不会再触发 change
  input.value = '';
  if (!file || !requireLogin()) {
    return;
  }

  videoUploading.value = true;
  commentVideo.value = null;
  try {
    videoStage.value = '浏览器端预检（大小 / 时长 / 分辨率）…';
    const info = await precheckCommentVideo(file);
    videoStage.value = `预检通过（${info.width}×${info.height} / ${info.duration.toFixed(1)}s），上传并切片中…`;

    const resp = await uploadCommentVideo(file);
    if (resp.status !== 'transcoded') {
      throw new Error(resp.failReason || `服务端处理未通过（状态 ${resp.status}）`);
    }
    commentVideo.value = {
      videoId: resp.videoId,
      name: file.name,
      size: file.size,
      duration: info.duration,
    };
    ElMessage.success(`视频已就绪（${info.duration.toFixed(1)}s / ${formatSize(file.size)}），发表评论时一并提交`);
  } catch (e) {
    ElMessage.error(`视频上传失败：${(e as Error).message}`);
  } finally {
    videoUploading.value = false;
    videoStage.value = '';
  }
}

/** 移除待提交的视频（已上传到 video 服务，仅取消本次评论的引用） */
function removeCommentVideo(): void {
  commentVideo.value = null;
}

/**
 * 展开/收起评论视频：首次展开懒加载播放签名 URL。
 *
 * 签名 URL 有效期 1 小时，故不提前批量取，点开才取。
 */
async function toggleCommentVideo(c: Comment): Promise<void> {
  const id = c.videoId;
  if (!id) return;
  if (commentPlayInfo.value[id]) {
    delete commentPlayInfo.value[id];
    return;
  }
  videoLoadingId.value = id;
  try {
    commentPlayInfo.value[id] = await getVideoPlayInfo(id);
  } catch (e) {
    ElMessage.error(`获取视频播放地址失败：${(e as Error).message}`);
  } finally {
    videoLoadingId.value = '';
  }
}

/** 加载根评论分页 */
async function loadComments(pageNo = 1): Promise<void> {
  if (!product.value) return;
  commentLoading.value = true;
  try {
    const res = await getRootComments(product.value.id, pageNo, commentSize, commentSort.value);
    comments.value = res.records;
    commentTotal.value = res.total;
    commentPage.value = pageNo;
  } catch (e) {
    ElMessage.error(`评论加载失败：${(e as Error).message}`);
  } finally {
    commentLoading.value = false;
  }
}

/** 发表评论 */
async function submitComment(): Promise<void> {
  if (!product.value) return;
  if (!requireLogin()) return;
  const content = newComment.value.trim();
  if (!content) {
    ElMessage.warning('请输入评论内容');
    return;
  }
  try {
    await createComment({
      productId: product.value.id,
      content,
      rating: newRating.value,
      videoId: commentVideo.value?.videoId,
    });
    newComment.value = '';
    newRating.value = 5;
    commentVideo.value = null;
    ElMessage.success('评论成功');
    await loadComments(1);
  } catch (e) {
    ElMessage.error(`评论失败：${(e as Error).message}`);
  }
}

/** 点赞（幂等），刷新点赞数 */
async function onLike(c: Comment): Promise<void> {
  if (!requireLogin()) return;
  try {
    const count = await likeComment(c.id);
    c.likeCount = count;
  } catch (e) {
    ElMessage.error(`点赞失败：${(e as Error).message}`);
  }
}

/** 举报评论 */
async function onReport(c: Comment): Promise<void> {
  if (!requireLogin()) return;
  try {
    await ElMessageBox.confirm('确认举报该评论？举报数达到阈值将自动隐藏。', '举报评论', {
      confirmButtonText: '举报',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await reportComment(c.id);
    ElMessage.success('举报成功，我们会尽快处理');
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(`举报失败：${(e as Error).message}`);
    }
  }
}

/** 删除自己的评论 */
async function onDeleteComment(c: Comment): Promise<void> {
  if (!requireLogin()) return;
  try {
    await ElMessageBox.confirm('确认删除该评论？', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteComment(c.id);
    ElMessage.success('评论已删除');
    await loadComments(commentPage.value);
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(`删除失败：${(e as Error).message}`);
    }
  }
}

/** 展开/收起某根评论的回复（首次展开懒加载） */
async function toggleReplies(c: Comment): Promise<void> {
  if (expandedReplies.value[c.id]) {
    delete expandedReplies.value[c.id];
    return;
  }
  try {
    const res = await getReplies(c.id, 1, 20);
    expandedReplies.value[c.id] = res.records;
  } catch (e) {
    ElMessage.error(`回复加载失败：${(e as Error).message}`);
  }
}

/** 打开回复输入框 */
function openReplyBox(root: Comment, replyTo?: Comment): void {
  if (!requireLogin()) return;
  replyBox.value = {
    rootId: root.id,
    parentId: replyTo?.id ?? root.id,
    replyToUserId: replyTo?.userId,
    replyToUsername: replyTo?.username,
  };
  replyContent.value = '';
}

/** 提交回复 */
async function submitReply(root: Comment): Promise<void> {
  if (!replyBox.value) return;
  const content = replyContent.value.trim();
  if (!content) {
    ElMessage.warning('请输入回复内容');
    return;
  }
  try {
    await replyComment(root.id, { ...replyBox.value, content });
    replyBox.value = null;
    replyContent.value = '';
    ElMessage.success('回复成功');
    root.replyCount = (root.replyCount ?? 0) + 1;
    await toggleReplies(root);
  } catch (e) {
    ElMessage.error(`回复失败：${(e as Error).message}`);
  }
}

/** 时间格式化：ISO → yyyy-MM-dd HH:mm */
function formatTime(t?: string): string {
  if (!t) return '';
  const d = new Date(t);
  if (Number.isNaN(d.getTime())) return t;
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function load(id: number): Promise<void> {
  loading.value = true;
  try {
    const p = await getProductDetail(id);
    product.value = p;
    const initial = p.skuList?.find((s) => (s.stock ?? 0) > 0) ?? p.skuList?.[0];
    selectedSkuId.value = initial?.id ?? null;
    quantity.value = 1;
  } catch (e) {
    ElMessage.error(`商品加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function goBack(): void {
  router.back();
}

async function onAddCart(): Promise<void> {
  if (!product.value || !selectedSku.value) return;
  try {
    await addCart({ skuId: selectedSku.value.id, productId: product.value.id, quantity: quantity.value });
    ElMessage.success('已加入购物车');
  } catch (e) {
    ElMessage.error(`加购失败：${(e as Error).message}`);
  }
}

function onBuyNow(): void {
  if (!product.value || !selectedSku.value) return;
  const sku = selectedSku.value;
  const item: CartItem = {
    id: 0, // 立即购买：非购物车项，id=0，结算时走 skuId + quantity
    skuId: sku.id,
    productId: product.value.id,
    productName: product.value.name,
    productImage: product.value.mainImage,
    skuSpecs: sku.specs,
    price: sku.price,
    quantity: quantity.value,
    subtotal: sku.price * quantity.value,
  };
  store.checkoutItems = [item];
  router.push('/checkout');
}

onMounted(() => {
  const id = Number(route.params.id);
  if (!Number.isNaN(id)) {
    void load(id).then(() => {
      void loadComments(1);
    });
  }
});
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-page-header @back="goBack" content="商品详情" style="margin-bottom: 16px;" />
    <div v-if="product" class="detail-body">
      <div class="gallery">
        <img v-if="product.mainImage" :src="product.mainImage" :alt="product.name" />
        <div v-else class="img-placeholder">暂无图片</div>
      </div>
      <div class="info">
        <h2>{{ product.name }}</h2>
        <p class="subtitle">{{ product.subtitle || '暂无简介' }}</p>
        <div class="price">¥{{ product.price.toFixed(2) }}</div>
        <div class="sales">销量：{{ product.salesVolume ?? 0 }}</div>

        <div class="sku-row">
          <span class="label">规格：</span>
          <el-radio-group v-model="selectedSkuId">
            <el-radio v-for="sku in product.skuList" :key="sku.id" :value="sku.id" :disabled="(sku.stock ?? 0) <= 0">
              {{ sku.skuCode }} {{ sku.specs ? `（${sku.specs}）` : '' }}
            </el-radio>
          </el-radio-group>
        </div>

        <div class="qty-row">
          <span class="label">数量：</span>
          <el-input-number v-model="quantity" :min="1" :max="maxStock" />
          <span class="stock">库存 {{ selectedSku?.stock ?? 0 }}</span>
        </div>

        <div class="actions">
          <el-button size="large" @click="onAddCart">加入购物车</el-button>
          <el-button size="large" type="primary" @click="onBuyNow">立即购买</el-button>
        </div>
      </div>
    </div>

    <!-- 评论区 -->
    <section v-if="product" class="comment-section">
      <div class="comment-head">
        <span class="comment-title">用户评论</span>
        <el-radio-group v-model="commentSort" size="small" @change="loadComments(1)">
          <el-radio-button value="latest">最新</el-radio-button>
          <el-radio-button value="hot">最热</el-radio-button>
        </el-radio-group>
      </div>

      <div class="comment-input">
        <el-rate v-model="newRating" />
        <el-input v-model="newComment" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="分享你的使用体验（评分 + 内容）" />

        <!-- 附带视频（可选）：评论小文件直传，前端预检 ≤100MB / ≤60s / 分辨率上限 -->
        <input ref="videoInput" type="file" accept="video/*" style="display: none;" @change="onCommentVideoPicked" />
        <div class="video-row">
          <el-button size="small" :loading="videoUploading" @click="videoInput?.click()">
            {{ commentVideo ? '重新选择视频' : '添加视频（可选）' }}
          </el-button>
          <el-tag v-if="commentVideo" type="success" closable @close="removeCommentVideo">
            {{ commentVideo.name }} · {{ commentVideo.duration.toFixed(1) }}s · {{ formatSize(commentVideo.size) }}
          </el-tag>
          <span v-if="videoStage" class="video-stage">{{ videoStage }}</span>
          <span v-else-if="!commentVideo" class="video-hint">支持 H.264 MP4，≤100MB 且 ≤60 秒</span>
        </div>

        <div class="comment-input-actions">
          <el-button type="primary" @click="submitComment">发表评论</el-button>
        </div>
      </div>

      <div class="comment-list" v-loading="commentLoading">
        <el-empty v-if="!commentLoading && comments.length === 0" description="暂无评论，快来抢沙发" />
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-top">
            <span class="comment-user">{{ c.username || `用户${c.userId ?? ''}` }}</span>
            <el-rate v-if="c.rating" :model-value="c.rating" disabled size="small" />
            <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
          </div>
          <div class="comment-content">{{ c.content }}</div>

          <!-- 评论附带视频：懒加载签名 URL，展开即播放 -->
          <div v-if="c.videoId" class="comment-video">
            <el-button link type="primary" size="small" @click="toggleCommentVideo(c)">
              {{ commentPlayInfo[c.videoId] ? '收起视频' : '查看视频' }}
            </el-button>
            <span v-if="videoLoadingId === c.videoId" class="video-stage">加载播放地址…</span>
            <HlsPlayer
              v-if="commentPlayInfo[c.videoId]?.playlistUrl"
              class="comment-video-player"
              :playlist-url="commentPlayInfo[c.videoId].playlistUrl!"
              :segment-urls="commentPlayInfo[c.videoId].segmentUrls"
              :poster="commentPlayInfo[c.videoId].thumbnailUrl"
            />
            <span
              v-else-if="commentPlayInfo[c.videoId] && !commentPlayInfo[c.videoId].playlistUrl"
              class="video-stage"
            >该视频当前不可播放（{{ commentPlayInfo[c.videoId].status }}）</span>
          </div>

          <div class="comment-actions">
            <span class="action-btn" @click="onLike(c)">赞 {{ c.likeCount ?? 0 }}</span>
            <span class="action-btn" @click="openReplyBox(c)">回复 {{ c.replyCount ?? 0 }}</span>
            <span v-if="c.userId === currentUserId()" class="action-btn danger" @click="onDeleteComment(c)">删除</span>
            <span class="action-btn" @click="onReport(c)">举报</span>
          </div>

          <!-- 回复输入框 -->
          <div v-if="replyBox && replyBox.rootId === c.id" class="reply-box">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              :placeholder="replyBox.replyToUsername ? `回复 @${replyBox.replyToUsername}` : '回复该评论'"
            />
            <div class="reply-box-actions">
              <el-button size="small" @click="replyBox = null; replyContent = ''">取消</el-button>
              <el-button size="small" type="primary" @click="submitReply(c)">提交回复</el-button>
            </div>
          </div>

          <!-- 回复列表（懒加载） -->
          <div v-if="expandedReplies[c.id] && expandedReplies[c.id].length" class="reply-list">
            <div v-for="r in expandedReplies[c.id]" :key="r.id" class="reply-item">
              <span class="reply-user">{{ r.username || `用户${r.userId ?? ''}` }}</span>
              <template v-if="r.replyToUsername">
                <span class="reply-arrow">回复</span>
                <span class="reply-user">{{ r.replyToUsername }}</span>
              </template>
              <span class="reply-content">{{ r.content }}</span>
              <span class="action-btn" @click="openReplyBox(c, r)">回复</span>
            </div>
          </div>

          <span v-if="(c.replyCount ?? 0) > 0" class="toggle-replies" @click="toggleReplies(c)">
            {{ expandedReplies[c.id] ? '收起回复' : `展开 ${c.replyCount} 条回复` }}
          </span>
        </div>

        <div v-if="commentTotal > commentSize" class="comment-pagination">
          <el-pagination
            v-model:current-page="commentPage"
            :page-size="commentSize"
            :total="commentTotal"
            layout="prev, pager, next"
            @current-change="loadComments"
          />
        </div>
      </div>
    </section>

    <el-empty v-if="!product && !loading" description="商品不存在" />
  </div>
</template>

<style scoped>
.detail-page {
  padding: 16px 24px;
}
.detail-body {
  display: flex;
  gap: 24px;
  background: #fff;
  padding: 24px;
  border-radius: 8px;
}
.gallery {
  width: 360px;
  height: 360px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 8px;
}
.gallery img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.img-placeholder {
  color: #909399;
}
.info {
  flex: 1;
}
.info h2 {
  margin: 0 0 8px;
}
.subtitle {
  color: #606266;
  margin: 0 0 16px;
}
.price {
  color: #f56c6c;
  font-size: 28px;
  font-weight: bold;
}
.sales {
  color: #909399;
  font-size: 14px;
  margin: 8px 0 20px;
}
.sku-row,
.qty-row {
  margin-bottom: 16px;
}
.label {
  color: #606266;
  margin-right: 8px;
}
.stock {
  margin-left: 12px;
  color: #909399;
  font-size: 14px;
}
.actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}

/* ---------------- 评论区 ---------------- */
.comment-section {
  margin-top: 16px;
  background: #fff;
  border-radius: 8px;
  padding: 20px 24px;
}
.comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.comment-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}
.comment-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}
.comment-input-actions {
  display: flex;
  justify-content: flex-end;
}
.video-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.video-hint,
.video-stage {
  color: #909399;
  font-size: 12px;
}
.comment-video {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin: 4px 0 8px;
}
.comment-video-player {
  max-width: 420px;
}
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 80px;
}
.comment-item {
  border-bottom: 1px solid #f0f2f5;
  padding-bottom: 14px;
}
.comment-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.comment-user {
  color: #409eff;
  font-size: 13px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.comment-time {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}
.comment-content {
  color: #303133;
  font-size: 14px;
  margin: 8px 0;
  line-height: 1.6;
  word-break: break-word;
}
.comment-actions {
  display: flex;
  gap: 16px;
}
.action-btn {
  color: #606266;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}
.action-btn:hover {
  color: #409eff;
}
.action-btn.danger:hover {
  color: #f56c6c;
}
.reply-box {
  margin-top: 10px;
  padding: 10px;
  background: #f8f9fb;
  border-radius: 6px;
}
.reply-box-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.reply-list {
  margin-top: 10px;
  background: #f8f9fb;
  border-radius: 6px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.reply-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}
.reply-user {
  color: #409eff;
  font-size: 13px;
  flex-shrink: 0;
}
.reply-arrow {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}
.reply-content {
  flex: 1;
  color: #303133;
  font-size: 13px;
  word-break: break-word;
}
.toggle-replies {
  display: inline-block;
  margin-top: 8px;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
}
.comment-pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
