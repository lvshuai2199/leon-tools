package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 壁纸图片：file_path / thumb_path 为相对壁纸存储目录的路径，如 {groupId}/{uuid}.jpg。
 */
@TableName(value = "wallpaper_image")
@Data
public class WallpaperImage implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String groupId;

    private String title;

    /** 原图相对路径 */
    private String filePath;

    /** 缩略图相对路径 */
    private String thumbPath;

    private Integer width;

    private Integer height;

    /** 原图字节数 */
    private Long fileSize;

    /** 组内排序（升序） */
    private Integer sort;

    /** 是否启用 1是 0否 */
    private Integer enabled;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
