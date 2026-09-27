import type {
  Address,
  ApiResp,
  CartAddDTO,
  CartItem,
  Category,
  ChatHistoryMsg,
  ChatMeta,
  ChatResp,
  ChatSessionSummary,
  Comment,
  ConfirmEvent,
  LoginResp,
  Order,
  OrderCreateDTO,
  PageResult,
  PayResponse,
  Product,
  ProductPageQuery,
  ProductSearchItem,
  ProductSearchQuery,
  UserInfo,
  VideoPlayInfo,
  VideoProgress,
  VideoUploadResp,
} from './types';

import { buildSignHeaders, isSignedRequest } from './sign';

const AUTH_BASE = '/api/v1/auth';
const USER_BASE = '/api/v1/user';
const CATEGORY_BASE = '/api/v1/categories';
const AGENT_BASE = '/api/v1/agent';
const PRODUCT_BASE = '/api/v1/user/product';
const CART_BASE = '/api/v1/cart';
const ORDER_BASE = '/api/v1/orders';
const PAYMENT_BASE = '/api/v1/payment';
const ADDRESS_BASE = '/api/v1/user/address';
const VIDEO_BASE = '/api/v1/video';

/** 从 localStorage 读取登录 token（登录成功后由 App.vue 写入） */
function authToken(): string {
  return localStorage.getItem('agent_token') || '';
}

/** 从 localStorage 读取签名密钥（登录时由 auth 服务下发，与 token 同生命周期） */
function authSignSecret(): string {
  return localStorage.getItem('agent_sign_secret') || '';
}

/** 统一请求：自动附带 Authorization 头（写接口额外附带 X-Sign 签名头），并解包 R<T> */
async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    ...(init.headers as Record<string, string> | undefined),
  };
  const token = authToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  // 下单/支付等写接口需要请求签名：method、path、query、时间戳、nonce、body 全部参与 HMAC，
  // 少签任何一项后端都会判为篡改（业务码 4014）——这样即使 token 泄露，
  // 拿到 token 的人也无法自行拼接 body 调起下单或支付。
  const method = (init.method || 'GET').toUpperCase();
  const rawBody = typeof init.body === 'string' ? init.body : '';
  if (isSignedRequest(method, url)) {
    const secret = authSignSecret();
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

/** 登录，换取 JWT token（auth 8081） */
export function login(username: string, password: string): Promise<LoginResp> {
  return request<LoginResp>(`${AUTH_BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ username, password }),
  });
}

/** 注册新用户（auth 8081），注册成功后直接返回登录态 */
export function register(dto: { username: string; password: string; phone?: string; email?: string }): Promise<LoginResp> {
  return request<LoginResp>(`${AUTH_BASE}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

/* ---------------- 商品（product 8083） ---------------- */

/** 用户端分页查询已上架商品 */
export function getProductPage(query: ProductPageQuery = {}): Promise<PageResult<Product>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 20, ...query }).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  });
  return request<PageResult<Product>>(`${PRODUCT_BASE}?${params.toString()}`);
}

/** 商品详情 */
export function getProductDetail(id: number): Promise<Product> {
  return request<Product>(`${PRODUCT_BASE}/${id}`);
}

/* ---------------- 搜索（search 8087，ES 全文检索） ---------------- */

const SEARCH_BASE = '/api/v1/search';

/**
 * ES 全文检索商品（IK 中文分词 + 分类/价格过滤 + 多字段排序）。
 * ES 或搜索服务不可用时由调用方决定是否降级到商品分页接口。
 */
export function searchProducts(q: ProductSearchQuery = {}): Promise<PageResult<ProductSearchItem>> {
  const params = new URLSearchParams();
  Object.entries({ page: 1, size: 20, ...q }).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    if (Array.isArray(v)) {
      v.forEach((item) => params.append(k, String(item)));
    } else {
      params.set(k, String(v));
    }
  });
  return request<PageResult<ProductSearchItem>>(`${SEARCH_BASE}/product?${params.toString()}`);
}

/** 分类树（公开接口） */
export function getCategoryTree(): Promise<Category[]> {
  // 后端公开接口是 /api/v1/categories/tree，漏掉 /tree 会命中 404 被兜底成 5000，表现为分类导航为空
  return request<Category[]>(`${CATEGORY_BASE}/tree`);
}

/* ---------------- 评论（comment 8089） ---------------- */

const COMMENT_BASE = '/api/v1/comment';

/** 全站热门评论榜（跨商品，按点赞数倒序） */
export function getHotComments(limit = 10): Promise<Comment[]> {
  return request<Comment[]>(`${COMMENT_BASE}/hot?limit=${limit}`);
}

