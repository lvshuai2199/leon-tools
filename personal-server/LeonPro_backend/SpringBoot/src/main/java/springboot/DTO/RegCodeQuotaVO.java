package springboot.DTO;

import lombok.Data;

@Data
public class RegCodeQuotaVO {

    /** 管理员不受配额限制 */
    private boolean unlimited;
    private Integer generateLimit;
    private Integer generateUsed;
    private Integer remaining;
}
