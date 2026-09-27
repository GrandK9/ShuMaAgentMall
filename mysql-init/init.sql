-- =============================================
-- 数码商城 (ShuMaMall) 数据库初始化脚本
-- =============================================

-- 创建数据库（配合 MYSQL_DATABASE 环境变量，双重保障）
CREATE DATABASE IF NOT EXISTS shumamall
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE shumamall;

-- ==================== 用户服务 ====================

-- 用户表
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`    VARCHAR(50)  NOT NULL                COMMENT '用户名',
  `password`    VARCHAR(255) NOT NULL                COMMENT '加密密码',
  `phone`       VARCHAR(255) DEFAULT NULL            COMMENT '手机号（AES-256-CBC + 随机 IV 加密存储）',
  `email`       VARCHAR(255) DEFAULT NULL            COMMENT '邮箱（AES-256-CBC + 随机 IV 加密存储）',
  `avatar`      VARCHAR(500) DEFAULT NULL            COMMENT '头像URL',
  `nickname`    VARCHAR(50)  DEFAULT NULL            COMMENT '昵称',
  `role`        VARCHAR(20)  DEFAULT 'user'          COMMENT '角色 user-普通用户 admin-管理员',
  `gender`      TINYINT      DEFAULT 0               COMMENT '性别 0-未知 1-男 2-女',
  `status`      TINYINT      DEFAULT 1               COMMENT '状态 0-禁用 1-正常',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
  -- 注：phone 走随机 IV 加密，同一手机号每次密文不同，唯一索引已失去语义，故不建 uk_phone
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户地址表
CREATE TABLE `user_address` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id`      BIGINT       NOT NULL                COMMENT '用户ID',
  `receiver`     VARCHAR(255) NOT NULL                COMMENT '收件人（AES-256-CBC 加密存储）',
  `phone`        VARCHAR(255) NOT NULL                COMMENT '联系电话（AES-256-CBC 加密存储）',
  `province`     VARCHAR(50)  NOT NULL                COMMENT '省',
  `city`         VARCHAR(50)  NOT NULL                COMMENT '市',
  `district`     VARCHAR(50)  NOT NULL                COMMENT '区/县',
  `detail`       VARCHAR(500) NOT NULL                COMMENT '详细地址（AES-256-CBC 加密存储）',
  `is_default`   TINYINT      DEFAULT 0               COMMENT '是否默认 0-否 1-是',
  `label`        VARCHAR(50)  DEFAULT NULL            COMMENT '地址标签（如：家、公司）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户地址表';

-- ==================== 商品服务 ====================

-- 分类表
CREATE TABLE `category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id`   BIGINT       DEFAULT 0               COMMENT '父分类ID, 0为顶级',
  `name`        VARCHAR(50)  NOT NULL                COMMENT '分类名称',
  `icon`        VARCHAR(500) DEFAULT NULL            COMMENT '图标URL',
  `sort_order`  INT          DEFAULT 0               COMMENT '排序值',
  `status`      TINYINT      DEFAULT 1               COMMENT '状态 0-隐藏 1-显示',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 品牌表
CREATE TABLE `brand` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '品牌ID',
  `name`        VARCHAR(100) NOT NULL                COMMENT '品牌名称',
  `logo`        VARCHAR(500) DEFAULT NULL            COMMENT '品牌Logo',
  `description` VARCHAR(255) DEFAULT NULL            COMMENT '品牌描述',
  `sort_order`  INT          DEFAULT 0               COMMENT '排序值',
  `status`      TINYINT      DEFAULT 1               COMMENT '状态 0-隐藏 1-显示',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌表';

