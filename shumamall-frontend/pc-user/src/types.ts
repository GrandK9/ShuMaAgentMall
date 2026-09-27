/** 与 shumamall-backend 各服务 DTO 对应的前端类型定义 */

/** 统一响应包装 R<T>：code=2000 表示成功 */
export interface ApiResp<T> {
  code: number;
  msg: string;
  data: T;
}

/** auth 登录响应 */
export interface LoginResp {
  token: string;
  /** 请求签名密钥：调用下单/支付等写接口时用于计算 X-Sign */
  signSecret: string;
  userId: number;
  username: string;
  roleCodes: string[];
}

/** 分页结果 */
export interface PageResult<T> {
  page: number;
  size: number;
  total: number;
  pages: number;
  records: T[];
}

/* ---------------- 商品（shumamall-product） ---------------- */

/** 商品 SKU */
export interface ProductSku {
  id: number;
  productId: number;
  skuCode: string;
  specs?: string;
  price: number;
  stock?: number;
  image?: string;
  status?: number;
}

/** 商品 VO（用户端展示） */
export interface Product {
  id: number;
  categoryId?: number;
  brandId?: number;
  name: string;
  subtitle?: string;
  description?: string;
  mainImage?: string;
  price: number;
  stock?: number;
  salesVolume?: number;
  isNew?: number;
  isHot?: number;
  isRecommend?: number;
  skuList?: ProductSku[];
}

/** 商品分页查询参数 */
export interface ProductPageQuery {
  page?: number;
  size?: number;
  categoryId?: number;
  brandId?: number;
  keyword?: string;
  status?: number;
  isHot?: number;
  isNew?: number;
  isRecommend?: number;
  sortBy?: string;
  sortOrder?: string;
}

/* ---------------- 搜索（shumamall-search，ES 全文检索） ---------------- */

/** ES 搜索结果项（对齐后端 ProductSearchVO，不含 SKU 列表） */
export interface ProductSearchItem {
  productId: number;
  name: string;
  subtitle?: string;
  mainImage?: string;
  price?: number;
  salesVolume?: number;
  categoryName?: string;
  brandName?: string;
  score?: number;
}

/** 商品搜索参数（sortBy：default / sales / price_asc / price_desc / newest） */
export interface ProductSearchQuery {
  keyword?: string;
  categoryId?: number;
  brandIds?: number[];
  priceMin?: number;
  priceMax?: number;
  sortBy?: string;
  page?: number;
  size?: number;
}

/* ---------------- 分类（shumamall-product） ---------------- */

export interface Category {
  id: number;
  parentId?: number;
  name: string;
  icon?: string;
  sortOrder?: number;
  status?: number;
  children?: Category[];
}

/* ---------------- 用户（shumamall-user） ---------------- */

export interface UserInfo {
  id: number;
  username: string;
  nickname?: string;
  avatar?: string;
  phone?: string;
  email?: string;
  gender?: number;
  status?: number;
}

/* ---------------- 评论（shumamall-comment） ---------------- */

/** 评论 VO（用户端展示） */
export interface Comment {
  /** 雪花 ID：19 位，后端按字符串下发（转 number 会丢末位精度，回传即「评论不存在」） */
  id: string;
  productId: number;
  /** 根评论 ID（字符串雪花 ID，渲染为回复时非空） */
  rootId?: string;
  /** 直接父评论 ID（字符串雪花 ID） */
  parentId?: string;
  userId?: number;
  username?: string;
  avatar?: string;
  content: string;
  rating?: number;
  /** 评论附带视频 ID（字符串雪花 ID，无视频时为空） */
  videoId?: string;
  likeCount?: number;
  replyCount?: number;
  reportCount?: number;
  status?: number;
  replyToUserId?: number;
  replyToUsername?: string;
  createdAt?: string;
}

/* ---------------- 购物车（shumamall-order） ---------------- */

export interface CartItem {
  id: number;
  skuId: number;
  productId: number;
  productName: string;
  productImage?: string;
  skuSpecs?: string;
  price: number;
  quantity: number;
  selected?: number;
  subtotal: number;
}

export interface CartAddDTO {
  skuId: number;
  productId: number;
  quantity?: number;
}

/* ---------------- 订单（shumamall-order） ---------------- */

export interface OrderItem {
  id: number;
  skuId: number;
  productId: number;
  productName: string;
  skuSpecs?: string;
  productImage?: string;
  price: number;
  quantity: number;
  subtotal: number;
}

