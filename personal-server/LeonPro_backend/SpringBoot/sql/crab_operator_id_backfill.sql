-- ============================================================
-- 螃蟹出货 operator_id 回填（按 operator_name 匹配用户）
-- 只处理 operator_id 为空（NULL 或 ''）的记录；TRIM(operator_name) 等于 sys_users.username 或 nickname
-- （不区分大小写）且只匹配到唯一一个用户时才回填，歧义和匹配不到的记录保持不变。
-- 可重复执行：已回填的记录 operator_id 不再为空，不会被再次修改。
-- 不改 update_time（显式写回原值，避免 ON UPDATE CURRENT_TIMESTAMP 刷新分享页上的更新时间）。
-- 执行前建议先跑 crab_operator_id_check.sql 看数量，并备份 crab_shipment。
-- 后端不会自动执行本脚本。
-- ============================================================

START TRANSACTION;

UPDATE crab_shipment c
JOIN (
    SELECT c2.id AS crab_id, MIN(u.id) AS user_id
    FROM crab_shipment c2
    JOIN sys_users u
      ON TRIM(c2.operator_name) COLLATE utf8mb4_general_ci = u.username COLLATE utf8mb4_general_ci
      OR TRIM(c2.operator_name) COLLATE utf8mb4_general_ci = u.nickname COLLATE utf8mb4_general_ci
    WHERE (c2.operator_id IS NULL OR c2.operator_id = '')
      AND c2.operator_name IS NOT NULL
      AND TRIM(c2.operator_name) <> ''
    GROUP BY c2.id
    HAVING COUNT(DISTINCT u.id) = 1
) m ON m.crab_id = c.id
SET c.operator_id = m.user_id,
    c.update_time = c.update_time
WHERE c.operator_id IS NULL OR c.operator_id = '';

SELECT ROW_COUNT() AS backfilled_rows;

-- 回填后仍为空的数量（歧义 / 匹配不到，需人工处理）
SELECT COUNT(*) AS still_missing
FROM crab_shipment
WHERE operator_id IS NULL OR operator_id = '';

COMMIT;
