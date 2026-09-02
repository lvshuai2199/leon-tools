package springboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import springboot.service.SystemDataPackService;
import springboot.service.impl.SystemDataPackServiceImpl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 启动时导入随代码部署的 system-data.zip（思维导图图片 + 注册码配置）。
 *
 * 查找顺序：
 * 1. classpath: seed/system-data.zip（推荐：放到 src/main/resources/seed/system-data.zip）
 * 2. 运行目录 seed/system-data.zip
 */
@Slf4j
@Component
@Order(10)
public class SystemDataPackSeeder implements CommandLineRunner {

    private final SystemDataPackService systemDataPackService;

    public SystemDataPackSeeder(SystemDataPackService systemDataPackService) {
        this.systemDataPackService = systemDataPackService;
    }

    @Override
    public void run(String... args) {
        ClassPathResource cp = new ClassPathResource(SystemDataPackServiceImpl.CLASSPATH_PACK);
        if (cp.exists()) {
            try (InputStream in = cp.getInputStream()) {
                log.info("发现 classpath 数据包 {}，开始导入。", SystemDataPackServiceImpl.CLASSPATH_PACK);
                systemDataPackService.importPack(in);
            } catch (Exception e) {
                log.error("导入 classpath 系统数据包失败", e);
            }
            return;
        }
        Path disk = Paths.get(SystemDataPackServiceImpl.DISK_PACK_RELATIVE);
        if (Files.isRegularFile(disk)) {
            try (InputStream in = Files.newInputStream(disk)) {
                log.info("发现磁盘数据包 {}，开始导入。", disk.toAbsolutePath());
                systemDataPackService.importPack(in);
            } catch (Exception e) {
                log.error("导入磁盘系统数据包失败", e);
            }
        }
    }
}
