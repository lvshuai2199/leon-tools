package springboot.DTO;

import lombok.Data;

import java.util.List;

/** 批量操作图片：action = enable | disable | move | delete；move 需 targetGroupId */
@Data
public class WallpaperBatchRequest {
    private List<String> ids;
    private String action;
    private String targetGroupId;
}
