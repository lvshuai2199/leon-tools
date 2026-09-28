package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@TableName(value = "reg_code_user_config")
@Data
public class RegCodeUserConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String configId;

    /** 该配置已分配的次数（可生成上限） */
    private Integer generateLimit;

    /** 该配置已使用的次数；剩余 = generateLimit - generateUsed */
    private Integer generateUsed;
}
