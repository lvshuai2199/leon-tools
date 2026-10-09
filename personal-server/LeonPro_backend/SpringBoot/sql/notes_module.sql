-- ============================================================
-- 笔记模块：仓库配置、Markdown 索引、随手记缓存
-- 可重复执行（表 IF NOT EXISTS）。后端启动时 SchemaPatcher 也会自动建这三张表。
-- 菜单由页面清单同步（vue3_frontend / frontend_phone 的 menus.json），不要在这里插 sys_menus。
-- 仓库克隆在 app.notes.storage-dir（默认 ./data/notes-repo）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `note_source` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键，固定 default',
  `repo_url` VARCHAR(500) NOT NULL COMMENT '仓库 https 地址',
  `branch` VARCHAR(200) NOT NULL COMMENT '分支',
  `access_token` VARCHAR(500) DEFAULT NULL COMMENT '私有仓库 / 上传用的访问令牌',
  `last_commit` VARCHAR(64) DEFAULT NULL COMMENT '已同步的提交',
  `last_sync_time` DATETIME DEFAULT NULL COMMENT '最近一次同步完成时间',
  `last_check_time` DATETIME DEFAULT NULL COMMENT '最近一次检查远程提交的时间',
  `last_error` VARCHAR(1000) DEFAULT NULL COMMENT '最近一次失败或未收录说明',
  `sync_status` VARCHAR(32) NOT NULL DEFAULT 'idle' COMMENT 'idle/syncing/ok/error',
  `file_count` INT NOT NULL DEFAULT 0 COMMENT '已收录的 Markdown 数量',
  `enabled` INT NOT NULL DEFAULT 1 COMMENT '1 自动检查新提交',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记仓库';

CREATE TABLE IF NOT EXISTS `note_doc` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键',
  `path` VARCHAR(768) NOT NULL COMMENT '仓库内相对路径',
  `title` VARCHAR(300) NOT NULL COMMENT '标题',
  `size_bytes` INT NOT NULL DEFAULT 0 COMMENT '字节数',
  PRIMARY KEY (`id`),
  KEY `idx_note_doc_path` (`path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记文档索引';

CREATE TABLE IF NOT EXISTS `note_draft` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键',
  `title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '标题',
  `content` MEDIUMTEXT COMMENT '正文，上传前只存在这里',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近缓存时间',
  PRIMARY KEY (`id`),
  KEY `idx_note_draft_update` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随手记缓存';
