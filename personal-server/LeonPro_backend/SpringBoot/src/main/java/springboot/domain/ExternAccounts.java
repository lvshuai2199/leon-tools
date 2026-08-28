package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * @TableName extern_accounts
 */
@TableName(value ="extern_accounts")
@Data
public class ExternAccounts implements Serializable {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String accountName;

    private String walletId;

    private String accountBalance;

    private String prifitAmount;

    private Integer userId;

    private static final long serialVersionUID = 1L;
}
