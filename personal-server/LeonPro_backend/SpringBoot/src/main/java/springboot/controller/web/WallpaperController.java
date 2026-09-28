package springboot.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import springboot.DTO.WallpaperBatchRequest;
import springboot.DTO.WallpaperGroupForm;
import springboot.DTO.WallpaperImageUpdateRequest;
import springboot.service.SysUsersService;
import springboot.service.WallpaperService;
import springboot.utils.ApiResponse;
import springboot.utils.RequestUserUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 壁纸管理（后台）：分组与图片的增删改查、上传、排序、批量操作。
 * 需登录：AuthInterceptor 校验 Authorization: Bearer {token} 后写入当前用户。
 */
@RestController
public class WallpaperController {

    private final WallpaperService wallpaperService;
    private final SysUsersService sysUsersService;

    public WallpaperController(WallpaperService wallpaperService, SysUsersService sysUsersService) {
        this.wallpaperService = wallpaperService;
        this.sysUsersService = sysUsersService;
    }

    // ------------------------------------------------------------------ 分组

    @GetMapping("/admin/wallpaper/group/list")
    public ApiResponse listGroups(HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        return ApiResponse.success(wallpaperService.listGroups());
    }

    @PostMapping("/admin/wallpaper/group")
    public ApiResponse createGroup(@RequestBody WallpaperGroupForm form, HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.createGroup(form));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PutMapping("/admin/wallpaper/group/sort")
    public ApiResponse sortGroups(@RequestBody List<String> ids, HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.sortGroups(ids));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PutMapping("/admin/wallpaper/group/{id}")
    public ApiResponse updateGroup(@PathVariable String id, @RequestBody WallpaperGroupForm form,
                                   HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.updateGroup(id, form));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    /** 分组下有图片时需 force=true，会连同图片记录和文件一起删除；否则返回 status=409 + imageCount */
    @DeleteMapping("/admin/wallpaper/group/{id}")
    public ApiResponse deleteGroup(@PathVariable String id,
                                   @RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                   HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        if (wallpaperService.getGroup(id) == null) {
            return ApiResponse.failure("分组不存在");
        }
        long count = wallpaperService.countImages(id);
        if (count > 0 && !force) {
            return ApiResponse.withStatus(409, "分组下还有 " + count + " 张图片，确认删除请传 force=true",
                    Map.of("imageCount", count));
        }
        try {
            wallpaperService.deleteGroup(id);
            return ApiResponse.success(Map.of("deletedImages", count));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    /** 重新生成分组访问令牌，data 为新 token 字符串；旧 token 立即失效 */
    @PostMapping("/admin/wallpaper/group/{id}/token/regenerate")
    public ApiResponse regenerateToken(@PathVariable String id, HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.regenerateToken(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 图片

    /** 分页参数 current/size（与项目其他分页一致），返回 IPageResult（MyBatis-Plus Page：records/total/size/current/pages） */
    @GetMapping("/admin/wallpaper/image/page")
    public ApiResponse pageImages(@RequestParam(value = "groupId", required = false) String groupId,
                                  @RequestParam(value = "current", required = false, defaultValue = "1") long current,
                                  @RequestParam(value = "size", required = false, defaultValue = "20") long size,
                                  HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        return ApiResponse.success(wallpaperService.pageImages(groupId, current, size, false));
    }

    @PostMapping("/admin/wallpaper/image/upload")
    public ApiResponse upload(@RequestParam("groupId") String groupId,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "title", required = false) String title,
                              HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.upload(groupId, file, title));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        } catch (IOException e) {
            return ApiResponse.failure("保存图片失败：" + e.getMessage());
        }
    }

    @PutMapping("/admin/wallpaper/image/sort")
    public ApiResponse sortImages(@RequestBody List<String> ids, HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.sortImages(ids));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PutMapping("/admin/wallpaper/image/{id}")
    public ApiResponse updateImage(@PathVariable String id, @RequestBody WallpaperImageUpdateRequest req,
                                   HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.updateImage(id, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("/admin/wallpaper/image/{id}/replace")
    public ApiResponse replace(@PathVariable String id, @RequestParam("file") MultipartFile file,
                               HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.replace(id, file));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        } catch (IOException e) {
            return ApiResponse.failure("保存图片失败：" + e.getMessage());
        }
    }

    /** body: {ids:[], action: enable|disable|move|delete, targetGroupId}；返回受影响条数 */
    @PostMapping("/admin/wallpaper/image/batch")
    public ApiResponse batch(@RequestBody WallpaperBatchRequest req, HttpServletRequest request) {
        ApiResponse deny = denyUnlessLogin(request);
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(wallpaperService.batch(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        } catch (IOException e) {
            return ApiResponse.failure("移动文件失败：" + e.getMessage());
        }
    }

    /** 兜底：拦截器已校验 token，这里再确认 token 对应的用户仍存在 */
    private ApiResponse denyUnlessLogin(HttpServletRequest request) {
        String userId = RequestUserUtils.currentUserId(request);
        if (userId == null || sysUsersService.getById(userId) == null) {
            return ApiResponse.withStatus(401, "请先登录", null);
        }
        return null;
    }
}
