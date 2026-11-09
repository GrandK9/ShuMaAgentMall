-- 审计 / 评论 / 视频（MySQL 表，与 init.sql 主库同库 shumamall）
-- 已有库执行：docker exec -i shumamall-mysql mysql -uroot -proot123 < mysql-init/document_tables.sql

USE shumamall;

-- ==================== 管理端审计 ====================
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`             BIGINT       NOT NULL COMMENT '雪花 ID',
  `admin_user_id`  BIGINT       DEFAULT NULL COMMENT '操作人',
  `action`         VARCHAR(500) NOT NULL COMMENT '权限编码，逗号连接',
  `resource`       VARCHAR(500) DEFAULT NULL COMMENT '请求 URI',
  `method`         VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP 方法',
  `params`         TEXT         DEFAULT NULL COMMENT '入参 JSON（截断）',
  `result`         VARCHAR(32)  NOT NULL COMMENT 'SUCCESS/FORBIDDEN/FAILED',
  `ip`             VARCHAR(64)  DEFAULT NULL,
  `occurred_at`    DATETIME(3)  NOT NULL COMMENT '操作发生时刻',
  `cost_ms`        BIGINT       DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_occurred` (`admin_user_id`, `occurred_at` DESC),
  KEY `idx_result_occurred` (`result`, `occurred_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端操作审计';

-- ==================== 商品评论 ====================
CREATE TABLE IF NOT EXISTS `product_comment` (
  `id`                BIGINT      NOT NULL COMMENT '雪花 ID',
  `product_id`        BIGINT      NOT NULL,
  `root_id`           BIGINT      DEFAULT NULL COMMENT '根评论 ID，根评论自身为 NULL',
  `parent_id`         BIGINT      DEFAULT NULL COMMENT '直接父评论 ID',
  `user_id`           BIGINT      NOT NULL,
  `content`           TEXT        NOT NULL,
  `rating`            TINYINT     DEFAULT NULL COMMENT '1-5，仅根评论',
  `video_id`          BIGINT      DEFAULT NULL,
  `like_count`        INT         NOT NULL DEFAULT 0,
  `reply_count`       INT         NOT NULL DEFAULT 0,
  `report_count`      INT         NOT NULL DEFAULT 0,
  `status`            TINYINT     NOT NULL DEFAULT 0 COMMENT '0正常 1作者删 2管理员隐藏',
  `reply_to_user_id`  BIGINT      DEFAULT NULL,
  `created_at`        DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_product_status` (`product_id`, `status`),
  KEY `idx_root_status` (`root_id`, `status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评论';

CREATE TABLE IF NOT EXISTS `comment_like` (
  `id`          BIGINT      NOT NULL,
  `comment_id`  BIGINT      NOT NULL,
  `user_id`     BIGINT      NOT NULL,
  `created_at`  DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论点赞';

CREATE TABLE IF NOT EXISTS `comment_report` (
  `id`          BIGINT      NOT NULL,
  `comment_id`  BIGINT      NOT NULL,
  `user_id`     BIGINT      NOT NULL,
  `created_at`  DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论举报';

-- ==================== 视频 ====================
CREATE TABLE IF NOT EXISTS `video_upload_session` (
  `session_id`     VARCHAR(64)  NOT NULL,
  `file_name`      VARCHAR(255) DEFAULT NULL,
  `file_size`      BIGINT       DEFAULT NULL,
  `mime_type`      VARCHAR(128) DEFAULT NULL,
  `hash`           VARCHAR(64)  DEFAULT NULL,
  `uploader_id`    BIGINT       DEFAULT NULL,
  `uploader_type`  VARCHAR(16)  DEFAULT NULL,
  `source_type`    VARCHAR(32)  DEFAULT NULL,
  `limit_type`     VARCHAR(16)  DEFAULT NULL,
  `status`         VARCHAR(32)  NOT NULL,
  `fail_reason`    VARCHAR(500) DEFAULT NULL,
  `chunks_json`    JSON         DEFAULT NULL COMMENT '已上传分片索引列表',
  `video_id`       BIGINT       DEFAULT NULL,
  `created_at`     DATETIME(3)  NOT NULL,
  `expire_at`      DATETIME(3)  DEFAULT NULL,
  PRIMARY KEY (`session_id`),
  KEY `idx_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频分片上传会话';

CREATE TABLE IF NOT EXISTS `video_metadata` (
  `video_id`       BIGINT       NOT NULL,
  `session_id`     VARCHAR(64)  DEFAULT NULL,
  `uploader_id`    BIGINT       DEFAULT NULL,
  `uploader_type`  VARCHAR(16)  DEFAULT NULL,
  `source_type`    VARCHAR(32)  DEFAULT NULL,
  `limit_type`     VARCHAR(16)  DEFAULT NULL,
  `status`         VARCHAR(32)  NOT NULL,
  `format`         VARCHAR(16)  DEFAULT NULL,
  `audio_codec`    VARCHAR(16)  DEFAULT NULL,
  `duration`       DOUBLE       DEFAULT NULL,
  `width`          INT          DEFAULT NULL,
  `height`         INT          DEFAULT NULL,
  `file_size`      BIGINT       DEFAULT NULL,
  `raw_object`     VARCHAR(500) DEFAULT NULL,
  `playlist_url`   VARCHAR(500) DEFAULT NULL,
  `hls_segments`   JSON         DEFAULT NULL,
  `thumbnail_url`  VARCHAR(500) DEFAULT NULL,
  `fail_reason`    VARCHAR(500) DEFAULT NULL,
  `uploaded_at`    DATETIME(3)  DEFAULT NULL,
  `transcoded_at`  DATETIME(3)  DEFAULT NULL,
  PRIMARY KEY (`video_id`),
  KEY `idx_session` (`session_id`),
  KEY `idx_uploader_time` (`uploader_id`, `uploaded_at` DESC),
  KEY `idx_status_time` (`status`, `uploaded_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频元数据';

CREATE TABLE IF NOT EXISTS `video_progress` (
  `id`          VARCHAR(128) NOT NULL COMMENT 'userId:videoId',
  `user_id`     BIGINT       NOT NULL,
  `video_id`    BIGINT       NOT NULL,
  `position`    DOUBLE       DEFAULT 0,
  `updated_at`  DATETIME(3)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_video` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频播放进度';
