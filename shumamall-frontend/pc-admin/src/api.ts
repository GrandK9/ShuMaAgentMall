import type {
  ApiResp,
  AuditLog,
  Brand,
  Category,
  CommentVO,
  Dashboard,
  LoginResp,
  MenuItem,
  Order,
  PageResult,
  PaymentRecord,
  Permission,
  Product,
  Role,
  SearchIndexStatus,
  VideoCompleteResp,
  VideoInitResp,
  VideoMeta,
  VideoPlayInfo,
  VideoUploadSession,
} from './types';

import { buildSignHeaders, isSignedRequest } from './sign';

const AUTH_BASE = '/api/v1/auth';
const DASHBOARD_BASE = '/api/v1/admin/dashboard';
const PRODUCT_BASE = '/api/v1/admin/product';
const CATEGORY_BASE = '/api/v1/admin/category';
const CATEGORY_TREE_BASE = '/api/v1/categories';
const BRAND_BASE = '/api/v1/admin/brand';
const ORDER_BASE = '/api/v1/admin/orders';
const PAYMENT_BASE = '/api/v1/admin/payment';
const PERMISSION_BASE = '/api/v1/admin/permission';
const ROLE_BASE = '/api/v1/admin/role';
const MENU_BASE = '/permission/menus';
const MY_PERMISSION_BASE = '/permission/my-permissions';
const AUDIT_BASE = '/api/v1/admin/audit';
const COMMENT_BASE = '/api/v1/admin/comment';
const SEARCH_INDEX_BASE = '/api/v1/search/index';
const ADMIN_VIDEO_BASE = '/api/v1/admin/video';
const VIDEO_BASE = '/api/v1/video';

/** 从 localStorage 读取管理端 token */
function adminToken(): string {
  return localStorage.getItem('admin_token') || '';
}

/** 从 localStorage 读取签名密钥（登录时由 auth 服务下发，与 token 同生命周期） */
function adminSignSecret(): string {
  return localStorage.getItem('admin_sign_secret') || '';
}

/** 统一请求：自动附带 Authorization 头（改状态/退款额外附带 X-Sign 签名头），并解包 R<T> */
async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    ...(init.headers as Record<string, string> | undefined),
  };
  const token = adminToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  // 改订单状态、退款会直接动钱/改状态，除 token 外还要带请求签名，
  // 用于证明"这次请求的内容没被改过、也没被重放"（后端 RequestSignFilter 校验）。
  const method = (init.method || 'GET').toUpperCase();
  const rawBody = typeof init.body === 'string' ? init.body : '';
  if (isSignedRequest(method, url)) {
    const secret = adminSignSecret();
    if (!secret) {
      throw new Error('登录态缺少签名密钥，请退出后重新登录');
    }
    Object.assign(headers, await buildSignHeaders(method, url, rawBody, secret));
  }
  const res = await fetch(url, { ...init, headers });
  // 先解析响应体再判断状态：后端所有失败响应（含 Filter 层的 401/403）都返回统一的 R 结构，
  // 只有先读 body 才能拿到真实失败原因（如「无效的认证令牌」）。
  // 解析失败（如网关/代理直接返回 HTML）时回退到 HTTP 状态文本。
  const body = (await res.json().catch(() => null)) as ApiResp<T> | null;
  if (body) {
    if (body.code !== 2000) {
      throw new Error(body.msg || `HTTP ${res.status} ${res.statusText}`);
    }
    return body.data;
  }
  throw new Error(`HTTP ${res.status} ${res.statusText}`);
}

/* ---------------- 登录（auth 8081） ---------------- */

