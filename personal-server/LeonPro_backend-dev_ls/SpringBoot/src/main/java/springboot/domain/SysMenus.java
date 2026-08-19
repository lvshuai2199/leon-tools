package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 
 * @TableName sys_menus
 */
@TableName(value ="sys_menus")
@Data
@Entity
public class SysMenus implements Serializable {
    /**
     * 
     */
    @Id
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 菜单链接
     */
    private String menuUrl;

    /**
     * 父级ID
     */
    private String parentId;      // 类型与id一致

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 图标
     */
    private String icon;

    /**
     * 是否显示
     */
    private Integer visible;      // 1显示 0隐藏

    /**
     * 目录类型
     */
    private Integer menuType;     // 0目录 1菜单 2按钮

    /**
     * 权限
     */
    private String permission;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}