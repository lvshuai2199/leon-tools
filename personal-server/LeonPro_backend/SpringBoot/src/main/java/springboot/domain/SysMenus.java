package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;

import lombok.Data;

/**
 * 
 * @TableName sys_menus
 */
@TableName(value ="sys_menus")
@Data
public class SysMenus implements Serializable {
    /**
     * 
     */
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
     * 组件路径（菜单类型填写，如 tool/trace/index；目录类型固定为 Layout）
     */
    private String component;

    /**
     * 路由名称（如 Trace，需与页面 defineOptions.name 一致以便 keep-alive 生效）
     */
    private String routeName;

    /**
     * 是否开启页面缓存 1开启 0关闭
     */
    private Integer keepAlive;

    /**
     * 始终显示 1是 0否
     */
    private Integer alwaysShow;

    /**
     * 目录跳转地址（目录类型填写）
     */
    private String redirect;

    /**
     * 所属端：admin 管理端 / app 用户端（默认 admin）
     */
    private String client;

    /**
     * 规范化后的完整路径（如 /regcode/user），与 client 一起唯一；菜单清单同步按它匹配
     */
    private String routeKey;

    /**
     * 1 = 由页面清单 menus.json 管理（启动时同步覆盖清单负责的字段）；0 = 手工菜单
     */
    private Integer managed;

    /**
     * 1 = 已从清单移除而停用（保留行和角色授权，任何菜单接口都不返回）
     */
    private Integer disabled;

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