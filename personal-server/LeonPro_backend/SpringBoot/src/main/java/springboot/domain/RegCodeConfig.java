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
 * 注册码生成配置：公司 + 名称 + 组件 + 加密方式/后缀。
 */
@TableName(value = "reg_code_config")
@Data
@Entity
public class RegCodeConfig implements Serializable {

    @Id
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 公司 */
    private String company;

    /** 名称（如 焊接专机 / CNC插件） */
    private String name;

    /** 组件名称（用于后续按类型拆分页面） */
    private String componentName;

    /** 加密方式，如 MD5 */
    private String encryptType;

    /** 加密字符后缀，拼在注册码后做哈希 */
    private String encryptSuffix;

    /** 排序，越小越靠前 */
    private Integer sortOrder;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
