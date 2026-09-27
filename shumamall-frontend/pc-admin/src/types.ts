/** 与 shumamall-backend 管理端 DTO 对应的前端类型定义 */

/** 统一响应包装 R<T>：code=2000 表示成功 */
export interface ApiResp<T> {
  code: number;
  msg: string;
  data: T;
}

/** 登录响应 */
export interface LoginResp {
  token: string;
  /** 请求签名密钥：调用改状态/退款等写接口时用于计算 X-Sign */
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

/* ---------------- 仪表盘（shumamall-admin 8090） ---------------- */

/** 销售趋势项：后端按天聚合（GET /api/v1/admin/dashboard 的 salesTrend，日期升序） */
export interface SalesTrendItem {
  /** yyyy-MM-dd */
  date: string;
  orderCount: number;
  salesAmount: number;
}

/** 热销商品排行项（同接口的 topProducts） */
export interface TopProduct {
  productId: number;
  productName: string;
  salesQuantity: number;
  salesAmount: number;
}

export interface Dashboard {
  totalOrders: number;
  totalProducts: number;
  totalUsers: number;
  todayOrders: number;
  todaySales: number;
  /** 近 7 天销售趋势 */
  salesTrend?: SalesTrendItem[];
  /** 热销商品 Top 10 */
  topProducts?: TopProduct[];
}

/* ---------------- 商品 SKU（shumamall-product 8083） ---------------- */

export interface ProductSku {
  id?: number;
  productId?: number;
  skuCode: string;
  specs?: string;
  price: number;
  stock?: number;
  image?: string;
  status?: number;
}

/* ---------------- 商品（shumamall-product 8083） ---------------- */

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
  status?: number;
  isNew?: number;
  isHot?: number;
  isRecommend?: number;
  skuList?: ProductSku[];
}

/* ---------------- 分类（shumamall-product 8083） ---------------- */

export interface Category {
  id: number;
  parentId?: number;
  name: string;
  icon?: string;
  sortOrder?: number;
  status?: number;
  children?: Category[];
}

/* ---------------- 品牌（shumamall-product 8083） ---------------- */

export interface Brand {
  id: number;
  name: string;
  logo?: string;
  description?: string;
  sortOrder?: number;
  status?: number;
}

/* ---------------- 订单（shumamall-order 8084） ---------------- */

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

/* ---------------- 支付记录（shumamall-payment 8085） ---------------- */

export interface PaymentRecord {
  id: number;
  paymentNo: string;
  orderNo: string;
  userId?: number;
  amount: number;
  paymentMethod?: number;
  status: number;
  statusLabel: string;
  thirdPartyNo?: string;
  paidAt?: string;
  createdAt?: string;
}

/* ---------------- 角色（shumamall-permission 8086） ---------------- */

export interface Role {
  id: number;
  name: string;
  code: string;
  status?: number;
  permissionIds?: number[];
}

/* ---------------- 权限点（shumamall-permission 8086） ---------------- */

export interface Permission {
  id: number;
  code: string;
  name: string;
  type: number; // 0-MENU 1-BUTTON 2-API
  pathPrefix?: string;
  parentId?: number;
  sortOrder?: number;
  status?: number;
}

/* ---------------- 菜单（shumamall-permission 8086） ---------------- */

export interface MenuItem {
  id: number;
  code: string;
  name: string;
  path?: string;
  children?: MenuItem[];
}

/* ---------------- 审计日志（shumamall-permission 8086） ---------------- */

/** 操作审计记录（GET /api/v1/admin/audit/logs） */
export interface AuditLog {
  /** 雪花 ID：19 位超出 JS 安全整数，后端按字符串下发 */
  id: string;
  adminUserId: number;
  /** 权限编码，多个用逗号连接 */
  action: string;
  resource: string;
  method: string;
  /** 入参 JSON（后端已剔除文件/流并截断 500 字符） */
  params?: string;
  /** SUCCESS / FORBIDDEN / FAILED */
  result: string;
  ip?: string;
  /** yyyy-MM-dd HH:mm:ss */
  occurredAt: string;
  costMs?: number;
}

/* ---------------- 评论（shumamall-comment 8089，管理端） ---------------- */

/** 评论视图对象（GET /api/v1/admin/comment） */
export interface CommentVO {
  /** 雪花 ID：19 位超出 JS 安全整数，后端按字符串下发 */
  id: string;
  productId?: number;
  /** 根评论 ID（字符串，同 id） */
  rootId?: string;
  /** 直接父评论 ID（字符串，同 id） */
  parentId?: string;
  userId?: number;
  username?: string;
  avatar?: string;
  content: string;
  /** 评分 1-5 */
  rating?: number;
  /** 附带视频 ID（字符串，同 id） */
  videoId?: string;
  likeCount?: number;
  replyCount?: number;
  reportCount?: number;
  /** 0-正常 1-作者删除 2-管理员隐藏 */
  status: number;
  replyToUserId?: number;
  replyToUsername?: string;
  createdAt?: string;
}

/* ---------------- 搜索索引（shumamall-search 8087，管理端） ---------------- */

/** 索引状态（GET /api/v1/search/index/status） */
export interface SearchIndexStatus {
  index: string;
  exists: boolean;
  /** 索引不存在时后端不返回该字段 */
  docCount?: number;
}

/* ---------------- 视频（shumamall-video 8092） ---------------- */

/** HLS 分片（元数据里的对象路径，不可直接播放） */
export interface HlsSegment {
  index: number;
  duration: number;
  url: string;
}

/** 视频元数据（管理端列表 / 详情返回） */
export interface VideoMeta {
  /** 雪花 ID：19 位，超出 JS 安全整数范围，后端按字符串下发，前端不可转 number */
  videoId: string;
  uploaderId?: number;
  /** admin / user */
  uploaderType?: string;
  /** browser / miniapp / api */
  sourceType?: string;
  /** comment / admin，决定时长与大小上限档位 */
  limitType?: string;
  /** uploading / validating / transcoded / failed */
  status: string;
  format?: string;
  duration?: number;
  width?: number;
  height?: number;
  fileSize?: number;
  playlistUrl?: string;
  thumbnailUrl?: string;
  failReason?: string;
  uploadedAt?: string;
  transcodedAt?: string;
  hlsSegments?: HlsSegment[];
}

/** 分片签名 URL 项：index 对应 m3u8 中的分片顺序 */
export interface VideoSegmentUrl {
  index: number;
  url: string;
}

/** 播放信息（签名 URL，1 小时有效） */
export interface VideoPlayInfo {
  /** 雪花 ID，字符串形式（同 VideoMeta.videoId） */
  videoId: string;
  status: string;
  playlistUrl?: string;
  duration?: number;
  thumbnailUrl?: string;
  failReason?: string;
  segmentUrls?: VideoSegmentUrl[];
}

/** 初始化分片上传会话的响应 */
export interface VideoInitResp {
  sessionId: string;
  /** 建议分片大小（字节） */
  chunkSize: number;
  expireAt: string;
}

/** 上传会话（用于断点续传：chunks 是已成功上传的分片索引） */
export interface VideoUploadSession {
  sessionId: string;
  fileName: string;
  fileSize: number;
  status: string;
  chunks: number[];
  /** 雪花 ID，字符串形式（同 VideoMeta.videoId） */
  videoId?: string;
}

/** 单次直传的响应 */
export interface VideoUploadResp {
  videoId?: string;
  status: string;
  playlistUrl?: string;
  failReason?: string;
}

/** 分片合并完成的响应 */
export interface VideoCompleteResp {
  sessionId: string;
  videoId?: string;
  status: string;
}
