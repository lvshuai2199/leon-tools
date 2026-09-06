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
    private Date createTime;
}
