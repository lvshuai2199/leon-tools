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
