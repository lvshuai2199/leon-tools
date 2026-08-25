package springboot.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * @TableName extern_wallet
 */
@TableName(value ="extern_wallet")
@Data
public class ExternWallet implements Serializable {
    private String id;

    private String walletName;

    private String walletType;

    private String userId;

    private static final long serialVersionUID = 1L;
}