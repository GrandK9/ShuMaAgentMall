-- ============================================
-- 权限服务初始化数据
-- 库: shumamall_perm
-- ============================================

USE shumamall_perm;

-- ----------------------------
-- 1. 权限点（菜单 + 按钮）
-- ----------------------------
INSERT INTO perm_permission (id, code, name, type, path_prefix, parent_id, sort_order, status) VALUES
-- 一级菜单
(1,  'dashboard:view',  '仪表盘',     0, '/admin/dashboard',   0,  1, 1),
(2,  'product:view',    '商品管理',   0, '/admin/product/**',  0,  2, 1),
(3,  'category:view',   '分类管理',   0, '/admin/category/**', 0,  3, 1),
(4,  'brand:view',      '品牌管理',   0, '/admin/brand/**',    0,  4, 1),
(5,  'order:view',      '订单管理',   0, '/admin/order/**',    0,  5, 1),
-- 24/25 为补齐项：pc-admin 侧边栏由本表菜单权限点驱动（App.vue 调 GET /permission/menus，
-- 按 code → 路由映射渲染），而"支付记录""角色管理"两个页面原先没有对应菜单权限点，
-- 接动态菜单后会从侧边栏消失，故补上。
(24, 'payment:view',    '支付记录',   0, '/admin/payment/**',  0,  6, 1),
(25, 'role:view',       '角色管理',   0, '/admin/role/**',     0,  7, 1),
(7,  'permission:view', '权限管理',   0, '/admin/permission/**',0,  8, 1),
-- user:view 目前管理端没有对应页面，前端 code → 路由映射里无此项会被跳过（保留权限点备用）
(6,  'user:view',       '用户管理',   0, '/admin/user/**',     0,  9, 1),
-- video:view 同理：pc-admin 暂无视频管理页，前端映射里无此 code 会被跳过（保留备用）
(26, 'video:view',      '视频管理',   0, '/admin/video/**',    0, 10, 1),

-- 商品管理按钮
(8,  'product:create',  '创建商品',   1, NULL, 2, 1, 1),
(9,  'product:edit',    '编辑商品',   1, NULL, 2, 2, 1),
(10, 'product:delete',  '删除商品',   1, NULL, 2, 3, 1),
(11, 'product:publish', '上架/下架',  1, NULL, 2, 4, 1),

-- 分类管理按钮
(12, 'category:create', '创建分类',   1, NULL, 3, 1, 1),
(13, 'category:edit',   '编辑分类',   1, NULL, 3, 2, 1),
(14, 'category:delete', '删除分类',   1, NULL, 3, 3, 1),

-- 品牌管理按钮
(15, 'brand:create',    '创建品牌',   1, NULL, 4, 1, 1),
(16, 'brand:edit',      '编辑品牌',   1, NULL, 4, 2, 1),
(17, 'brand:delete',    '删除品牌',   1, NULL, 4, 3, 1),

-- 订单管理按钮
(18, 'order:edit',      '编辑订单',   1, NULL, 5, 1, 1),
(19, 'order:refund',    '退款处理',   1, NULL, 5, 2, 1),

-- 权限管理按钮
(20, 'permission:assign','分配权限',  1, NULL, 7, 1, 1),
(21, 'permission:create','创建权限点',1, NULL, 7, 2, 1),
(22, 'permission:edit',  '编辑权限点',1, NULL, 7, 3, 1),
(23, 'permission:delete','删除权限点',1, NULL, 7, 4, 1),

-- 视频管理按钮（shumamall-video 管理端接口，原先无鉴权、匿名可删视频）
(27, 'video:delete',      '删除视频',  1, NULL, 26, 1, 1),
(28, 'video:retranscode', '重新转码',  1, NULL, 26, 2, 1),

-- 审计日志（GET /api/v1/admin/audit/logs 的权限点，同时是 pc-admin 审计日志页的菜单项）
-- 2026-09-17 由 type=2 API 改为 0 MENU：pc-admin 已实现审计日志页，
-- 菜单树接口只查 type=0，不改类型则侧边栏不会出现该页入口。
(29, 'audit:view',        '审计日志',  0, '/admin/audit/**',    0, 11, 1),
-- comment:view / search:view：pc-admin 新增「评论管理」「搜索索引管理」两页。
-- 菜单树接口只返回 type=0 的权限点，不补这两行侧边栏就没有入口（页面本身可直达）。
(30, 'comment:view',      '评论管理',  0, '/admin/comment/**',  0, 12, 1),
(31, 'search:view',       '搜索索引',  0, '/api/v1/search/**',  0, 13, 1);

-- ----------------------------
-- 2. 角色
-- ----------------------------
INSERT INTO perm_role (id, name, code, status) VALUES
(1, '超级管理员', 'role_admin', 1);

-- ----------------------------
-- 3. 角色→权限（admin 角色拥有全部权限）
-- ----------------------------
INSERT INTO perm_role_permission (role_id, permission_id)
SELECT 1, id FROM perm_permission WHERE status = 1;

-- ----------------------------
-- 4. 管理员→角色
-- 权限校验链路的 userId 来自 auth 服务签发的 JWT，而 AuthServiceImpl.login
-- 统一从 shumamall.user 表认证（管理员在 user 表里也有一条同名记录）。
-- 因此这里必须挂 user 表里 admin 的 id：原文硬编码 1（admin_user 表的 id）
-- 与登录身份不是同一张表，导致 permission 服务按 JWT 里的 user_id 查不到任何
-- 角色，所有 @RequirePermission 接口一律返回 4031 权限不足。
-- ----------------------------
INSERT INTO perm_admin_user_role (admin_user_id, role_id)
SELECT u.id, 1
FROM shumamall.user u
WHERE u.username = 'admin';
