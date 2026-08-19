package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @TableName sys_users
 */
@TableName(value ="sys_users")
@Data
@Entity
public class SysUsers implements Serializable {
    /**
     * 
     */
    @Id
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
     * 角色ID
     */
    private Integer roleId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}