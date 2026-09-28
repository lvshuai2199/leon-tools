package springboot.DTO;

import lombok.Data;

/** 修改图片：title / enabled 任填其一，null 表示不修改；enabled 接受 true/false 或 1/0 */
@Data
public class WallpaperImageUpdateRequest {
    private String title;
    private Object enabled;
}
