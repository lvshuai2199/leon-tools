package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 注册码客户账号：绑定可生成的配置与次数配额。
 */
@TableName(value = "reg_code_user")
@Data
public class RegCodeUser implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /**
     * 旧版“总次数”字段（按用户合计，不分配置）。已改为按配置记在 reg_code_user_config 上，
     * 这两个字段不再读写，仅为兼容旧数据保留；升级时由 SchemaPatcher 迁移到按配置的次数。
     */
    private Integer generateLimit;

    /** 旧版已使用次数，见 {@link #generateLimit} */
    private Integer generateUsed;

    /** 最多可创建的子用户数量（管理员设置，默认 0 = 不能创建） */
    private Integer maxSubUsers;

    /** 账号状态：1 启用，0 停用（客户在注册码页停用子用户时置 0） */
    private Integer status;

    private String remark;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private List<String> configIds;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
