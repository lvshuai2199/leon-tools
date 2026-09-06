package springboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import springboot.domain.RegCodeConfig;
import springboot.service.RegCodeConfigService;
import springboot.utils.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 注册码配置种子：空表时写入通用焊接/码垛、友博 CNC。
 */
@Slf4j
@Component
@Order(2)
public class RegCodeConfigSeeder implements CommandLineRunner {

    private final RegCodeConfigService regCodeConfigService;

    public RegCodeConfigSeeder(RegCodeConfigService regCodeConfigService) {
        this.regCodeConfigService = regCodeConfigService;
    }

    @Override
    public void run(String... args) {
        Date now = DateUtils.getNow();
        if (this.regCodeConfigService.count() == 0) {
            List<RegCodeConfig> rows = new ArrayList<>();
            rows.add(row("rcc_weld", "通用", "焊接专机", "weld", "MD5", "auboweld", 1, now));
            rows.add(row("rcc_pallet", "通用", "码垛专机", "pallet", "MD5", "aubo", 2, now));
            rows.add(row("rcc_youbo", "友博", "CNC插件", "cnc", "MD5", "youbo_leon", 3, now));
            rows.add(row("rcc_youbo_pe", "友博", "PE插件", "pe", "SHA-256", "youbo_leon", 4, now));
            this.regCodeConfigService.saveBatch(rows);
            log.info("reg_code_config 为空，已初始化 {} 条默认配置。", rows.size());
            return;
        }
        ensureYouboPe(now);
    }

    /** 友博 PE 插件：与上下料小工具相同，原文 + youbo_leon，再 SHA-256 截取有效期 */
    private void ensureYouboPe(Date now) {
        if (this.regCodeConfigService.getById("rcc_youbo_pe") != null) {
            return;
        }
        this.regCodeConfigService.save(row("rcc_youbo_pe", "友博", "PE插件", "pe", "SHA-256", "youbo_leon", 4, now));
        log.info("已补插注册码配置 rcc_youbo_pe（友博 / PE插件 / SHA-256）。");
    }

    private RegCodeConfig row(String id, String company, String name, String componentName,
                              String encryptType, String encryptSuffix, int sortOrder, Date now) {
        RegCodeConfig item = new RegCodeConfig();
        item.setId(id);
        item.setCompany(company);
        item.setName(name);
        item.setComponentName(componentName);
        item.setEncryptType(encryptType);
        item.setEncryptSuffix(encryptSuffix);
        item.setSortOrder(sortOrder);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        return item;
    }
}
