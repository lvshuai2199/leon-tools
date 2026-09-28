package springboot.DTO;

import lombok.Data;

import java.util.Date;

/**
 * 壁纸图片返回对象：url / thumbUrl 为可直接访问的静态资源地址。
 */
@Data
public class WallpaperImageVO {
    private String id;
    private String groupId;
    private String title;
    private String url;
    private String thumbUrl;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private Integer sort;
    private Integer enabled;
    private Date createTime;
}
