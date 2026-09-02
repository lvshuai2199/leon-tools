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
@Order(1)
public class SchemaPatcher implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaPatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureColumn("com_registration", "operator",
                "ALTER TABLE com_registration ADD COLUMN operator VARCHAR(100) DEFAULT NULL COMMENT '操作人员（用户ID，无则未知人员）'");
        ensureRegCodeConfigTable();
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
