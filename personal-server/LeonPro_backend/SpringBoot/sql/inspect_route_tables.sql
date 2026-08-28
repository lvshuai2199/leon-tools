-- 对比开发库 / 生产库：用户、角色、菜单、角色菜单
SELECT 'dev.sys_users' AS t, COUNT(*) AS n FROM leonpro_db_dev.sys_users
UNION ALL SELECT 'prod.sys_users', COUNT(*) FROM leonpro_db_prod.sys_users
UNION ALL SELECT 'dev.sys_roles', COUNT(*) FROM leonpro_db_dev.sys_roles
UNION ALL SELECT 'prod.sys_roles', COUNT(*) FROM leonpro_db_prod.sys_roles
UNION ALL SELECT 'dev.sys_menus', COUNT(*) FROM leonpro_db_dev.sys_menus
UNION ALL SELECT 'prod.sys_menus', COUNT(*) FROM leonpro_db_prod.sys_menus
UNION ALL SELECT 'dev.sys_role_menu', COUNT(*) FROM leonpro_db_dev.sys_role_menu
UNION ALL SELECT 'prod.sys_role_menu', COUNT(*) FROM leonpro_db_prod.sys_role_menu;

SELECT username, nickname, role_id FROM leonpro_db_dev.sys_users;
SELECT username, nickname, role_id FROM leonpro_db_prod.sys_users;
SELECT id, menu_name, parent_id, menu_url, sort_order FROM leonpro_db_dev.sys_menus ORDER BY sort_order, id;
SELECT id, menu_name, parent_id, menu_url, sort_order FROM leonpro_db_prod.sys_menus ORDER BY sort_order, id;
