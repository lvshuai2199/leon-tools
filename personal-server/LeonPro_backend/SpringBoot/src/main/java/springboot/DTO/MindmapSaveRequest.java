package springboot.DTO;

import lombok.Data;

/**
 * 保存思维导图：标题、Markdown，以及 PNG 的 Base64。
 */
@Data
public class MindmapSaveRequest {

    /** 有值则更新已有记录 */
    private String id;

    private String title;

    private String markdown;

    /** 纯 Base64 或 data:image/png;base64,... */
    private String imageBase64;
}
