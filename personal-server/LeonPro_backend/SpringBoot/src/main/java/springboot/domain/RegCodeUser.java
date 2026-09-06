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

    /** 可生成次数上限 */
    private Integer generateLimit;

    /** 已使用次数 */
    private Integer generateUsed;

    private String remark;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private List<String> configIds;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
