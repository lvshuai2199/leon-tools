package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 壁纸分组：group_key 供外部接口按分组取图；access_token 非空时外部访问需带 token。
 */
@TableName(value = "wallpaper_group")
@Data
public class WallpaperGroup implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 分组名称 */
    private String name;

    /** 分组标识，仅小写字母/数字/-，唯一 */
    private String groupKey;

    private String description;

    /** 排序（升序） */
    private Integer sort;

    /** 是否公开 1是 0否 */
    private Integer isPublic;

    /** 访问令牌，空表示不校验 */
    private String accessToken;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
