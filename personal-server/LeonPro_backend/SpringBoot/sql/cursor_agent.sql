-- Cursor 任务：每人一把加密后的 API Key，任务按 user_id 隔离。
-- 可重复执行。后端启动时 SchemaPatcher 也会自动建这两张表。
-- 菜单由 frontend_phone 的 menus.json 同步，不要在这里插 sys_menus。

CREATE TABLE IF NOT EXISTS `cursor_user_key` (
  `user_id` VARCHAR(64) NOT NULL COMMENT '系统用户ID',
  `key_cipher` VARCHAR(700) NOT NULL COMMENT 'AES-GCM 加密后的 Cursor API Key',
  `key_hint` VARCHAR(8) NOT NULL COMMENT 'Key 末四位，用于界面确认',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自己的 Cursor API Key';

CREATE TABLE IF NOT EXISTS `cursor_agent` (
  `id` VARCHAR(64) NOT NULL COMMENT '本地主键',
  `user_id` VARCHAR(64) NOT NULL COMMENT '系统用户ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT 'Cursor agent id',
  `name` VARCHAR(120) DEFAULT NULL COMMENT '显示名',
  `prompt_preview` VARCHAR(200) DEFAULT NULL COMMENT '首条指令摘要',
  `repo_url` VARCHAR(300) DEFAULT NULL COMMENT '仓库地址',
  `starting_ref` VARCHAR(120) DEFAULT NULL COMMENT '起始分支或提交',
  `agent_status` VARCHAR(32) DEFAULT NULL COMMENT 'ACTIVE/IDLE/ARCHIVED',
  `run_status` VARCHAR(32) DEFAULT NULL COMMENT '最近一次执行状态',
  `latest_run_id` VARCHAR(80) DEFAULT NULL COMMENT '最近一次 run id',
  `result_text` MEDIUMTEXT COMMENT '最近一次执行结果',
  `agent_url` VARCHAR(300) DEFAULT NULL COMMENT 'cursor.com/agents 链接',
  `pr_url` VARCHAR(300) DEFAULT NULL COMMENT 'PR 链接',
  `branch_name` VARCHAR(200) DEFAULT NULL COMMENT '推送分支',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cursor_agent_owner` (`user_id`, `agent_id`),
  KEY `idx_cursor_agent_user_time` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户发起的 Cursor Cloud Agent';