export interface Order {
  id: number;
  orderNo: string;
  userId?: number;
  totalAmount: number;
  payAmount: number;
  freightAmount?: number;
  status: number;
  statusLabel: string;
  paymentMethod?: number;
  paymentNo?: string;
  paymentTime?: string;
  deliveryTime?: string;
  receiveTime?: string;
  remark?: string;
  addressSnapshot?: string;
  cancelReason?: string;
  createdAt?: string;
  items?: OrderItem[];
}

/** 创建订单请求 */
export interface OrderCreateDTO {
  productId: number;
  productName: string;
  skuId?: number;
  quantity?: number;
  addressId: number;
  remark?: string;
  cartItemIds?: number[];
}

/* ---------------- 支付（shumamall-payment） ---------------- */

export interface PayResponse {
  paymentId: number;
  paymentNo: string;
  orderNo: string;
  amount: number;
  status: number;
  statusLabel: string;
  paidAt?: string;
}

/* ---------------- 地址（shumamall-user） ---------------- */

export interface Address {
  id: number;
  consignee: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  isDefault?: number;
  label?: string;
}

/* ---------------- agent 对话（shumamall-agent） ---------------- */

/** agent 规划中的单个步骤 */
export interface PlanStep {
  stepId: string;
  description: string;
  toolName: string;
  params: Record<string, unknown>;
}

/** agent 规划结果（Plan-then-Execute） */
export interface AgentPlan {
  goal: string;
  steps: PlanStep[];
}

/** agent 一次工具调用的结果 */
export interface ToolResult {
  toolName: string;
  args: string;
  status: 'SUCCESS' | 'FAILED' | string;
  result: string;
}

/** Human-in-the-Loop：待确认的高风险步骤 */
export interface PendingStep {
  stepId: string;
  description: string;
  toolName: string;
  params: Record<string, unknown>;
  /**
   * 服务端签发的一次性确认凭据（HMAC 签名，绑定会话 + 步骤 + 操作内容，5 分钟有效、用后作废）。
   * 用户确认后原样回传，不要自行构造。
   */
  confirmToken?: string;
}

/** Human-in-the-Loop：confirm 事件负载 */
export interface ConfirmEvent {
  pendingSteps: PendingStep[];
  confirmMessage: string;
}

/** agent 对话响应 */
export interface ChatResp {
  sessionId: string;
  reply: string;
  skillId: string;
  plan: AgentPlan;
  toolResults: ToolResult[];
}

/** 流式对话 meta 事件负载（工具调用过程先于文本推送） */
export interface ChatMeta {
  sessionId: string;
  skillId: string;
  plan: AgentPlan;
  toolResults: ToolResult[];
}

/** 历史消息项 */
export interface ChatHistoryMsg {
  role: 'user' | 'assistant';
  content: string;
}

/** 会话摘要 */
export interface ChatSessionSummary {
  sessionId: string;
  lastMessagePreview: string;
  messageCount: number;
}

/** 前端消息模型 */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  skillId?: string;
  plan?: AgentPlan;
  toolResults?: ToolResult[];
}

/* ---------------- 视频（shumamall-video 8092） ---------------- */

/** 评论区视频直传结果（POST /api/v1/video/upload，服务端同步校验 + 切片） */
export interface VideoUploadResp {
  /** 雪花 ID：19 位，后端按字符串下发，前端不可转 number */
  videoId: string;
  /** validating / transcoded / failed */
  status: string;
  /** 播放清单路径（转码完成后非空） */
  playlistUrl?: string;
  /** 失败原因（失败时非空） */
  failReason?: string;
}

/** 分片签名 URL 项：index 对应 m3u8 中的分片顺序 */
export interface VideoSegmentUrl {
  index: number;
  url: string;
}

/** 播放信息：playlistUrl 与各 .ts 分片均为 MinIO 签名 URL（1 小时有效） */
export interface VideoPlayInfo {
  /** 雪花 ID：19 位，超出 JS 安全整数范围，后端按字符串下发，前端不可转 number */
  videoId: string;
  /** transcoded / failed */
  status: string;
  playlistUrl?: string;
  duration?: number;
  thumbnailUrl?: string;
  failReason?: string;
  segmentUrls?: VideoSegmentUrl[];
}

/** 播放进度（断点续播用） */
export interface VideoProgress {
  videoId: string;
  /** 上次播放位置（秒） */
  position: number;
}
