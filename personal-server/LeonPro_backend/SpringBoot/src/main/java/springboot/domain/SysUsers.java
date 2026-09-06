package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
     * 用户密码
     */
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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}