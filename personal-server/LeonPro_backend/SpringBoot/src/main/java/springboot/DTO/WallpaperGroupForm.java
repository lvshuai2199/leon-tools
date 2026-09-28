package springboot.DTO;

import lombok.Data;

/**
 * 新增 / 修改壁纸分组。修改时字段为 null 表示不修改；isPublic 接受 true/false 或 1/0。
 * 访问令牌由后端在创建时生成，只能通过 token/regenerate 接口更换。
 */
@Data
public class WallpaperGroupForm {
    private String name;
    private String groupKey;
    private String description;
    private Integer sort;
    private Object isPublic;
}
