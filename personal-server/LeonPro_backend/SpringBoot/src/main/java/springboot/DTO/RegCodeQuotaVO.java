package springboot.DTO;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /common/regCodeUser/myQuota：当前用户各配置的次数。
 * 次数按配置分别计算；generateLimit / generateUsed / remaining 是各配置合计，仅为兼容旧页面保留。
 */
@Data
public class RegCodeQuotaVO {

    /** ROOT 不受次数限制，可用全部配置（items 为空） */
    private boolean unlimited;
    private List<RegCodeSubUser.QuotaItem> items = new ArrayList<>();
    private Integer generateLimit;
    private Integer generateUsed;
    private Integer remaining;
}
