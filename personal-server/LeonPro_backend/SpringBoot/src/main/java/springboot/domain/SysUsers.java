package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @TableName sys_users
 */
@TableName(value ="sys_users")
@Data
public class SysUsers implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 用户名称
     */
    private String username;

    private String nickname;

    private String avatarUrl;

    /**
     * 用户密码（只接收不输出：任何接口响应都不返回密码）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 角色ID（对应 sys_roles.id）
     */
    private String roleId;

    /**
     * 父用户 ID；为空表示主用户，有值表示注册码子用户
     */
    private String parentId;

    /**
     * 角色名称（非表字段，登录时回填）
     */
    @TableField(exist = false)
    private String roleName;

    /**
     * 直接下级用户数量（非表字段）
     */
    @TableField(exist = false)
    private Integer childCount;

    /**
     * 角色已分配菜单 ID（非表字段，登录时回填；ROOT 为 null 表示全部）
     */
    @TableField(exist = false)
    private java.util.List<String> menuIds;

    /**
     * 登录凭证（非表字段，仅 /auth/login 登录接口返回；为空时不输出）
     */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}