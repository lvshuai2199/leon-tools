-- ============================================================
-- LeonPro 数据库建表脚本
-- 根据 LeonPro_backend 实体类 + MyBatis Mapper XML 生成
-- 生成日期: 2026-08-26
-- ============================================================

-- 创建数据库 (开发环境用 leonpro_test，生产环境用 leonpro)
CREATE DATABASE IF NOT EXISTS `leonpro_test` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `leonpro_test`;

-- ============================================================
-- 1. sys_users - 用户表
-- ============================================================
DROP TABLE IF EXISTS `sys_users`;
CREATE TABLE `sys_users` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '用户ID (UUID)',
  `username`    VARCHAR(100) NOT NULL COMMENT '用户名',
  `nickname`    VARCHAR(100) DEFAULT NULL COMMENT '昵称',
  `avatar_url`  VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  `password`    VARCHAR(255) NOT NULL COMMENT '密码',
  `email`       VARCHAR(150) DEFAULT NULL COMMENT '邮箱',
  `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `role_id`     VARCHAR(64)  DEFAULT NULL COMMENT '角色ID (sys_roles.id)',
  `parent_id`   VARCHAR(64)  DEFAULT NULL COMMENT '父用户ID，空表示主用户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 2. sys_roles - 角色表
-- ============================================================
DROP TABLE IF EXISTS `sys_roles`;
CREATE TABLE `sys_roles` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '角色ID (UUID)',
  `role_name`   VARCHAR(100) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '角色描述',
  `is_disabled` INT          DEFAULT 0 COMMENT '是否禁用 (0:启用 1:禁用)',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- ============================================================
-- 3. sys_menus - 菜单表
-- ============================================================
DROP TABLE IF EXISTS `sys_menus`;
CREATE TABLE `sys_menus` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '菜单ID (UUID)',
  `menu_name`   VARCHAR(100) NOT NULL COMMENT '菜单名称',
  `menu_url`    VARCHAR(255) DEFAULT NULL COMMENT '菜单链接',
  `parent_id`   VARCHAR(64)  DEFAULT NULL COMMENT '父级菜单ID',
  `sort_order`  INT          DEFAULT 0 COMMENT '排序',
  `icon`        VARCHAR(255) DEFAULT NULL COMMENT '图标',
  `visible`     INT          DEFAULT 1 COMMENT '是否显示 (1:显示 0:隐藏)',
  `menu_type`   INT          DEFAULT 1 COMMENT '菜单类型 (0:目录 1:菜单 2:按钮)',
  `permission`  VARCHAR(255) DEFAULT NULL COMMENT '权限标识',
  `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- ============================================================
-- 4. sys_role_menu - 角色菜单关联表
-- ============================================================
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id`      VARCHAR(64) NOT NULL COMMENT '主键 (UUID)',
  `rold_id` VARCHAR(64) NOT NULL COMMENT '角色ID (注: 实体类拼写为 roldId)',
  `menu_id` VARCHAR(64) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`id`),
  KEY `idx_rold_id` (`rold_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- ============================================================
