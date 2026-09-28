-- ============================================================
-- 螃蟹出货 operator_id 回填前检查（只读，不改任何数据）
-- 背景：用户端螃蟹出货按 operator_id 限定数据范围（主账号看自己 + 名下子账号，子账号只看自己）。
--       operator_id 为空的旧记录在用户端谁都看不到（ROOT 与管理端不受影响），
--       需先用 crab_operator_id_backfill.sql 按 operator_name 回填。
-- 匹配规则与回填脚本一致：TRIM(operator_name) 等于 sys_users.username 或 nickname（不区分大小写），
--       且只匹配到唯一一个用户时才算“可回填”。
-- 用法：mysql ... 目标库 < crab_operator_id_check.sql
-- ============================================================

-- 1) 总数 / operator_id 为空的数量
SELECT COUNT(*) AS total_rows,
       SUM(CASE WHEN operator_id IS NULL OR operator_id = '' THEN 1 ELSE 0 END) AS missing_operator_id
FROM crab_shipment;

-- 2) operator_id 为空的记录分类：可唯一匹配 / 匹配到多个用户（歧义）/ 匹配不到（含 operator_name 为空）
SELECT SUM(CASE WHEN t.match_cnt = 1 THEN 1 ELSE 0 END) AS unique_match,
       SUM(CASE WHEN t.match_cnt > 1 THEN 1 ELSE 0 END) AS ambiguous,
       SUM(CASE WHEN t.match_cnt = 0 THEN 1 ELSE 0 END) AS unmatched
FROM (
    SELECT c.id, COUNT(DISTINCT u.id) AS match_cnt
    FROM crab_shipment c
    LEFT JOIN sys_users u
           ON TRIM(c.operator_name) <> ''
          AND (TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u.username COLLATE utf8mb4_general_ci
               OR TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u.nickname COLLATE utf8mb4_general_ci)
    WHERE c.operator_id IS NULL OR c.operator_id = ''
    GROUP BY c.id
) t;

-- 3) 按 operator_name 汇总明细：每个名字多少条、匹配到几个用户、匹配到的用户
SELECT TRIM(c.operator_name)                       AS operator_name,
       COUNT(DISTINCT c.id)                        AS rows_cnt,
       COUNT(DISTINCT u.id)                        AS matched_users,
       GROUP_CONCAT(DISTINCT CONCAT(u.username, '(', u.id, ')') SEPARATOR ', ') AS candidates
FROM crab_shipment c
LEFT JOIN sys_users u
       ON TRIM(c.operator_name) <> ''
      AND (TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u.username COLLATE utf8mb4_general_ci
           OR TRIM(c.operator_name) COLLATE utf8mb4_general_ci = u.nickname COLLATE utf8mb4_general_ci)
WHERE c.operator_id IS NULL OR c.operator_id = ''
GROUP BY TRIM(c.operator_name)
ORDER BY rows_cnt DESC;

-- 4) 已有 operator_id 但对应用户已不存在（孤儿），这些记录在用户端同样没人能看到
SELECT c.operator_id, MAX(c.operator_name) AS operator_name, COUNT(*) AS rows_cnt
FROM crab_shipment c
LEFT JOIN sys_users u ON u.id = c.operator_id
WHERE c.operator_id IS NOT NULL AND c.operator_id <> '' AND u.id IS NULL
GROUP BY c.operator_id
ORDER BY rows_cnt DESC;
