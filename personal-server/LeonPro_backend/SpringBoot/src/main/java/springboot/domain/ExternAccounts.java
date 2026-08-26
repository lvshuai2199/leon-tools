package springboot.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.io.Serializable;
import lombok.Data;

/**
 * @TableName extern_accounts
 */
@TableName(value ="extern_accounts")
@Data
@Entity
public class ExternAccounts implements Serializable {
    @Id
    private String id;

    private String accountName;

    private String walletId;

    private String accountBalance;

    private String prifitAmount;

    private Integer userId;

    private static final long serialVersionUID = 1L;
}