export function login(username: string, password: string): Promise<LoginResp> {
  return request<LoginResp>(`${AUTH_BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ username, password }),
  });
}

/* ---------------- 仪表盘（admin 8090） ---------------- */

export function getDashboard(): Promise<Dashboard> {
  return request<Dashboard>(DASHBOARD_BASE);
}

/* ---------------- 商品（product 8083） ---------------- */

export function getProductPage(query: { page?: number; size?: number; keyword?: string; status?: number } = {}): Promise<PageResult<Product>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 50, ...query }).forEach(([k, v]) => {
    if (v === undefined || v === null || (typeof v === 'string' && v === '')) return;
    params.set(k, String(v));
  });
  return request<PageResult<Product>>(`${PRODUCT_BASE}?${params.toString()}`);
}

export function getProductDetail(id: number): Promise<Product> {
  return request<Product>(`${PRODUCT_BASE}/${id}`);
}

export function createProduct(dto: Partial<Product>): Promise<number> {
  return request<number>(PRODUCT_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function updateProduct(id: number, dto: Partial<Product>): Promise<void> {
  return request<void>(`${PRODUCT_BASE}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function deleteProduct(id: number): Promise<void> {
  return request<void>(`${PRODUCT_BASE}/${id}`, { method: 'DELETE' });
}

export function updateProductStatus(id: number, status: number): Promise<void> {
  return request<void>(`${PRODUCT_BASE}/${id}/status?status=${status}`, { method: 'PUT' });
}

export function updateProductStock(id: number, stock: number): Promise<void> {
  return request<void>(`${PRODUCT_BASE}/${id}/stock?stock=${stock}`, { method: 'PUT' });
}

/* ---------------- 分类（product 8083） ---------------- */

export function getCategoryTree(): Promise<Category[]> {
  // 后端公开接口是 /api/v1/categories/tree，漏掉 /tree 会命中 404 被兜底成 5000，
  // 表现为分类页恒显示「暂无分类」
  return request<Category[]>(`${CATEGORY_TREE_BASE}/tree`);
}

export function createCategory(dto: Partial<Category>): Promise<number> {
  return request<number>(CATEGORY_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function updateCategory(id: number, dto: Partial<Category>): Promise<void> {
  return request<void>(`${CATEGORY_BASE}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function deleteCategory(id: number): Promise<void> {
  return request<void>(`${CATEGORY_BASE}/${id}`, { method: 'DELETE' });
}

/* ---------------- 品牌（product 8083） ---------------- */

export function getBrandPage(query: { page?: number; size?: number; keyword?: string } = {}): Promise<PageResult<Brand>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 50, ...query }).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  });
  return request<PageResult<Brand>>(`${BRAND_BASE}?${params.toString()}`);
}

export function createBrand(dto: Partial<Brand>): Promise<number> {
  return request<number>(BRAND_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function updateBrand(dto: Partial<Brand>): Promise<void> {
  // 后端只有 PUT /api/v1/admin/brand/{id}（BrandAdminController#update 用 @PathVariable 取 id），
  // 原来打到基路径会命中 405，品牌编辑保存不了
  return request<void>(`${BRAND_BASE}/${dto.id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function deleteBrand(id: number): Promise<void> {
  return request<void>(`${BRAND_BASE}/${id}`, { method: 'DELETE' });
}

/* ---------------- 订单（order 8084） ---------------- */

export function getOrderPage(query: { page?: number; size?: number; status?: number; userId?: number } = {}): Promise<PageResult<Order>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 20, ...query }).forEach(([k, v]) => {
    if (v === undefined || v === null || (typeof v === 'string' && v === '')) return;
    params.set(k, String(v));
  });
  return request<PageResult<Order>>(`${ORDER_BASE}?${params.toString()}`);
}

export function getOrderDetail(id: number): Promise<Order> {
  return request<Order>(`${ORDER_BASE}/${id}`);
}

export function updateOrderStatus(id: number, status: number, remark?: string): Promise<void> {
  const params = new URLSearchParams({ status: String(status) });
  if (remark) params.set('remark', remark);
  return request<void>(`${ORDER_BASE}/${id}/status?${params.toString()}`, { method: 'PUT' });
}

/* ---------------- 支付记录（payment 8085） ---------------- */

export function getPaymentPage(query: { page?: number; size?: number; orderNo?: string } = {}): Promise<PageResult<PaymentRecord>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 20, ...query }).forEach(([k, v]) => {
    if (v === undefined || v === null || (typeof v === 'string' && v === '')) return;
    params.set(k, String(v));
  });
  return request<PageResult<PaymentRecord>>(`${PAYMENT_BASE}?${params.toString()}`);
}

export function refundPayment(id: number): Promise<void> {
  return request<void>(`${PAYMENT_BASE}/${id}/refund`, { method: 'POST' });
}

/* ---------------- 角色（permission 8086） ---------------- */

export function getRolePage(query: { page?: number; size?: number } = {}): Promise<PageResult<Role>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 20, ...query }).forEach(([k, v]) => {
    if (v === undefined || v === null || (typeof v === 'string' && v === '')) return;
    params.set(k, String(v));
  });
  return request<PageResult<Role>>(`${ROLE_BASE}?${params.toString()}`);
}

