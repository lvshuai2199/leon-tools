package springboot.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.io.Serializable;
import lombok.Data;

/**
 * @TableName extern_wallet
 */
@TableName(value ="extern_wallet")
@Data
@Entity
public class ExternWallet implements Serializable {
    @Id
    private String id;

    private String walletName;

    private String walletType;

    private String userId;

    private static final long serialVersionUID = 1L;
}