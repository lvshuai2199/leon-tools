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

    private Integer generateLimit;
    private Integer generateUsed;
    private String remark;
    private List<String> configIds;
}