export function createRole(dto: Partial<Role>): Promise<number> {
  return request<number>(ROLE_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function updateRole(id: number, dto: Partial<Role>): Promise<void> {
  return request<void>(`${ROLE_BASE}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function deleteRole(id: number): Promise<void> {
  return request<void>(`${ROLE_BASE}/${id}`, { method: 'DELETE' });
}

export function getRolePermissions(roleId: number): Promise<number[]> {
  return request<number[]>(`${ROLE_BASE}/${roleId}/permissions`);
}

/* ---------------- 权限点（permission 8086） ---------------- */

export function getPermissionList(): Promise<Permission[]> {
  return request<Permission[]>(PERMISSION_BASE);
}

export function createPermission(dto: Partial<Permission>): Promise<number> {
  return request<number>(PERMISSION_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function updatePermission(id: number, dto: Partial<Permission>): Promise<void> {
  return request<void>(`${PERMISSION_BASE}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function deletePermission(id: number): Promise<void> {
  return request<void>(`${PERMISSION_BASE}/${id}`, { method: 'DELETE' });
}

/* ---------------- 菜单（permission 8086） ---------------- */

export function getMenuTree(): Promise<MenuItem[]> {
  return request<MenuItem[]>(MENU_BASE);
}

/**
 * 当前登录管理员的全部权限编码（含 type=1 按钮权限）。
 * 与菜单树接口的区别：菜单树只返回 type=0 的菜单权限点，本接口返回全部编码，
 * 供 `v-permission` 指令判断按钮是否渲染。
 */
export function getMyPermissions(): Promise<string[]> {
  return request<string[]>(MY_PERMISSION_BASE);
}

/* ---------------- 审计日志（permission 8086） ---------------- */

/**
 * 审计日志组合查询（需 audit:view 权限）。
 * 所有筛选条件为空即不参与过滤；后端对 size 有硬上限 100。
 */
export function queryAuditLogs(query: {
  page?: number;
  size?: number;
  adminUserId?: number;
  action?: string;
  result?: string;
  resource?: string;
  startTime?: string;
  endTime?: string;
} = {}): Promise<PageResult<AuditLog>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 20, ...query }).forEach(([k, v]) => {
    if (v === undefined || v === null || (typeof v === 'string' && v === '')) return;
    params.set(k, String(v));
  });
  return request<PageResult<AuditLog>>(`${AUDIT_BASE}/logs?${params.toString()}`);
}

/* ---------------- 评论（comment 8089） ---------------- */

/** 评论分页查询（可按商品 / 状态过滤）；status：0-正常 1-作者删除 2-管理员隐藏 */
export function getCommentPage(query: { page?: number; size?: number; productId?: number; status?: number } = {}): Promise<PageResult<CommentVO>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 10, ...query }).forEach(([k, v]) => {
    if (v === undefined || v === null || (typeof v === 'string' && v === '')) return;
    params.set(k, String(v));
  });
  return request<PageResult<CommentVO>>(`${COMMENT_BASE}?${params.toString()}`);
}

/** 隐藏评论（内容违规）；commentId 为字符串雪花 ID */
export function hideComment(commentId: string): Promise<void> {
  return request<void>(`${COMMENT_BASE}/${commentId}/hide`, { method: 'PUT' });
}

/** 恢复评论；commentId 为字符串雪花 ID */
export function restoreComment(commentId: string): Promise<void> {
  return request<void>(`${COMMENT_BASE}/${commentId}/restore`, { method: 'PUT' });
}