-- 商品表
CREATE TABLE `product` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id`     BIGINT       NOT NULL                COMMENT '分类ID',
  `brand_id`        BIGINT       DEFAULT NULL            COMMENT '品牌ID',
  `name`            VARCHAR(200) NOT NULL                COMMENT '商品名称',
  `subtitle`        VARCHAR(500) DEFAULT NULL            COMMENT '副标题/卖点',
  `description`     TEXT         DEFAULT NULL            COMMENT '商品详情(HTML)',
  `main_image`      VARCHAR(500) DEFAULT NULL            COMMENT '主图URL',
  `price`           DECIMAL(10,2) NOT NULL               COMMENT '默认价格',
  `stock`           INT          DEFAULT 0               COMMENT '默认库存',
  `sales_volume`    INT          DEFAULT 0               COMMENT '销量',
  `comment_count`   INT          DEFAULT 0               COMMENT '评论数（评论区服务回写）',
  `status`          TINYINT      DEFAULT 1               COMMENT '状态 0-下架 1-上架',
  `is_new`          TINYINT      DEFAULT 0               COMMENT '是否新品 0-否 1-是',
  `is_hot`          TINYINT      DEFAULT 0               COMMENT '是否热销 0-否 1-是',
  `is_recommend`    TINYINT      DEFAULT 0               COMMENT '是否推荐 0-否 1-是',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_is_hot` (`is_hot`),
  KEY `idx_is_recommend` (`is_recommend`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 商品 SKU 表
CREATE TABLE `product_sku` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id`  BIGINT        NOT NULL                COMMENT '商品ID',
  `sku_code`    VARCHAR(50)   NOT NULL                COMMENT 'SKU编码',
  `specs`       JSON          DEFAULT NULL            COMMENT '规格值 JSON, 如 {"颜色":"黑色","存储":"256GB"}',
  `price`       DECIMAL(10,2) NOT NULL                COMMENT 'SKU价格',
  `stock`       INT           DEFAULT 0               COMMENT 'SKU库存',
  `image`       VARCHAR(500)  DEFAULT NULL            COMMENT 'SKU图片',
  `status`      TINYINT       DEFAULT 1               COMMENT '状态 0-禁用 1-启用',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- 商品图片表
CREATE TABLE `product_image` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `product_id`  BIGINT       NOT NULL                COMMENT '商品ID',
  `image_url`   VARCHAR(500) NOT NULL                COMMENT '图片URL',
  `sort_order`  INT          DEFAULT 0               COMMENT '排序值',
  `type`        TINYINT      DEFAULT 0               COMMENT '类型 0-展示图 1-详情图',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';

-- ==================== 订单服务 ====================

-- 购物车表
CREATE TABLE `cart` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
  `user_id`     BIGINT        NOT NULL                COMMENT '用户ID',
  `sku_id`      BIGINT        NOT NULL                COMMENT 'SKU ID',
  `product_id`  BIGINT        NOT NULL                COMMENT '商品ID',
  `quantity`    INT           DEFAULT 1               COMMENT '数量',
  `selected`    TINYINT       DEFAULT 1               COMMENT '是否选中 0-否 1-是',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- 订单表
CREATE TABLE `order` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no`        VARCHAR(32)   NOT NULL                COMMENT '订单编号',
  `user_id`         BIGINT        NOT NULL                COMMENT '用户ID',
  `total_amount`    DECIMAL(10,2) NOT NULL                COMMENT '订单总金额',
  `pay_amount`      DECIMAL(10,2) NOT NULL                COMMENT '实付金额',
  `freight_amount`  DECIMAL(10,2) DEFAULT 0.00            COMMENT '运费',
  `status`          TINYINT       DEFAULT 0               COMMENT '状态 0-待付款 1-待发货 2-待收货 3-已完成 4-已取消 5-售后中',
  `payment_method`  TINYINT       DEFAULT NULL            COMMENT '支付方式 1-微信 2-支付宝',
  `payment_no`      VARCHAR(32)   DEFAULT NULL            COMMENT '支付流水号',
  `payment_time`    DATETIME      DEFAULT NULL            COMMENT '支付时间',
  `delivery_time`   DATETIME      DEFAULT NULL            COMMENT '发货时间',
  `receive_time`    DATETIME      DEFAULT NULL            COMMENT '收货时间',
  `remark`          VARCHAR(500)  DEFAULT NULL            COMMENT '订单备注',
  `address_snapshot` JSON         DEFAULT NULL            COMMENT '收货地址快照',
  `cancel_reason`   VARCHAR(255)  DEFAULT NULL            COMMENT '取消原因',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单明细表
CREATE TABLE `order_item` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id`      BIGINT        NOT NULL                COMMENT '订单ID',
  `order_no`      VARCHAR(32)   NOT NULL                COMMENT '订单编号',
  `sku_id`        BIGINT        NOT NULL                COMMENT 'SKU ID',
  `product_id`    BIGINT        NOT NULL                COMMENT '商品ID',
  `product_name`  VARCHAR(200)  NOT NULL                COMMENT '商品名称',
  `sku_specs`     JSON          DEFAULT NULL            COMMENT 'SKU规格快照',
  `product_image` VARCHAR(500)  DEFAULT NULL            COMMENT '商品图片快照',
  `price`         DECIMAL(10,2) NOT NULL                COMMENT '单价',
  `quantity`      INT           NOT NULL                COMMENT '数量',
  `subtotal`      DECIMAL(10,2) NOT NULL                COMMENT '小计',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ==================== 支付服务 ====================

-- 支付记录表
CREATE TABLE `payment_record` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `payment_no`      VARCHAR(32)   NOT NULL                COMMENT '支付流水号',
  `order_no`        VARCHAR(32)   NOT NULL                COMMENT '订单编号',
  `user_id`         BIGINT        NOT NULL                COMMENT '用户ID',
  `amount`          DECIMAL(10,2) NOT NULL                COMMENT '支付金额',
  `payment_method`  TINYINT       DEFAULT NULL            COMMENT '支付方式 1-微信 2-支付宝',
  `status`          TINYINT       DEFAULT 0               COMMENT '状态 0-待支付 1-支付成功 2-支付失败 3-已退款',
  `third_party_no`  VARCHAR(100)  DEFAULT NULL            COMMENT '第三方支付流水号',
  `paid_at`         DATETIME      DEFAULT NULL            COMMENT '支付成功时间',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  -- 一单一付：同一订单最多一条支付记录。并发重复支付的数据库级兜底，
  -- 应用层的「先查后插」存在竞态，唯一约束才能保证精确一次。
  UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- ==================== 管理员相关 ====================

-- 管理员表
CREATE TABLE `admin_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username`    VARCHAR(50)  NOT NULL                COMMENT '用户名',
  `password`    VARCHAR(255) NOT NULL                COMMENT '加密密码',
  `real_name`   VARCHAR(50)  DEFAULT NULL            COMMENT '真实姓名',
  `phone`       VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
  `avatar`      VARCHAR(500) DEFAULT NULL            COMMENT '头像',
  `role`        VARCHAR(50)  DEFAULT 'admin'         COMMENT '角色 super_admin / admin / editor',
  `status`      TINYINT      DEFAULT 1               COMMENT '状态 0-禁用 1-正常',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 插入默认管理员（密码: admin123 的 BCrypt 加密值，用 BCrypt.checkpw 可验证）
INSERT INTO `admin_user` (`username`, `password`, `real_name`, `role`) VALUES
('admin', '$2a$10$gExff0vnbw3qeXx9F1kUIub/8hKkujmsCQECjGsy6cy01LJf34xHm', '超级管理员', 'super_admin');

-- 同时在 user 表中插入管理员记录（auth 模块统一从 user 表认证）
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', '$2a$10$gExff0vnbw3qeXx9F1kUIub/8hKkujmsCQECjGsy6cy01LJf34xHm', '超级管理员', 'admin', 1);

-- ==================== 码表（系统字典） ====================

CREATE TABLE `sys_dict` (
  `id`          BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  `type`        VARCHAR(100) NOT NULL                COMMENT '字典类型编码，如 product_spec、order_reason',
  `type_label`  VARCHAR(100) NOT NULL                COMMENT '字典类型中文名，如 "商品规格"',
  `code`        VARCHAR(50)  NOT NULL                COMMENT '字典项编码，如 color_black',
  `label`       VARCHAR(100) NOT NULL                COMMENT '字典项显示值，如 "黑色"',
  `value`       VARCHAR(255) DEFAULT NULL            COMMENT '字典项实际值（可选），如 hex #000000',
  `sort_order`  INT          DEFAULT 0               COMMENT '排序',
  `status`      TINYINT      DEFAULT 1               COMMENT '状态 0-禁用 1-启用',
  `remark`      VARCHAR(255) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_type_code` (`type`, `code`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典表（码表）';

-- 插入默认码表数据
INSERT INTO `sys_dict` (`id`, `type`, `type_label`, `code`, `label`, `value`, `sort_order`, `status`, `remark`) VALUES
(1, 'product_spec', '商品规格', 'phone_color', '颜色', NULL, 1, 1, '手机颜色规格'),
(2, 'product_spec', '商品规格', 'phone_storage', '存储容量', NULL, 2, 1, '手机存储容量规格'),
(3, 'payment_method', '支付方式', 'wechat', '微信支付', 'wx', 1, 1, NULL),
(4, 'payment_method', '支付方式', 'alipay', '支付宝', 'alipay', 2, 1, NULL);

-- ==================== Seata 分布式事务 ====================

-- AT 模式要求每个参与全局事务的库都有这张表：分支事务提交前 Seata 在此写入
-- 「前镜像」，全局回滚时据此生成反向 SQL。order / product 共用 shumamall 库，故只建一份。
-- 注意：init.sql 只在 MySQL 数据卷首次初始化时执行，已存在的库需手工执行本段 DDL。
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id`     BIGINT       NOT NULL COMMENT '分支事务 ID',
  `xid`           VARCHAR(128) NOT NULL COMMENT '全局事务 ID',
  `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log 上下文，如序列化方式',
  `rollback_info` LONGBLOB     NOT NULL COMMENT '回滚信息（前镜像 + 后镜像）',
  `log_status`    INT          NOT NULL COMMENT '0-正常 1-防御态（前镜像校验不一致）',
  `log_created`   DATETIME(6)  NOT NULL COMMENT '创建时间',
  `log_modified`  DATETIME(6)  NOT NULL COMMENT '修改时间',
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT 模式 undo_log 表';
