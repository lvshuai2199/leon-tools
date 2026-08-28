package springboot.utils;

import springboot.domain.SysRoles;

/**
 * 系统内置超级管理员角色 ROOT：默认拥有全部权限，不允许配置或删除。
 */
public final class RoleUtils {

    public static final String ROOT_ROLE_ID = "role_root";
    public static final String ROOT_ROLE_NAME = "ROOT";

    private RoleUtils() {
    }

    public static boolean isRoot(SysRoles role) {
        if (role == null) {
            return false;
        }
        return isRoot(role.getId(), role.getRoleName());
    }

    public static boolean isRoot(String roleId, String roleName) {
        if (ROOT_ROLE_ID.equalsIgnoreCase(roleId)) {
            return true;
        }
        return roleName != null && ROOT_ROLE_NAME.equalsIgnoreCase(roleName.trim());
    }
}
