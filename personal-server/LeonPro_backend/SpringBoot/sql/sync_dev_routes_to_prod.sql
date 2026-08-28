-- 把 leonpro_db_dev 中的用户/角色/路由同步到 leonpro_db_prod
-- 同机 MySQL，仅覆盖这四张表
SET NAMES utf8mb4;

INSERT INTO leonpro_db_prod.sys_roles (id, create_time, description, is_disabled, role_name)
SELECT id, create_time, description, is_disabled, role_name
FROM leonpro_db_dev.sys_roles
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  description = VALUES(description),
  is_disabled = VALUES(is_disabled),
  create_time = VALUES(create_time);

DELETE FROM leonpro_db_prod.sys_role_menu;
DELETE FROM leonpro_db_prod.sys_menus;

INSERT INTO leonpro_db_prod.sys_menus (
  id, always_show, component, create_time, icon, keep_alive, menu_name,
  menu_type, menu_url, parent_id, permission, redirect, route_name,
  sort_order, update_time, visible
)
SELECT
  id, always_show, component, create_time, icon, keep_alive, menu_name,
  menu_type, menu_url, parent_id, permission, redirect, route_name,
  sort_order, update_time, visible
FROM leonpro_db_dev.sys_menus;

INSERT INTO leonpro_db_prod.sys_role_menu (id, menu_id, rold_id)
SELECT id, menu_id, rold_id FROM leonpro_db_dev.sys_role_menu;

DELETE FROM leonpro_db_prod.sys_users;
INSERT INTO leonpro_db_prod.sys_users (
  id, avatar_url, create_time, email, nickname, password, role_id, role_name, username
)
SELECT
  id, avatar_url, create_time, email, nickname, password, role_id, role_name, username
FROM leonpro_db_dev.sys_users;

SELECT 'prod_users' AS k, COUNT(*) AS n FROM leonpro_db_prod.sys_users
UNION ALL SELECT 'prod_roles', COUNT(*) FROM leonpro_db_prod.sys_roles
UNION ALL SELECT 'prod_menus', COUNT(*) FROM leonpro_db_prod.sys_menus
UNION ALL SELECT 'prod_role_menu', COUNT(*) FROM leonpro_db_prod.sys_role_menu;
SELECT username, role_id FROM leonpro_db_prod.sys_users;
SELECT id, menu_name, parent_id FROM leonpro_db_prod.sys_menus ORDER BY id;