-- 5. sys_tasks - 任务表
-- ============================================================
DROP TABLE IF EXISTS `sys_tasks`;
CREATE TABLE `sys_tasks` (
  `id`             VARCHAR(64)  NOT NULL COMMENT '任务ID (UUID)',
  `task_type`      VARCHAR(50)  DEFAULT NULL COMMENT '任务类别',
  `task_name`      VARCHAR(200) NOT NULL COMMENT '任务名称',
  `description`    TEXT         DEFAULT NULL COMMENT '描述',
  `task_level`     VARCHAR(20)  DEFAULT NULL COMMENT '任务等级',
  `task_status`    VARCHAR(20)  DEFAULT NULL COMMENT '任务状态/优先级',
  `publisher_id`   VARCHAR(64)  DEFAULT NULL COMMENT '任务发布用户ID',
  `handler_id`     VARCHAR(64)  DEFAULT NULL COMMENT '处理人ID',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `customer_name`  VARCHAR(200) DEFAULT NULL COMMENT '客户名称',
  `customer_place` VARCHAR(500) DEFAULT NULL COMMENT '客户地址',
  `industry`       VARCHAR(100) DEFAULT NULL COMMENT '所属行业',
  `scenario`       VARCHAR(200) DEFAULT NULL COMMENT '应用场景',
  `robot_type`     VARCHAR(100) DEFAULT NULL COMMENT '机械臂型号',
  `robot_num`      VARCHAR(50)  DEFAULT NULL COMMENT '机械臂数量',
  `remarks`        TEXT         DEFAULT NULL COMMENT '备注/异常情况信息',
  `is_delete`      INT          DEFAULT 0 COMMENT '是否删除 (0:正常 1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_publisher_id` (`publisher_id`),
  KEY `idx_task_status` (`task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统任务表';

-- ============================================================
-- 6. sys_info - 信息表
-- ============================================================
DROP TABLE IF EXISTS `sys_info`;
CREATE TABLE `sys_info` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '信息ID (UUID)',
  `info_des`    TEXT         DEFAULT NULL COMMENT '信息描述',
  `info_status` INT          DEFAULT 1 COMMENT '信息状态',
  `public_id`   VARCHAR(64)  DEFAULT NULL COMMENT '发布者ID',
  `user_id`     VARCHAR(64)  DEFAULT NULL COMMENT '用户ID',
  `info_type`   INT          DEFAULT NULL COMMENT '信息类型',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统信息表';

-- ============================================================
-- 7. com_registration - 注册申请表
-- ============================================================
DROP TABLE IF EXISTS `com_registration`;
CREATE TABLE `com_registration` (
  `id`               VARCHAR(64)  NOT NULL COMMENT '主键 (UUID)',
  `apply_name`       VARCHAR(100) DEFAULT NULL COMMENT '申请人姓名',
  `company`          VARCHAR(200) DEFAULT NULL COMMENT '公司名称',
  `sales_name`       VARCHAR(100) DEFAULT NULL COMMENT '销售名称',
  `apply_phone`      VARCHAR(50)  DEFAULT NULL COMMENT '申请电话',
  `reg_code`         VARCHAR(255) DEFAULT NULL COMMENT '注册码',
  `reg_code_type`    INT          DEFAULT NULL COMMENT '注册码类型',
  `remarks`          TEXT         DEFAULT NULL COMMENT '备注',
  `one_month_valid`  VARCHAR(255) DEFAULT NULL COMMENT '一个月有效期注册码',
  `long_time_valid`  VARCHAR(255) DEFAULT NULL COMMENT '永久有效注册码',
  `apply_id`         VARCHAR(64)  DEFAULT NULL COMMENT '申请人ID',
  `operator`         VARCHAR(100) DEFAULT NULL COMMENT '操作人员（用户ID，无则未知人员）',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `apply_status`     INT          DEFAULT 0 COMMENT '申请状态',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册申请表';

-- ============================================================
-- 7b. reg_code_config - 注册码生成配置
-- ============================================================
DROP TABLE IF EXISTS `reg_code_config`;
CREATE TABLE `reg_code_config` (
  `id`             VARCHAR(64)  NOT NULL COMMENT '主键',
  `company`        VARCHAR(100) DEFAULT NULL COMMENT '公司',
  `name`           VARCHAR(100) DEFAULT NULL COMMENT '名称',
  `component_name` VARCHAR(100) DEFAULT NULL COMMENT '组件名称',
  `encrypt_type`   VARCHAR(50)  DEFAULT 'MD5' COMMENT '加密方式',
  `encrypt_suffix` VARCHAR(200) DEFAULT NULL COMMENT '加密字符后缀',
  `sort_order`     INT          DEFAULT 0 COMMENT '排序',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册码生成配置';

-- ============================================================
-- 7c. reg_code_user - 注册码客户账号
-- ============================================================
DROP TABLE IF EXISTS `reg_code_user`;
CREATE TABLE `reg_code_user` (
  `id`             VARCHAR(64)  NOT NULL COMMENT '主键',
  `user_id`        VARCHAR(64)  NOT NULL COMMENT '系统用户ID',
  `generate_limit` INT          DEFAULT 0 COMMENT '可生成次数',
  `generate_used`  INT          DEFAULT 0 COMMENT '已使用次数',
  `remark`         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reg_code_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册码客户账号';

DROP TABLE IF EXISTS `reg_code_user_config`;
CREATE TABLE `reg_code_user_config` (
  `id`        VARCHAR(64) NOT NULL COMMENT '主键',
  `user_id`   VARCHAR(64) NOT NULL COMMENT '系统用户ID',
  `config_id` VARCHAR(64) NOT NULL COMMENT '注册码配置ID',
  PRIMARY KEY (`id`),
  KEY `idx_reg_code_user_config_user` (`user_id`),
  KEY `idx_reg_code_user_config_cfg` (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册码客户可用配置';

-- ============================================================
-- 8. extern_wallet - 外部钱包表
-- ============================================================
DROP TABLE IF EXISTS `extern_wallet`;
CREATE TABLE `extern_wallet` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `wallet_name` VARCHAR(100) DEFAULT NULL COMMENT '钱包名称',
  `wallet_type` VARCHAR(50)  DEFAULT NULL COMMENT '钱包类型',
  `user_id`     VARCHAR(64)  DEFAULT NULL COMMENT '用户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部钱包表';

-- ============================================================
-- 9. extern_accounts - 外部账户表
-- ============================================================
DROP TABLE IF EXISTS `extern_accounts`;
CREATE TABLE `extern_accounts` (
  `id`              VARCHAR(64)  NOT NULL COMMENT '主键',
  `account_name`    VARCHAR(100) DEFAULT NULL COMMENT '账户名称',
  `wallet_id`       VARCHAR(64)  DEFAULT NULL COMMENT '钱包ID',
  `account_balance` VARCHAR(50)  DEFAULT NULL COMMENT '账户余额',
  `prifit_amount`   VARCHAR(50)  DEFAULT NULL COMMENT '利润金额 (注: 实体类拼写为 prifitAmount)',
  `user_id`         INT          DEFAULT NULL COMMENT '用户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部账户表';

-- ============================================================
-- 初始数据: 默认管理员用户与角色
-- ============================================================
INSERT INTO `sys_roles` (`id`, `role_name`, `description`, `is_disabled`)
VALUES ('role_root', 'ROOT', '超级管理员', 0);

INSERT INTO `sys_users` (`id`, `username`, `nickname`, `password`, `email`, `role_id`)
VALUES (REPLACE(UUID(), '-', ''), 'admin', '管理员', 'admin123', 'admin@leonpro.local', 'role_root');

INSERT INTO `reg_code_config` (`id`, `company`, `name`, `component_name`, `encrypt_type`, `encrypt_suffix`, `sort_order`)
VALUES
  ('rcc_weld', '通用', '焊接专机', 'weld', 'MD5', 'auboweld', 1),
  ('rcc_pallet', '通用', '码垛专机', 'pallet', 'MD5', 'aubo', 2),
  ('rcc_youbo', '友博', 'CNC插件', 'cnc', 'MD5', 'youbo_leon', 3);
