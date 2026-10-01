-- ============================================================
-- 羽毛球计费：球局主表 + 场地费 / 用球费明细 + 后台菜单
-- 可重复执行（表 IF NOT EXISTS，菜单 ON DUPLICATE KEY UPDATE）。
-- 后端启动时 SchemaPatcher 也会自动建这三张表；菜单由 MenuDataSeeder 补插。
-- ============================================================

CREATE TABLE IF NOT EXISTS `badminton_bill` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键',
  `play_date` VARCHAR(10) NOT NULL COMMENT '球局日期 yyyy-MM-dd',
  `title` VARCHAR(100) DEFAULT NULL COMMENT '标题',
  `participant_count` INT DEFAULT 1 COMMENT '参与人数',
  `court_total` DECIMAL(12,2) DEFAULT 0.00 COMMENT '场地费合计',
  `ball_total` DECIMAL(12,2) DEFAULT 0.00 COMMENT '用球费用合计',
  `grand_total` DECIMAL(12,2) DEFAULT 0.00 COMMENT '总费用',
  `per_person` DECIMAL(12,2) DEFAULT 0.00 COMMENT '个人应付',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_badminton_bill_date` (`play_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='羽毛球球局计费';

CREATE TABLE IF NOT EXISTS `badminton_court_fee` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键',
  `bill_id` VARCHAR(64) NOT NULL COMMENT '球局ID',
  `court_count` INT DEFAULT 1 COMMENT '场地数量（片）',
  `hours` DECIMAL(10,2) DEFAULT 0.00 COMMENT '时长（小时）',
  `unit_price` DECIMAL(12,2) DEFAULT 0.00 COMMENT '单价（元/片/小时）',
  `amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '小计',
  `remark` VARCHAR(100) DEFAULT NULL COMMENT '备注（场地名/时段）',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_badminton_court_bill` (`bill_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='羽毛球场地费明细';

CREATE TABLE IF NOT EXISTS `badminton_ball_fee` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键',
  `bill_id` VARCHAR(64) NOT NULL COMMENT '球局ID',
  `brand` VARCHAR(50) DEFAULT NULL COMMENT '用球品牌',
  `quantity` INT DEFAULT 0 COMMENT '数量',
  `unit_price` DECIMAL(12,2) DEFAULT 0.00 COMMENT '单价',
  `amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '小计',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_badminton_ball_bill` (`bill_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='羽毛球用球费用明细';

-- 后台菜单：工具中心 / 羽毛球计费，路由 /tool/badminton，组件 src/views/tool/badminton/index.vue
INSERT INTO sys_menus (id, menu_name, menu_url, parent_id, sort_order, icon, visible, menu_type, component, redirect, route_name, always_show, keep_alive)
VALUES
  ('menu_badminton', '羽毛球计费', 'badminton', 'menu_tool', 6, 'el-icon-Trophy', 1, 1, 'tool/badminton/index', NULL, 'BadmintonBill', 0, 0)
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  menu_url = VALUES(menu_url),
  parent_id = VALUES(parent_id),
  sort_order = VALUES(sort_order),
  icon = VALUES(icon),
  visible = VALUES(visible),
  menu_type = VALUES(menu_type),
  component = VALUES(component),
  redirect = VALUES(redirect),
  route_name = VALUES(route_name),
  always_show = VALUES(always_show),
  keep_alive = VALUES(keep_alive);

-- 已有思维导图或任务管理菜单的角色，补上「羽毛球计费」；ROOT 默认拥有全部菜单
INSERT INTO sys_role_menu (id, rold_id, menu_id)
SELECT UUID(), r.rold_id, 'menu_badminton'
FROM (
  SELECT DISTINCT rold_id
  FROM sys_role_menu
  WHERE menu_id IN ('menu_mindmap', 'menu_tasks', 'menu_tool')
) r
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu x
  WHERE x.rold_id = r.rold_id AND x.menu_id = 'menu_badminton'
);
