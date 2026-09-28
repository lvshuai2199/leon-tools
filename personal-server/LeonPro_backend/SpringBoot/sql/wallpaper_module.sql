-- ============================================================
-- 壁纸模块：分组 / 图片两张表 + 后台菜单「壁纸管理」
-- 可重复执行（表 IF NOT EXISTS，菜单 ON DUPLICATE KEY UPDATE）。
-- 后端启动时 SchemaPatcher 也会自动建这两张表；菜单需执行本脚本。
-- 图片文件存放在 app.wallpaper.storage-dir（默认 ./uploads/wallpaper）/{groupId}/，
-- 通过 /uploads/wallpaper/{groupId}/{文件名} 静态访问（生产经 nginx 为 /prod-api/uploads/wallpaper/...）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `wallpaper_group` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键',
  `name` VARCHAR(100) NOT NULL COMMENT '分组名称',
  `group_key` VARCHAR(64) NOT NULL COMMENT '分组标识（小写字母/数字/-），外部接口按此取图',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `sort` INT DEFAULT 0 COMMENT '排序（升序）',
  `is_public` INT DEFAULT 1 COMMENT '是否公开 1是 0否',
  `access_token` VARCHAR(128) DEFAULT NULL COMMENT '访问令牌（创建分组时自动生成，外部随机接口必须携带）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallpaper_group_key` (`group_key`),
  KEY `idx_wallpaper_group_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='壁纸分组';

CREATE TABLE IF NOT EXISTS `wallpaper_image` (
  `id` VARCHAR(64) NOT NULL COMMENT '主键',
  `group_id` VARCHAR(64) NOT NULL COMMENT '分组ID',
  `title` VARCHAR(200) DEFAULT NULL COMMENT '标题',
  `file_path` VARCHAR(500) NOT NULL COMMENT '原图相对路径（相对壁纸存储目录，如 {groupId}/{uuid}.jpg）',
  `thumb_path` VARCHAR(500) DEFAULT NULL COMMENT '缩略图相对路径',
  `width` INT DEFAULT NULL COMMENT '原图宽',
  `height` INT DEFAULT NULL COMMENT '原图高',
  `file_size` BIGINT DEFAULT NULL COMMENT '原图字节数',
  `sort` INT DEFAULT 0 COMMENT '组内排序（升序）',
  `enabled` INT DEFAULT 1 COMMENT '是否启用 1是 0否',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_wallpaper_image_group` (`group_id`, `enabled`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='壁纸图片';

-- 后台菜单：工具中心 / 壁纸管理，路由 /tool/wallpaper，组件 src/views/tool/wallpaper/index.vue
INSERT INTO sys_menus (id, menu_name, menu_url, parent_id, sort_order, icon, visible, menu_type, component, redirect, route_name, always_show, keep_alive)
VALUES
  ('menu_wallpaper', '壁纸管理', 'wallpaper', 'menu_tool', 5, 'el-icon-Picture', 1, 1, 'tool/wallpaper/index', NULL, 'Wallpaper', 0, 0)
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

-- 已拥有「思维导图」（工具中心）菜单的角色，补上「壁纸管理」；ROOT 角色默认拥有全部菜单
INSERT INTO sys_role_menu (id, rold_id, menu_id)
SELECT UUID(), r.rold_id, 'menu_wallpaper'
FROM (
  SELECT DISTINCT rold_id
  FROM sys_role_menu
  WHERE menu_id = 'menu_mindmap'
) r
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu x
  WHERE x.rold_id = r.rold_id AND x.menu_id = 'menu_wallpaper'
);
