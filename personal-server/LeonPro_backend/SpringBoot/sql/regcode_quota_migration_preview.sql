-- ============================================================
-- 注册码次数迁移预览（只读，不改任何数据）
-- 背景：旧版次数是“每个注册码用户一个总数”（reg_code_user.generate_limit / generate_used，所有配置共用）；
--       新版按配置分别计次（reg_code_user_config.generate_limit / generate_used）。
--       新后端第一次启动、SchemaPatcher 新建 reg_code_user_config.generate_limit 列时，会自动迁移一次：
--         · 只分配了 1 个配置的用户：原上限、已用原样搬到这个配置上（已用超过上限的按上限算），完全等价；
--         · 分配了多个配置的用户：每个配置都给“原剩余次数”（上限 - 已用，最少 0），已用记 0 —— 总次数会变多；
--         · 没有分配任何配置的用户：没有配置行可以迁移，旧次数不会带过去；
--         · 配置行存在但没有 reg_code_user 记录的：保持 0 / 0。
-- 本脚本只读旧字段，按上面同样的规则算出“迁移后会是什么样”，方便上线前让管理员核对多配置客户多给了多少次。
-- 在新后端第一次启动之前执行（旧字段迁移后不会被改，之后执行也能看到当时的迁移结果，可与新字段实际值对照）。
-- 用法：mysql ... 目标库 < regcode_quota_migration_preview.sql
-- ============================================================

-- 1) 每个注册码用户：旧总数 vs 迁移后各配置合计；over_grant = 迁移后剩余合计 - 旧剩余（多给的次数），按多给的从多到少排
SELECT t.user_id,
       t.username,
       t.nickname,
       t.old_limit,
       t.old_used,
       t.old_remaining,
       t.config_count,
       CASE WHEN t.config_count = 0 THEN 'none：无配置，旧次数不迁移'
            WHEN t.config_count = 1 THEN 'single：原样迁移'
            ELSE 'multi：每个配置给原剩余次数' END                               AS migration_rule,
       CASE WHEN t.config_count = 0 THEN 0
            WHEN t.config_count = 1 THEN GREATEST(t.old_limit, 0)
            ELSE t.config_count * t.old_remaining END                          AS new_total_limit,
       CASE WHEN t.config_count = 1 THEN LEAST(GREATEST(t.old_used, 0), GREATEST(t.old_limit, 0))
            ELSE 0 END                                                         AS new_total_used,
       t.config_count * t.old_remaining                                        AS new_total_remaining,
       t.config_count * t.old_remaining - t.old_remaining                      AS over_grant
FROM (
    SELECT u.user_id,
           su.username,
           su.nickname,
           IFNULL(u.generate_limit, 0)                                           AS old_limit,
           IFNULL(u.generate_used, 0)                                            AS old_used,
           GREATEST(IFNULL(u.generate_limit, 0) - IFNULL(u.generate_used, 0), 0) AS old_remaining,
           IFNULL(c.cfg_rows, 0)                                                 AS config_count
    FROM reg_code_user u
    LEFT JOIN sys_users su ON su.id = u.user_id
    LEFT JOIN (SELECT user_id, COUNT(*) AS cfg_rows FROM reg_code_user_config GROUP BY user_id) c ON c.user_id = u.user_id
) t
ORDER BY over_grant DESC, t.old_remaining DESC, t.username;
-- 说明：config_count = 0 时 over_grant 为负数（= -旧剩余），表示这部分旧次数不会带过去。

-- 2) 迁移后每个配置行的次数（与 SchemaPatcher 的两条 UPDATE 完全同一规则）
SELECT c.user_id,
       su.username,
       c.config_id,
       CASE WHEN rc.id IS NULL THEN '（配置已不存在）'
            WHEN IFNULL(TRIM(rc.company), '') = '' THEN TRIM(rc.name)
            WHEN IFNULL(TRIM(rc.name), '') = '' THEN TRIM(rc.company)
            ELSE CONCAT(TRIM(rc.company), ' / ', TRIM(rc.name)) END            AS config_name,
       n.cfg_rows                                                            AS user_config_count,
       CASE WHEN u.user_id IS NULL THEN 0
            WHEN n.cfg_rows = 1 THEN GREATEST(IFNULL(u.generate_limit, 0), 0)
            ELSE GREATEST(IFNULL(u.generate_limit, 0) - IFNULL(u.generate_used, 0), 0) END AS new_limit,
       CASE WHEN u.user_id IS NOT NULL AND n.cfg_rows = 1
            THEN LEAST(GREATEST(IFNULL(u.generate_used, 0), 0), GREATEST(IFNULL(u.generate_limit, 0), 0))
            ELSE 0 END                                                       AS new_used,
       CASE WHEN u.user_id IS NULL THEN 0
            ELSE GREATEST(IFNULL(u.generate_limit, 0) - IFNULL(u.generate_used, 0), 0) END AS new_remaining,
       CASE WHEN u.user_id IS NULL THEN '无 reg_code_user 记录，保持 0 / 0' ELSE '' END AS note
FROM reg_code_user_config c
JOIN (SELECT user_id, COUNT(*) AS cfg_rows FROM reg_code_user_config GROUP BY user_id) n ON n.user_id = c.user_id
LEFT JOIN reg_code_user u ON u.user_id = c.user_id
LEFT JOIN sys_users su ON su.id = c.user_id
LEFT JOIN reg_code_config rc ON rc.id = c.config_id
ORDER BY su.username, c.user_id, config_name;

-- 3) 汇总：多配置客户数、合计多给的次数；无配置但还有剩余次数的客户数（这部分次数不会迁移）
SELECT SUM(CASE WHEN c.cfg_rows > 1 THEN 1 ELSE 0 END) AS multi_config_users,
       SUM(CASE WHEN c.cfg_rows > 1
                THEN (c.cfg_rows - 1) * GREATEST(IFNULL(u.generate_limit, 0) - IFNULL(u.generate_used, 0), 0)
                ELSE 0 END)                           AS total_over_grant,
       SUM(CASE WHEN IFNULL(c.cfg_rows, 0) = 0
                 AND IFNULL(u.generate_limit, 0) > IFNULL(u.generate_used, 0) THEN 1 ELSE 0 END) AS no_config_users_with_remaining,
       SUM(CASE WHEN IFNULL(c.cfg_rows, 0) = 0
                THEN GREATEST(IFNULL(u.generate_limit, 0) - IFNULL(u.generate_used, 0), 0)
                ELSE 0 END)                           AS remaining_not_migrated
FROM reg_code_user u
LEFT JOIN (SELECT user_id, COUNT(*) AS cfg_rows FROM reg_code_user_config GROUP BY user_id) c ON c.user_id = u.user_id;
