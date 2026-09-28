package springboot.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import springboot.domain.SysMenus;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * GET /auth/me 返回：当前登录用户（只按 token 取），不含 password / token。
 */
@Data
public class MeVO {
    private String id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private Date createTime;
    private String roleId;
    private String roleName;
    private String parentId;
    /** 角色已分配的菜单 id（与登录返回一致）；ROOT 为 null，表示全部 */
    private List<String> menuIds;
    /**
     * 当前用户可用的用户端（client=app）菜单。
     * 一期 sys_menus 还没有 client 字段，固定返回空数组；二期按菜单清单同步后再填充（ROOT 返回全部）。
     */
    private List<SysMenus> appMenus = Collections.emptyList();
    /** 是否 ROOT */
    private boolean root;
    /** 能否登录 / 使用管理端（子账号、注册码客户为 false） */
    private boolean canLoginWeb;
    /** 能否使用用户端出货 */
    private boolean canUseCrab;
    /** 能否使用注册码生成（/common/**） */
    private boolean canUseRegCode;

    /** 注册码子用户相关（用户端注册码页用） */
    private RegCodeInfo regCode = new RegCodeInfo();

    @Data
    public static class RegCodeInfo {
        /** 是否底层子用户（在注册码页由客户创建的，不能再建子用户） */
        @JsonProperty("isSubUser")
        private boolean subUser;
        /** 现在能否新建子用户（顶层账号、有注册码权限、启用中的子用户数 < 上限） */
        private boolean canCreateSubUsers;
        /** 能否进入子用户管理（顶层账号且有注册码权限；上限用完也能管理已有的） */
        private boolean canManageSubUsers;
        private int maxSubUsers;
        /** 启用中的子用户数（停用的不计） */
        private int createdCount;
    }
}
