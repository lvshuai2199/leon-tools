package springboot.DTO;

import lombok.Data;

import java.util.List;

@Data
public class RegCodeUserForm {

    /** 分配记录 ID（更新时必传） */
    private String id;

    /** 绑定已有用户；为空则按用户名新建 */
    private String userId;

    /** 所属主用户 ID */
    private String parentId;

    private String username;
    private String password;
    private String nickname;
    private String email;
    private String roleId;

    /** 旧版：总次数。仅在未传 quotas 时兼容使用——configIds 里每个配置都给 generateLimit 次 */
    private Integer generateLimit;
    /** 旧版字段，已不使用（已用次数按配置记录，不能在这里改） */
    private Integer generateUsed;
    private String remark;
    /** 旧版：可用配置列表，见 generateLimit */
    private List<String> configIds;

    /** 各配置的次数上限：[{configId, count}]；不在列表里的配置会被移除，上限不能低于该配置已用 */
    private List<RegCodeSubUser.QuotaCount> quotas;

    /** 最多可创建的子用户数（默认 0 = 不能创建）；更新时不传则不改 */
    private Integer maxSubUsers;
}
