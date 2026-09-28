package springboot.DTO;

import lombok.Data;

/**
 * POST /auth/me 可修改字段白名单：只有 nickname、phone、email、password。
 * 其他字段（角色、父用户、用户名等）即使前端传了也不会绑定到这里。
 */
@Data
public class MeUpdateForm {
    private String nickname;
    /** sys_users 目前没有 phone 列，一期接收但不保存（见接口说明） */
    private String phone;
    private String email;
    /** 留空表示不修改；与后台用户管理一致，至少 6 位 */
    private String password;
}
