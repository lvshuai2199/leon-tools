package springboot.DTO;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class RegCodeUserVO {

    private String id;
    private String userId;
    private String parentId;
    private String parentUsername;
    private String parentNickname;
    private String username;
    private String nickname;
    private String email;
    private String roleId;
    private String roleName;
    private Integer generateLimit;
    private Integer generateUsed;
    private Integer remaining;
    private String remark;
    private List<String> configIds;
    private List<String> configLabels;
    /** 各配置次数明细 [{configId, configName, allocated, used, remaining}]；上面的 generateLimit / generateUsed / remaining 是合计 */
    private List<RegCodeSubUser.QuotaItem> quotas;
    private Integer maxSubUsers;
    /** 1 启用，0 停用 */
    private Integer status;
    /** 启用中的子用户数 */
    private Integer subUserCount;
    private Date createTime;
}
