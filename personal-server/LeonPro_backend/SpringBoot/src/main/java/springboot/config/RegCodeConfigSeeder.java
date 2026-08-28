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
        if (this.regCodeConfigService.count() > 0) {
            return;
        }
        Date now = DateUtils.getNow();
        List<RegCodeConfig> rows = new ArrayList<>();
        rows.add(row("rcc_weld", "通用", "焊接专机", "weld", "MD5", "auboweld", 1, now));
        rows.add(row("rcc_pallet", "通用", "码垛专机", "pallet", "MD5", "aubo", 2, now));
        rows.add(row("rcc_youbo", "友博", "CNC插件", "cnc", "MD5", "youbo_leon", 3, now));
        this.regCodeConfigService.saveBatch(rows);
        log.info("reg_code_config 为空，已初始化 {} 条默认配置。", rows.size());
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
