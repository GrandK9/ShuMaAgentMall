-- 秒杀资格表按订单号回补活动额度。
-- init.sql 只在数据目录首次初始化时执行，已有库需要单独加索引。
--
-- 执行：docker exec -i shumamall-mysql mysql -uroot -proot123 < seckill_order_no_index.sql

USE shumamall;

ALTER TABLE `seckill_order` ADD INDEX `idx_order_no` (`order_no`);
