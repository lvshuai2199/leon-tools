package springboot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import springboot.domain.WallpaperImage;

import java.util.Collection;
import java.util.Map;

public interface WallpaperImageService extends IService<WallpaperImage> {

    /** 按分组统计图片数；onlyEnabled=true 时只统计启用图片 */
    Map<String, Long> countByGroup(Collection<String> groupIds, boolean onlyEnabled);

    /** 分组内当前最大 sort，无图片时返回 0 */
    int maxSort(String groupId);
}
