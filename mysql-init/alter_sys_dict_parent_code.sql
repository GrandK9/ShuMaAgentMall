-- 补充脚本：为 sys_dict 增加 parent_code 列。
-- 早期 init.sql 建表语句中未包含该字段，而 DictEntity / 码表树形接口已使用 parentCode；
-- 已有库执行本脚本即可对齐；init.sql 中 CREATE TABLE 已同步补上，新库无需再跑。
--
-- init.sql 只在 MySQL 数据卷首次初始化时执行，不会自动改已有表结构。
--
-- 执行：docker exec -i shumamall-mysql mysql -uroot -proot123 < mysql-init/alter_sys_dict_parent_code.sql

USE shumamall;

ALTER TABLE `sys_dict`
  ADD COLUMN `parent_code` VARCHAR(50) DEFAULT NULL COMMENT '父级字典项 code，同 type 下构建树形结构'
  AFTER `value`;
