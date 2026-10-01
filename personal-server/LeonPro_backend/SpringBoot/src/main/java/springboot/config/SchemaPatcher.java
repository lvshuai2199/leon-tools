package springboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 存量库结构补齐：为已存在的表补充新增列，避免每次手工 ALTER。
 */
@Slf4j
@Component
@Order(0)
public class SchemaPatcher implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaPatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureColumn("com_registration", "operator",
                "ALTER TABLE com_registration ADD COLUMN operator VARCHAR(100) DEFAULT NULL COMMENT '操作人员（用户ID，无则未知人员）'");
        ensureColumn("sys_users", "parent_id",
                "ALTER TABLE sys_users ADD COLUMN parent_id VARCHAR(64) DEFAULT NULL COMMENT '父用户ID，空表示主用户'");
        ensureRegCodeConfigTable();
        ensureRegCodeUserTables();
        ensureToolMindmapTable();
        ensureCrabShipmentTable();
        ensureWallpaperTables();
        ensureBadmintonTables();
    }

    /** 羽毛球计费表，与 sql/badminton_module.sql 保持一致 */
    private void ensureBadmintonTables() {
        ensureTable("badminton_bill",
                "CREATE TABLE `badminton_bill` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`play_date` VARCHAR(10) NOT NULL COMMENT '球局日期 yyyy-MM-dd',"
                        + "`title` VARCHAR(100) DEFAULT NULL COMMENT '标题',"
                        + "`participant_count` INT DEFAULT 1 COMMENT '参与人数',"
                        + "`court_total` DECIMAL(12,2) DEFAULT 0.00 COMMENT '场地费合计',"
                        + "`ball_total` DECIMAL(12,2) DEFAULT 0.00 COMMENT '用球费用合计',"
                        + "`grand_total` DECIMAL(12,2) DEFAULT 0.00 COMMENT '总费用',"
                        + "`per_person` DECIMAL(12,2) DEFAULT 0.00 COMMENT '个人应付',"
                        + "`remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',"
                        + "`operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',"
                        + "`operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                        + "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        + "PRIMARY KEY (`id`),"
                        + "KEY `idx_badminton_bill_date` (`play_date`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='羽毛球球局计费'");
        ensureTable("badminton_court_fee",
                "CREATE TABLE `badminton_court_fee` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`bill_id` VARCHAR(64) NOT NULL COMMENT '球局ID',"
                        + "`court_count` INT DEFAULT 1 COMMENT '场地数量（片）',"
                        + "`hours` DECIMAL(10,2) DEFAULT 0.00 COMMENT '时长（小时）',"
                        + "`unit_price` DECIMAL(12,2) DEFAULT 0.00 COMMENT '单价（元/片/小时）',"
                        + "`amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '小计',"
                        + "`remark` VARCHAR(100) DEFAULT NULL COMMENT '备注（场地名/时段）',"
                        + "`sort_order` INT DEFAULT 0 COMMENT '排序',"
                        + "PRIMARY KEY (`id`),"
                        + "KEY `idx_badminton_court_bill` (`bill_id`, `sort_order`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='羽毛球场地费明细'");
        ensureTable("badminton_ball_fee",
                "CREATE TABLE `badminton_ball_fee` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`bill_id` VARCHAR(64) NOT NULL COMMENT '球局ID',"
                        + "`brand` VARCHAR(50) DEFAULT NULL COMMENT '用球品牌',"
                        + "`quantity` INT DEFAULT 0 COMMENT '数量',"
                        + "`unit_price` DECIMAL(12,2) DEFAULT 0.00 COMMENT '单价',"
                        + "`amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '小计',"
                        + "`sort_order` INT DEFAULT 0 COMMENT '排序',"
                        + "PRIMARY KEY (`id`),"
                        + "KEY `idx_badminton_ball_bill` (`bill_id`, `sort_order`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='羽毛球用球费用明细'");
    }

    /** 壁纸模块表，与 sql/wallpaper_module.sql 保持一致 */
    private void ensureWallpaperTables() {
        ensureTable("wallpaper_group",
                "CREATE TABLE `wallpaper_group` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`name` VARCHAR(100) NOT NULL COMMENT '分组名称',"
                        + "`group_key` VARCHAR(64) NOT NULL COMMENT '分组标识（小写字母/数字/-），外部接口按此取图',"
                        + "`description` VARCHAR(500) DEFAULT NULL COMMENT '描述',"
                        + "`sort` INT DEFAULT 0 COMMENT '排序（升序）',"
                        + "`is_public` INT DEFAULT 1 COMMENT '是否公开 1是 0否',"
                        + "`access_token` VARCHAR(128) DEFAULT NULL COMMENT '访问令牌（创建分组时自动生成，外部随机接口必须携带）',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                        + "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        + "PRIMARY KEY (`id`),"
                        + "UNIQUE KEY `uk_wallpaper_group_key` (`group_key`),"
                        + "KEY `idx_wallpaper_group_sort` (`sort`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='壁纸分组'");
        ensureTable("wallpaper_image",
                "CREATE TABLE `wallpaper_image` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`group_id` VARCHAR(64) NOT NULL COMMENT '分组ID',"
                        + "`title` VARCHAR(200) DEFAULT NULL COMMENT '标题',"
                        + "`file_path` VARCHAR(500) NOT NULL COMMENT '原图相对路径（相对壁纸存储目录，如 {groupId}/{uuid}.jpg）',"
                        + "`thumb_path` VARCHAR(500) DEFAULT NULL COMMENT '缩略图相对路径',"
                        + "`width` INT DEFAULT NULL COMMENT '原图宽',"
                        + "`height` INT DEFAULT NULL COMMENT '原图高',"
                        + "`file_size` BIGINT DEFAULT NULL COMMENT '原图字节数',"
                        + "`sort` INT DEFAULT 0 COMMENT '组内排序（升序）',"
                        + "`enabled` INT DEFAULT 1 COMMENT '是否启用 1是 0否',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                        + "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        + "PRIMARY KEY (`id`),"
                        + "KEY `idx_wallpaper_image_group` (`group_id`, `enabled`, `sort`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='壁纸图片'");
    }

    private void ensureRegCodeConfigTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                "reg_code_config");
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(
                "CREATE TABLE `reg_code_config` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`company` VARCHAR(100) DEFAULT NULL COMMENT '公司',"
                        + "`name` VARCHAR(100) DEFAULT NULL COMMENT '名称',"
                        + "`component_name` VARCHAR(100) DEFAULT NULL COMMENT '组件名称',"
                        + "`encrypt_type` VARCHAR(50) DEFAULT 'MD5' COMMENT '加密方式',"
                        + "`encrypt_suffix` VARCHAR(200) DEFAULT NULL COMMENT '加密字符后缀',"
                        + "`sort_order` INT DEFAULT 0 COMMENT '排序',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                        + "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册码生成配置'");
        log.info("已创建表 reg_code_config。");
    }

    private void ensureRegCodeUserTables() {
        ensureTable("reg_code_user",
                "CREATE TABLE `reg_code_user` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`user_id` VARCHAR(64) NOT NULL COMMENT '系统用户ID',"
                        + "`generate_limit` INT DEFAULT 0 COMMENT '可生成次数',"
                        + "`generate_used` INT DEFAULT 0 COMMENT '已使用次数',"
                        + "`remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                        + "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        + "PRIMARY KEY (`id`),"
                        + "UNIQUE KEY `uk_reg_code_user_id` (`user_id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册码客户账号'");
        ensureTable("reg_code_user_config",
                "CREATE TABLE `reg_code_user_config` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`user_id` VARCHAR(64) NOT NULL COMMENT '系统用户ID',"
                        + "`config_id` VARCHAR(64) NOT NULL COMMENT '注册码配置ID',"
                        + "PRIMARY KEY (`id`),"
                        + "KEY `idx_reg_code_user_config_user` (`user_id`),"
                        + "KEY `idx_reg_code_user_config_cfg` (`config_id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册码客户可用配置'");
    }

    private void ensureTable(String table, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                table);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("已创建表 {}。", table);
    }

    private void ensureToolMindmapTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                "tool_mindmap");
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(
                "CREATE TABLE `tool_mindmap` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`title` VARCHAR(200) DEFAULT NULL COMMENT '标题',"
                        + "`markdown` MEDIUMTEXT COMMENT 'Markdown 源码',"
                        + "`public_id` VARCHAR(64) NOT NULL COMMENT '对外访问标识',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                        + "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        + "PRIMARY KEY (`id`),"
                        + "UNIQUE KEY `uk_tool_mindmap_public_id` (`public_id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='思维导图存储'");
        log.info("已创建表 tool_mindmap。");
    }

    private void ensureCrabShipmentTable() {
        ensureTable("crab_shipment",
                "CREATE TABLE `crab_shipment` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`seq_no` INT DEFAULT NULL COMMENT '当天序号',"
                        + "`customer_name` VARCHAR(100) DEFAULT NULL COMMENT '姓名',"
                        + "`phone` VARCHAR(50) DEFAULT NULL COMMENT '电话',"
                        + "`address` VARCHAR(500) DEFAULT NULL COMMENT '地址',"
                        + "`spec` VARCHAR(100) DEFAULT NULL COMMENT '规格',"
                        + "`quantity` INT DEFAULT NULL COMMENT '数量（只）',"
                        + "`paid` INT DEFAULT 0 COMMENT '是否已付款 0否 1是',"
                        + "`shipped` INT DEFAULT 0 COMMENT '是否已发货 0否 1是',"
                        + "`tracking_no` VARCHAR(100) DEFAULT NULL COMMENT '发货单号',"
                        + "`ship_date` VARCHAR(10) DEFAULT NULL COMMENT '出货日期 yyyy-MM-dd',"
                        + "`remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',"
                        + "`public_id` VARCHAR(64) NOT NULL COMMENT '对外分享标识',"
                        + "`operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',"
                        + "`operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                        + "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        + "PRIMARY KEY (`id`),"
                        + "UNIQUE KEY `uk_crab_shipment_public_id` (`public_id`),"
                        + "KEY `idx_crab_shipment_date` (`ship_date`),"
                        + "KEY `idx_crab_shipment_phone` (`phone`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='螃蟹每日出货'");
    }

    private void ensureOperationLogTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                "sys_operation_log");
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(
                "CREATE TABLE `sys_operation_log` ("
                        + "`id` VARCHAR(64) NOT NULL COMMENT '主键',"
                        + "`operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',"
                        + "`operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人用户名',"
                        + "`module` VARCHAR(50) DEFAULT NULL COMMENT '业务模块',"
                        + "`action` VARCHAR(200) DEFAULT NULL COMMENT '动作摘要',"
                        + "`request_method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP方法',"
                        + "`request_uri` VARCHAR(500) DEFAULT NULL COMMENT '请求路径',"
                        + "`request_params` TEXT COMMENT '脱敏后的请求参数',"
                        + "`ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',"
                        + "`user_agent` VARCHAR(500) DEFAULT NULL COMMENT '浏览器标识',"
                        + "`status` VARCHAR(20) DEFAULT NULL COMMENT 'SUCCESS/FAIL/ERROR',"
                        + "`result_msg` VARCHAR(500) DEFAULT NULL COMMENT '结果摘要',"
                        + "`error_msg` TEXT COMMENT '失败或异常详情',"
                        + "`cost_ms` BIGINT DEFAULT NULL COMMENT '耗时毫秒',"
                        + "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',"
                        + "PRIMARY KEY (`id`),"
                        + "KEY `idx_oplog_create_time` (`create_time`),"
                        + "KEY `idx_oplog_operator_name` (`operator_name`),"
                        + "KEY `idx_oplog_status` (`status`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志'");
        log.info("已创建表 sys_operation_log。");
    }

    private void ensureColumn(String table, String column, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column);
        if (count != null && count == 0) {
            jdbcTemplate.execute(alterSql);
            log.info("已为 {}.{} 补齐字段。", table, column);
        }
    }
}
