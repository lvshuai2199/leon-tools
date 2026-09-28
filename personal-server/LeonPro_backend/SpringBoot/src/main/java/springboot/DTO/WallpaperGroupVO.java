package springboot.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Date;

/**
 * 壁纸分组返回对象。token 仅后台接口返回（外部接口永不返回）；coverUrl / coverThumbUrl 仅外部接口返回。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WallpaperGroupVO {
    private String id;
    private String name;
    private String groupKey;
    private String description;
    private Integer sort;
    private Integer isPublic;
    private Long imageCount;
    /** 仅后台：访问令牌（库字段 access_token） */
    private String token;
    /** 仅外部接口：封面原图 / 缩略图地址 */
    private String coverUrl;
    private String coverThumbUrl;
    private Date createTime;
    private Date updateTime;
}
