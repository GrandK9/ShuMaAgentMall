-- ============================================================
-- 敏感字段加密改造迁移脚本（2026-09-17）
--
-- 背景：user.phone / user.email、user_address.receiver / user_address.phone
--       改为 AES-256-CBC + 随机 IV 加密存储（MyBatis EncryptTypeHandler 透明加解密）。
--
-- 本脚本做两件事：
--   1. 列扩容：随机 IV 密文按 v1:{Base64(IV)}:{Base64(密文)} 存储，长度远超原列宽
--              （11 位手机号加密后约 60 字符），原 VARCHAR(20) 必须扩到 VARCHAR(255)。
--   2. 删除 uk_phone 唯一索引：随机 IV 保证同一手机号每次密文不同，唯一索引已失去语义
--              （相同手机号写入不再冲突），保留只会造成"约束存在但不起作用"的误解。
--              当前业务无手机号登录/查重场景；若后续需要手机号唯一性，应新增
--              HMAC-SHA256 摘要列（盲索引）并在该列上建唯一约束，而不是回退确定性加密。
--
-- 存量明文数据无需转换：解密时遇到无 v1: 前缀的值原样返回，存量明文在下次写入时自然转为密文。
--
-- 执行：docker exec -i shumamall-mysql mysql -uroot -proot123 < encrypt_migration.sql
-- ============================================================

USE shumamall;

ALTER TABLE `user`
  MODIFY COLUMN `phone` VARCHAR(255) DEFAULT NULL COMMENT '手机号（AES-256-CBC + 随机 IV 加密存储）',
  MODIFY COLUMN `email` VARCHAR(255) DEFAULT NULL COMMENT '邮箱（AES-256-CBC + 随机 IV 加密存储）';

ALTER TABLE `user` DROP INDEX `uk_phone`;

ALTER TABLE `user_address`
  MODIFY COLUMN `receiver` VARCHAR(255) NOT NULL COMMENT '收件人（AES-256-CBC 加密存储）',
  MODIFY COLUMN `phone` VARCHAR(255) NOT NULL COMMENT '联系电话（AES-256-CBC 加密存储）',
  MODIFY COLUMN `detail` VARCHAR(500) NOT NULL COMMENT '详细地址（AES-256-CBC 加密存储）';
