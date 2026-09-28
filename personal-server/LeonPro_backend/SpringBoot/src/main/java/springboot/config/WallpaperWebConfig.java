package springboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springboot.service.WallpaperStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 壁纸静态资源：{app.wallpaper.url-prefix}/** -> {app.wallpaper.storage-dir}/。
 * 文件名为随机 UUID，替换图片会生成新文件名，因此可长期缓存。
 */
@Slf4j
@Configuration
public class WallpaperWebConfig implements WebMvcConfigurer {

    private final WallpaperStorage storage;

    public WallpaperWebConfig(WallpaperStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = storage.baseDir();
        try {
            Files.createDirectories(base);
        } catch (Exception e) {
            log.warn("创建壁纸目录失败 {}: {}", base, e.getMessage());
        }
        String location = base.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(storage.urlPrefix() + "/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
        log.info("壁纸静态资源 {}/** -> {}", storage.urlPrefix(), location);
    }
}