/** 分页查询商品根评论（sortBy：latest-最新 hot-最热） */
export function getRootComments(
  productId: number,
  page = 1,
  size = 10,
  sortBy = 'latest',
): Promise<PageResult<Comment>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sortBy });
  return request<PageResult<Comment>>(`${COMMENT_BASE}/product/${productId}?${params.toString()}`);
}

/** 分页查询根评论下的回复（时间正序） */
export function getReplies(rootId: string, page = 1, size = 20): Promise<PageResult<Comment>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return request<PageResult<Comment>>(`${COMMENT_BASE}/root/${rootId}?${params.toString()}`);
}

/**
 * 发表评论。
 *
 * @param dto.videoId 可选，评论附带视频 ID（字符串雪花 ID，先调 uploadCommentVideo 拿到）
 */
export function createComment(dto: {
  productId: number;
  content: string;
  rating?: number;
  videoId?: string;
}): Promise<Comment> {
  return request<Comment>(COMMENT_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

/** 回复评论（rootId / parentId 均为字符串雪花 ID） */
export function replyComment(
  rootId: string,
  dto: { content: string; parentId?: string; replyToUserId?: number; replyToUsername?: string },
): Promise<Comment> {
  return request<Comment>(`${COMMENT_BASE}/${rootId}/reply`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

/** 点赞评论（幂等），返回点赞后的点赞数 */
export function likeComment(commentId: string): Promise<number> {
  return request<number>(`${COMMENT_BASE}/${commentId}/like`, { method: 'POST' });
}

/** 举报评论 */
export function reportComment(commentId: string): Promise<void> {
  return request<void>(`${COMMENT_BASE}/${commentId}/report`, { method: 'POST' });
}

/** 删除自己的评论（软删除） */
export function deleteComment(commentId: string): Promise<void> {
  return request<void>(`${COMMENT_BASE}/${commentId}`, { method: 'DELETE' });
}

/* ---------------- 购物车（order 8084） ---------------- */

export function getCart(): Promise<CartItem[]> {
  return request<CartItem[]>(CART_BASE);
}

export function addCart(dto: CartAddDTO): Promise<void> {
  return request<void>(CART_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function updateCartQuantity(skuId: number, quantity: number): Promise<void> {
  return request<void>(`${CART_BASE}/${skuId}?quantity=${quantity}`, { method: 'PUT' });
}

export function updateCartSelected(skuId: number, selected: number): Promise<void> {
  return request<void>(`${CART_BASE}/${skuId}/selected?selected=${selected}`, { method: 'PUT' });
}

export function removeCartItem(skuId: number): Promise<void> {
  return request<void>(`${CART_BASE}/${skuId}`, { method: 'DELETE' });
}

export function clearCart(): Promise<void> {
  return request<void>(CART_BASE, { method: 'DELETE' });
}

/* ---------------- 订单（order 8084） ---------------- */

export function createOrder(dto: OrderCreateDTO): Promise<Order> {
  return request<Order>(ORDER_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function getOrders(page = 1, size = 10, status?: number): Promise<PageResult<Order>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status !== undefined && status !== null) params.set('status', String(status));
  return request<PageResult<Order>>(`${ORDER_BASE}?${params.toString()}`);
}

export function getOrderDetail(id: number): Promise<Order> {
  return request<Order>(`${ORDER_BASE}/${id}`);
}

export function cancelOrder(id: number): Promise<void> {
  return request<void>(`${ORDER_BASE}/${id}/cancel`, { method: 'PUT' });
}

/** 确认收货：订单状态 2→3，仅订单所有者可操作 */
export function receiveOrder(id: number): Promise<void> {
  return request<void>(`${ORDER_BASE}/${id}/receive`, { method: 'PUT' });
}

/* ---------------- 支付（payment 8085） ---------------- */

/** 支付：直调接口，一次调用完成流水 + 订单状态 0→1 */
export function payOrder(orderNo: string, paymentMethod: number, amount: number): Promise<PayResponse> {
  return request<PayResponse>(`${PAYMENT_BASE}/pay`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ orderNo, paymentMethod, amount }),
  });
}

/* ---------------- 用户（user 8082） ---------------- */

export function getUserInfo(): Promise<UserInfo> {
  return request<UserInfo>(`${USER_BASE}/info`);
}

export function updateUserInfo(params: { phone?: string; email?: string; nickname?: string; gender?: number }): Promise<UserInfo> {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null) query.set(k, String(v));
  });
  return request<UserInfo>(`${USER_BASE}/info?${query.toString()}`, { method: 'PUT' });
}

