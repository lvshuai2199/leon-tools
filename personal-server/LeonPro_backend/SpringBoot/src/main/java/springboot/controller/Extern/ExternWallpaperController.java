package springboot.controller.Extern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.DTO.WallpaperImageVO;
import springboot.domain.WallpaperGroup;
import springboot.service.WallpaperService;
import springboot.utils.ApiResponse;

import java.util.Collections;

/**
 * 壁纸外部接口（免登录）。
 * groups / images 供门户浏览，不需要也不返回 token；
 * random 供外部小工具取图，必须带 group + token（分组访问令牌）。
 */
@RestController
public class ExternWallpaperController {

    private final WallpaperService wallpaperService;

    public ExternWallpaperController(WallpaperService wallpaperService) {
        this.wallpaperService = wallpaperService;
    }

    /** 公开分组列表（imageCount 只算启用图片；coverUrl 取排序第一张启用图片） */
    @GetMapping("/extern/wallpaper/groups")
    public ApiResponse groups() {
        return ApiResponse.success(wallpaperService.publicGroups());
    }

    /** 公开分组内的启用图片，分页参数 current/size，返回 IPageResult */
    @GetMapping("/extern/wallpaper/images")
    public ResponseEntity<ApiResponse> images(@RequestParam(value = "group", required = false) String group,
                                              @RequestParam(value = "current", required = false, defaultValue = "1") long current,
                                              @RequestParam(value = "size", required = false, defaultValue = "20") long size) {
        WallpaperGroup g = wallpaperService.findPublicGroup(group);
        if (g == null) {
            return error(HttpStatus.NOT_FOUND, "分组不存在或未公开");
        }
        return ResponseEntity.ok(ApiResponse.success(
                wallpaperService.pageImages(g.getId(), current, Math.min(size, 100), true)));
    }

    /**
     * 随机一张启用图片，必须带 group + token。默认 302 跳转原图（Location 为 app.wallpaper.redirect-prefix +
     * /uploads/wallpaper/...）；format=json 返回图片信息，url / thumbUrl 同样带该前缀。
     * 分组不存在 / 未公开 / 无启用图片 → 404；token 缺失或错误 → 403。
     */
    @GetMapping("/extern/wallpaper/random")
    public ResponseEntity<?> random(@RequestParam(value = "group", required = false) String group,
                                    @RequestParam(value = "token", required = false) String token,
                                    @RequestParam(value = "format", required = false) String format) {
        WallpaperGroup g = wallpaperService.findPublicGroup(group);
        if (g == null) {
            return error(HttpStatus.NOT_FOUND, "分组不存在或未公开");
        }
        if (!wallpaperService.tokenMatches(g, token)) {
            return error(HttpStatus.FORBIDDEN, "token 缺失或无效");
        }
        WallpaperImageVO img = wallpaperService.randomImage(Collections.singletonList(g.getId()));
        if (img == null) {
            return error(HttpStatus.NOT_FOUND, "分组内没有可用的壁纸");
        }
        if ("json".equalsIgnoreCase(format == null ? null : format.trim())) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResponse.success(img));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.LOCATION, img.getUrl())
                .build();
    }

    private static ResponseEntity<ApiResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.withStatus(status.value(), message, null));
    }
}
