-- ============================================================
-- 录过螃蟹出货单、但（有效）角色没有出货菜单的账号核对（只读，不改任何数据）
-- 背景：二期起用户端螃蟹出货（/app/crabShipment）要求角色有“出货菜单”：
--         · 顶层账号看自己的角色；子账号（parent_id 非空）看创建人（parent_id 指向的账号）的角色；
--         · 出货菜单 = 用户端出货菜单 menu_app_crab（菜单同步后），同步前退回看管理端 menu_crab；
--         · 注册码客户角色（role_regcode_client）永远不能用出货；ROOT 永远可以。
--       菜单同步第一次运行时，只会把用户端出货菜单自动授予“已有 menu_crab 的角色”。
--       本脚本列出“录过出货单、但有效角色既没有 menu_crab 也没有 menu_app_crab”的角色，
--       核对后把要保留出货权限的角色 id 填到配置 app.menu-sync.first-grant.app-crab-extra-roles（逗号分隔），
--       下次启动时按名单授予（每个角色只授予一次）。
-- 出货单归属：crab_shipment.operator_id = sys_users.id；operator_id 为空的旧记录按 TRIM(operator_name)
--       等于 username 或 nickname（不区分大小写）匹配，只采用唯一匹配到一个账号的记录（与 crab_operator_id_backfill.sql 一致）。
-- 注意：出货单没有“来源端”字段，这里统计的是全部出货单（管理端录入的通常来自已有 menu_crab 的角色，不会出现在结果里）。
-- 兼容：只用一期已有的表和字段（不依赖 sys_menus.client），上线前后都可执行；MySQL 5.7 / 8.0 均可。
-- 用法：mysql ... 目标库 < crab_users_without_menu_check.sql
-- ============================================================

-- 1) 按有效角色汇总（这就是要填进名单的候选角色）；按出货单数从多到少
SELECT x.effective_role_id                                  AS role_id,
       r.role_name,
       r.is_disabled                                        AS role_disabled,
       COUNT(DISTINCT x.user_id)                            AS user_count,
       COUNT(DISTINCT CASE WHEN x.is_sub = 1 THEN x.user_id END) AS sub_account_count,
       COUNT(*)                                             AS shipment_count,
       MAX(x.create_time)                                   AS last_shipment_time,
       SUBSTRING(GROUP_CONCAT(DISTINCT x.username ORDER BY x.username SEPARATOR ','), 1, 300) AS usernames
FROM (
    SELECT s.shipment_id,
           s.create_time,
           u.id                                                             AS user_id,
           u.username,
           CASE WHEN u.parent_id IS NOT NULL AND u.parent_id <> '' THEN 1 ELSE 0 END AS is_sub,
           CASE WHEN u.parent_id IS NOT NULL AND u.parent_id <> '' THEN p.role_id ELSE u.role_id END AS effective_role_id,
           u.role_id                                                        AS own_role_id
    FROM (
        SELECT c.id AS shipment_id, c.create_time, c.operator_id AS user_id
        FROM crab_shipment c
        WHERE c.operator_id IS NOT NULL AND c.operator_id <> ''
        UNION ALL
        SELECT c.id, c.create_time, MIN(u2.id)
        FROM crab_shipment c
        JOIN sys_users u2
          ON TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u2.username COLLATE utf8mb4_general_ci
          OR TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u2.nickname COLLATE utf8mb4_general_ci
        WHERE (c.operator_id IS NULL OR c.operator_id = '')
          AND c.operator_name IS NOT NULL AND TRIM(c.operator_name) <> ''
        GROUP BY c.id, c.create_time
        HAVING COUNT(DISTINCT u2.id) = 1
    ) s
    JOIN sys_users u ON u.id = s.user_id
    LEFT JOIN sys_users p ON p.id = u.parent_id
) x
LEFT JOIN sys_roles r ON r.id = x.effective_role_id
WHERE x.effective_role_id IS NOT NULL AND x.effective_role_id <> ''
  AND x.effective_role_id <> 'role_regcode_client'
  AND (x.own_role_id IS NULL OR x.own_role_id <> 'role_regcode_client')
  AND x.effective_role_id <> 'role_root'
  AND (r.role_name IS NULL OR r.role_name <> 'ROOT')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.rold_id = x.effective_role_id AND rm.menu_id IN ('menu_crab', 'menu_app_crab'))
GROUP BY x.effective_role_id, r.role_name, r.is_disabled
ORDER BY shipment_count DESC, last_shipment_time DESC;

-- 2) 明细：每个账号（便于逐个确认）。effective_role_id 为空表示子账号的创建人已不存在（上线后会失去出货权限，填名单也救不回来）
SELECT x.user_id,
       x.username,
       x.nickname,
       x.own_role_id,
       x.parent_id,
       x.parent_username,
       x.effective_role_id,
       COUNT(*)           AS shipment_count,
       MAX(x.create_time) AS last_shipment_time,
       CASE WHEN x.effective_role_id IS NULL OR x.effective_role_id = '' THEN '创建人不存在或无角色'
            WHEN EXISTS (SELECT 1 FROM sys_role_menu rm
                         WHERE rm.rold_id = x.effective_role_id AND rm.menu_id IN ('menu_crab', 'menu_app_crab'))
                 THEN '已有出货菜单'
            ELSE '缺出货菜单' END AS status
FROM (
    SELECT s.shipment_id, s.create_time, u.id AS user_id, u.username, u.nickname,
           u.role_id AS own_role_id, u.parent_id, p.username AS parent_username,
           CASE WHEN u.parent_id IS NOT NULL AND u.parent_id <> '' THEN p.role_id ELSE u.role_id END AS effective_role_id
    FROM (
        SELECT c.id AS shipment_id, c.create_time, c.operator_id AS user_id
        FROM crab_shipment c
        WHERE c.operator_id IS NOT NULL AND c.operator_id <> ''
        UNION ALL
        SELECT c.id, c.create_time, MIN(u2.id)
        FROM crab_shipment c
        JOIN sys_users u2
          ON TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u2.username COLLATE utf8mb4_general_ci
          OR TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u2.nickname COLLATE utf8mb4_general_ci
        WHERE (c.operator_id IS NULL OR c.operator_id = '')
          AND c.operator_name IS NOT NULL AND TRIM(c.operator_name) <> ''
        GROUP BY c.id, c.create_time
        HAVING COUNT(DISTINCT u2.id) = 1
    ) s
    JOIN sys_users u ON u.id = s.user_id
    LEFT JOIN sys_users p ON p.id = u.parent_id
) x
WHERE (x.own_role_id IS NULL OR x.own_role_id <> 'role_regcode_client')
  AND (x.effective_role_id IS NULL OR x.effective_role_id NOT IN ('role_regcode_client', 'role_root'))
GROUP BY x.user_id, x.username, x.nickname, x.own_role_id, x.parent_id, x.parent_username, x.effective_role_id
HAVING status <> '已有出货菜单'
ORDER BY shipment_count DESC;

-- 3) 无法归属到账号的出货单数量（operator_id 指向不存在的账号 / 名字匹配不到或匹配到多个），仅供参考
SELECT SUM(CASE WHEN c.operator_id IS NOT NULL AND c.operator_id <> '' AND u.id IS NULL THEN 1 ELSE 0 END) AS operator_id_user_missing,
       SUM(CASE WHEN (c.operator_id IS NULL OR c.operator_id = '') THEN 1 ELSE 0 END)                     AS operator_id_empty,
       COUNT(*)                                                                                           AS total
FROM crab_shipment c
LEFT JOIN sys_users u ON u.id = c.operator_id;
