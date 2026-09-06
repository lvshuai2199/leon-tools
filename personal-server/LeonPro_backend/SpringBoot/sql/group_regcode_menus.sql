-- 将注册码相关菜单收拢到「注册码」目录，并补齐注册码用户
INSERT INTO sys_menus (id, menu_name, menu_url, parent_id, sort_order, icon, visible, menu_type, component, redirect, route_name, always_show, keep_alive)
VALUES
  ('menu_regcode_center', '注册码', '/regcode', '0', 2, 'key', 1, 0, 'Layout', '/regcode/generate', 'RegCodeCenter', 1, 0),
  ('menu_regcode', '注册码生成', 'generate', 'menu_regcode_center', 1, 'key', 1, 1, 'tool/regcode/index', NULL, 'RegCode', 0, 0),
  ('menu_regcode_config', '注册码配置', 'config', 'menu_regcode_center', 2, 'setting', 1, 1, 'tool/regcode-config/index', NULL, 'RegCodeConfig', 0, 0),
  ('menu_regcode_user', '注册码用户', 'user', 'menu_regcode_center', 3, 'user', 1, 1, 'tool/regcode-user/index', NULL, 'RegCodeUser', 0, 1),
  ('menu_registration', '注册码记录', 'records', 'menu_regcode_center', 4, 'client', 1, 1, 'work/registration/index', NULL, 'Registration', 0, 1)
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

INSERT INTO sys_role_menu (id, rold_id, menu_id)
SELECT UUID(), r.rold_id, m.menu_id
FROM (
  SELECT DISTINCT rold_id
  FROM sys_role_menu
  WHERE menu_id IN ('menu_regcode', 'menu_regcode_config', 'menu_regcode_user', 'menu_registration')
) r
JOIN (
  SELECT 'menu_regcode_center' AS menu_id
  UNION ALL SELECT 'menu_regcode'
  UNION ALL SELECT 'menu_regcode_config'
  UNION ALL SELECT 'menu_regcode_user'
  UNION ALL SELECT 'menu_registration'
) m
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu x
  WHERE x.rold_id = r.rold_id AND x.menu_id = m.menu_id
);
