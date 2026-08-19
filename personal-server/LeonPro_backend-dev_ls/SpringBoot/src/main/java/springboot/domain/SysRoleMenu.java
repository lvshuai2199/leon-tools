package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.io.Serializable;

/**
 * (SysRoleMenu)实体类
 *
 * @author makejava
 * @since 2025-04-15 17:17:58
 */
@TableName(value ="sys_role_menu")
@Data
@Entity
public class SysRoleMenu implements Serializable {

    /**
     *
     */
    @Id
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 角色id
     */
    private String roldId;

    /**
     * 菜单id
     */
    private String menuId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}