/* ---------------- 搜索索引（search 8087） ---------------- */

/** 索引状态（是否存在 + 文档数） */
export function getSearchIndexStatus(): Promise<SearchIndexStatus> {
  return request<SearchIndexStatus>(`${SEARCH_INDEX_BASE}/status`);
}

/** 全量重建商品索引，返回实际写入的商品数量 */
export function rebuildSearchIndex(): Promise<number> {
  return request<number>(`${SEARCH_INDEX_BASE}/rebuild`, { method: 'POST' });
}

/* ---------------- 视频（video 8092） ---------------- */

/** 视频列表（管理端，需 video:view） */
export function getVideoPage(
  query: { page?: number; size?: number; status?: string; uploaderType?: string } = {},
): Promise<PageResult<VideoMeta>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 20, ...query }).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    params.set(k, String(v));
  });
  return request<PageResult<VideoMeta>>(`${ADMIN_VIDEO_BASE}?${params.toString()}`);
}

/**
 * 删除视频（清理 MinIO 原始文件 + HLS 分片 + 缩略图 + MongoDB 元数据）。
 *
 * @param videoId 雪花 ID，必须按字符串透传（19 位，转 number 会丢精度）
 */
export function deleteVideo(videoId: string): Promise<void> {
  return request<void>(`${ADMIN_VIDEO_BASE}/${videoId}`, { method: 'DELETE' });
}

/** 重新转码（管理端兜底：重新 ffprobe 校验 + HLS 切片）；videoId 为字符串雪花 ID */
export function retranscodeVideo(videoId: string): Promise<void> {
  return request<void>(`${ADMIN_VIDEO_BASE}/${videoId}/retranscode`, { method: 'POST' });
}

/** 视频元数据（上传后轮询处理状态用）；videoId 为字符串雪花 ID */
export function getVideoMeta(videoId: string): Promise<VideoMeta> {
  return request<VideoMeta>(`${VIDEO_BASE}/meta/${videoId}`);
}

/** 生成播放签名 URL（playlistUrl + 每个 .ts 分片的签名 URL，1 小时有效）；videoId 为字符串雪花 ID */
export function getVideoPlayInfo(videoId: string): Promise<VideoPlayInfo> {
  return request<VideoPlayInfo>(`${VIDEO_BASE}/play/${videoId}`);
}

/**
 * 初始化分片上传会话。
 *
 * @param file 待上传文件（取文件名/大小/MIME）
 * @returns sessionId 与建议分片大小
 */
export function initVideoUpload(file: File): Promise<VideoInitResp> {
  return request<VideoInitResp>(`${VIDEO_BASE}/init`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({
      fileName: file.name,
      fileSize: file.size,
      mimeType: file.type || 'video/mp4',
      uploaderType: 'admin',
      // 来源固定 browser：管理端走浏览器上传，服务端据此走 HLS 切片兜底链路
      sourceType: 'browser',
    }),
  });
}

/**
 * 上传单个分片。不要手动设置 Content-Type，交给浏览器生成 multipart boundary。
 */
export function uploadVideoChunk(sessionId: string, chunkIndex: number, data: Blob): Promise<void> {
  const form = new FormData();
  form.append('sessionId', sessionId);
  form.append('chunkIndex', String(chunkIndex));
  form.append('data', data, `chunk_${chunkIndex}`);
  return request<void>(`${VIDEO_BASE}/chunk`, { method: 'POST', body: form });
}

/** 查询上传会话（断点续传：返回已成功上传的分片索引列表） */
export function getVideoUploadSession(sessionId: string): Promise<VideoUploadSession> {
  return request<VideoUploadSession>(`${VIDEO_BASE}/session/${sessionId}`);
}

/** 合并分片并触发后台 ffprobe 校验 + HLS 切片 */
export function completeVideoUpload(sessionId: string): Promise<VideoCompleteResp> {
  return request<VideoCompleteResp>(`${VIDEO_BASE}/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ sessionId }),
  });
}