/* ---------------- 地址（user 8082） ---------------- */

export function getAddresses(): Promise<Address[]> {
  return request<Address[]>(ADDRESS_BASE);
}

export function createAddress(dto: Partial<Address>): Promise<Address> {
  return request<Address>(ADDRESS_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function updateAddress(id: number, dto: Partial<Address>): Promise<Address> {
  return request<Address>(`${ADDRESS_BASE}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(dto),
  });
}

export function deleteAddress(id: number): Promise<void> {
  return request<void>(`${ADDRESS_BASE}/${id}`, { method: 'DELETE' });
}

/* ---------------- agent（agent 8088） ---------------- */

/** 查询会话历史消息 */
export function getChatHistory(token: string, sessionId: string): Promise<ChatHistoryMsg[]> {
  return request<ChatHistoryMsg[]>(`${AGENT_BASE}/chat/history?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** 查询当前用户的会话列表摘要 */
export function getChatSessions(token: string): Promise<ChatSessionSummary[]> {
  return request<ChatSessionSummary[]>(`${AGENT_BASE}/chat/sessions`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** 删除指定会话 */
export function deleteChatSession(token: string, sessionId: string): Promise<void> {
  return request<void>(`${AGENT_BASE}/chat/session?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** Agent 对话请求选项 */
export interface ChatOptions {
  /**
   * 用户已确认的高风险操作凭据（Human-in-the-Loop）。
   * 凭据由服务端在 confirm 事件中签发（`pendingSteps[].confirmToken`），前端只负责原样回传；
   * 服务端会校验签名与「会话 + 步骤 + 操作内容」绑定关系并一次性消费。
   */
  confirmTokens?: string[];
  /** 是否开启 ReAct 多轮工具调用 */
  reactEnabled?: boolean;
  /**
   * 中止流式请求的信号。
   * <p>
   * SSE 是长连接：不主动 abort 时 `getReader()` 会一直读到服务端 complete。
   * 组件卸载（离开聊天页）等「结果已经没人要」的场景应传入 AbortSignal 及时断开，
   * 否则连接与后续回调会一直存活到本轮回复结束。
   */
  signal?: AbortSignal;
}

/** 发送对话消息（非流式，保留备用） */
export function chat(
  token: string,
  message: string,
  sessionId: string,
  options: ChatOptions = {},
): Promise<ChatResp> {
  return request<ChatResp>(`${AGENT_BASE}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      message,
      sessionId,
      confirmTokens: options.confirmTokens ?? [],
      reactEnabled: options.reactEnabled ?? false,
    }),
  });
}

/** 流式对话回调 */
export interface ChatStreamHandlers {
  /** meta 事件：sessionId / skillId / plan / toolResults（工具调用过程） */
  onMeta: (meta: ChatMeta) => void;
  /** token 事件：LLM 回复逐块追加 */
  onToken: (token: string) => void;
  /** confirm 事件：高风险操作需要用户二次确认（Human-in-the-Loop） */
  onConfirm?: (confirm: ConfirmEvent) => void;
  /** 流正常结束 */
  onDone: () => void;
}

/**
 * 流式对话（agent 8088，SSE）。
 * 事件顺序：
 * 1. meta 事件（规划 + 工具执行结果）
 * 2. confirm 事件（如有高风险操作需二次确认，此时不会收到 token）
 * 3. token 事件（LLM 回复逐块推送）
 */
export async function chatStream(
  token: string,
  message: string,
  sessionId: string,
  handlers: ChatStreamHandlers,
  options: ChatOptions = {},
): Promise<void> {
  const res = await fetch(`${AGENT_BASE}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      Authorization: `Bearer ${token}`,
    },
    // 透传中止信号：abort 后 fetch 抛 AbortError，由调用方决定是否提示
    signal: options.signal,
    body: JSON.stringify({
      message,
      sessionId,
      confirmTokens: options.confirmTokens ?? [],
      reactEnabled: options.reactEnabled ?? false,
    }),
  });
  if (!res.ok) {
    // SSE 失败时后端同样返回统一 R 结构（如 401 未登录），读出 msg 才能展示真实原因。
    // 401 场景下响应体是 JSON 而非事件流，这里读完即抛，不会进入下面的流式解析。
    const body = (await res.json().catch(() => null)) as ApiResp<unknown> | null;
    throw new Error(body?.msg || `HTTP ${res.status}`);
  }
  if (!res.body) {
    throw new Error('响应无内容');
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const { events, rest } = parseSse(buffer);
      buffer = rest;
      for (const ev of events) {
        if (ev.event === 'meta') {
          handlers.onMeta(JSON.parse(ev.data) as ChatMeta);
        } else if (ev.event === 'confirm') {
          handlers.onConfirm?.(JSON.parse(ev.data) as ConfirmEvent);
        } else if (ev.event === 'token') {
          handlers.onToken(parseTokenData(ev.data));
        }
      }
    }
    handlers.onDone();
  } finally {
    reader.releaseLock();
  }
}

interface SseEvent {
  event: string;
  data: string;
}

/**
 * 解析 token 事件的 data。
 *
 * 服务端把 token 序列化成 JSON 字符串下发（见 AgentChatController#safeSendNamedEvent）：
 * 若直接传裸文本，回复里的换行会被 SSE 的空行分帧吞掉、首字符空格会被「去掉一个前导空格」
 * 的规范约定吃掉，表现为气泡里整段缺空格、markdown 多行被压成一行。
 *
 * 解析失败时退回原文：万一将来服务端改回裸文本，也不至于整条回复变成空。
 */
function parseTokenData(data: string): string {
  try {
    const parsed = JSON.parse(data);
    return typeof parsed === 'string' ? parsed : data;
  } catch {
    return data;
  }
}

/**
 * 解析 SSE 数据块：事件以空行分隔。
 * data 多行需按 \n 合并（备用的裸文本协议下，含换行的数据会被拆成多行 data:）。
 * 注意：只去掉 data: 后可选的一个空格，保留其余内容（缩进/空行等 markdown 结构）。
 */
function parseSse(buffer: string): { events: SseEvent[]; rest: string } {
  const events: SseEvent[] = [];
  const parts = buffer.split(/\r?\n\r?\n/);
  const rest = parts.pop() ?? '';
  for (const part of parts) {
    if (!part.trim()) continue;
    let event = 'message';
    let data = '';
    for (const line of part.split(/\r?\n/)) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        // 按 SSE 规范去掉字段值的一个前导空格（字段分隔符）。服务端下发的一律是 JSON
        // （首字符为 " 或 {），所以去掉的只可能是这个分隔符，数据本身不受影响。
        // 不要改成 trim()/trimStart()：那会吃掉 JSON 里的缩进，也会破坏裸文本协议的兼容路径。
        data += (data ? '\n' : '') + line.slice(5).replace(/^ /, '');
      }
    }
    events.push({ event, data });
  }
  return { events, rest };
}

/* ---------------- 视频（video 8092） ---------------- */

/**
 * 评论区视频直传（单次上传，服务端同步 ffprobe 校验 + HLS 切片）。
 *
 * 只用于评论区小文件（≤100MB / ≤60s）；管理端 2GB 素材走分片上传，不在用户端实现。
 * 显式传 `sourceType=browser`：后端默认值是已砍掉的小程序端（miniapp），不传会被记错来源。
 *
 * @param file 视频文件（前端已做大小/时长/分辨率预检）
 * @returns videoId（字符串雪花 ID）与处理状态
 */
export function uploadCommentVideo(file: File): Promise<VideoUploadResp> {
  const form = new FormData();
  form.append('file', file);
  form.append('uploaderType', 'user');
  form.append('sourceType', 'browser');
  // 不手动设置 Content-Type：交给浏览器带上 multipart boundary
  return request<VideoUploadResp>(`${VIDEO_BASE}/upload`, { method: 'POST', body: form });
}

/**
 * 生成播放签名 URL。
 *
 * @param videoId 雪花 ID，必须按字符串透传（19 位，转 number 会丢精度）
 * @returns playlistUrl 与每个 .ts 分片的签名 URL（同效期 1 小时）
 */
export function getVideoPlayInfo(videoId: string): Promise<VideoPlayInfo> {
  return request<VideoPlayInfo>(`${VIDEO_BASE}/play/${videoId}`);
}

/** 查询当前用户在该视频上的上次播放位置（断点续播，需登录） */
export function getVideoProgress(videoId: string): Promise<VideoProgress> {
  return request<VideoProgress>(`${VIDEO_BASE}/progress/${videoId}`);
}

/**
 * 上报播放进度（需登录）。
 * <p>
 * 服务端把高频写入放 Redis，定期回写 MongoDB，因此上报频率可以按 10 秒一次给。
 *
 * @param videoId       视频 ID（字符串雪花 ID）
 * @param position      当前播放位置（秒）
 * @param totalDuration 视频总时长（秒）
 */
export function reportVideoProgress(
  videoId: string,
  position: number,
  totalDuration: number,
): Promise<void> {
  return request<void>(`${VIDEO_BASE}/progress`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ videoId, position, totalDuration }),
  });
}
