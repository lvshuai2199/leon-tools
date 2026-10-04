-- ============================================================
-- 羽毛球计费：用球明细加「整桶价格」（2026-10，feat/badminton-name-bucket）
-- 只加一列可为空的 bucket_price，老数据不动（老记录 bucket_price = NULL，照旧按单价算）。
-- 后端启动时 SchemaPatcher 也会自动补这一列；手动执行前先确认列不存在：
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'badminton_ball_fee' AND COLUMN_NAME = 'bucket_price';
-- 回滚（确认没有要保留的整桶价后）：ALTER TABLE badminton_ball_fee DROP COLUMN bucket_price;
-- ============================================================

ALTER TABLE `badminton_ball_fee`
  ADD COLUMN `bucket_price` DECIMAL(10,2) NULL DEFAULT NULL COMMENT '整桶价格（12个），空=没填' AFTER `unit_price`;
