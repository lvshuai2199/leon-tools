-- ============================================================
-- 羽毛球计费：球局主表 + 场地费 / 用球费明细
-- 可重复执行（表 IF NOT EXISTS）。后端启动时 SchemaPatcher 也会自动建这三张表。
-- 已有库加整桶价：sql/badminton_bucket_price.sql（后端启动时 SchemaPatcher 也会自动补这一列）。
-- 菜单由页面清单同步（vue3_frontend / frontend_phone 的 menus.json），不要在这里插 sys_menus。
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
  `bucket_price` DECIMAL(10,2) NULL DEFAULT NULL COMMENT '整桶价格（12个），空=没填',
  `amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '小计',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_badminton_ball_bill` (`bill_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='羽毛球用球费用明细';
