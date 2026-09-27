// =============================================
// 数码商城 (ShuMaMall) MongoDB 初始化脚本
// 首次启动时自动执行
// =============================================

db = db.getSiblingDB('shumamall');

// ==================== 视频模块 ====================

// 视频元数据集合
// MinIO 存二进制文件（原始上传、HLS 分片、缩略图），
// MongoDB 存元数据（格式、时长、分辨率、转码状态、HLS 清单路径）
db.createCollection('videos');
db.videos.createIndex({ userId: 1 });
db.videos.createIndex({ 'metadata.productId': 1 }, { sparse: true });
db.videos.createIndex({ status: 1 });
db.videos.createIndex({ createdAt: -1 });

// 视频分片上传记录
// 断点续传场景：记录每个分片的上传状态
db.createCollection('upload_sessions');
db.upload_sessions.createIndex({ uploadId: 1 }, { unique: true });
db.upload_sessions.createIndex({ userId: 1, status: 1 });
db.upload_sessions.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }); // TTL 自动清理过期会话

// ==================== 评论模块 ====================

// 评论集合（文档模型天然支持嵌套回复，无需 JOIN）
db.createCollection('comments');
db.comments.createIndex({ targetType: 1, targetId: 1, createdAt: -1 });
db.comments.createIndex({ userId: 1 });
db.comments.createIndex({ parentId: 1 });
db.comments.createIndex({ status: 1 });
db.comments.createIndex({ createdAt: -1 });

// ==================== 审计日志 ====================

// 操作审计日志（非结构化日志，文档模型写入快）
db.createCollection('audit_logs');
db.audit_logs.createIndex({ createdAt: -1 });
db.audit_logs.createIndex({ type: 1, createdAt: -1 });
db.audit_logs.createIndex({ userId: 1, createdAt: -1 });

// ==================== 视频断点续播 ====================

// 视频播放进度（高频写入走 Redis，定时持久化到 MongoDB）
db.createCollection('video_progress');
db.video_progress.createIndex({ userId: 1, videoId: 1 }, { unique: true });
db.video_progress.createIndex({ updatedAt: 1 });
