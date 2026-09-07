/** 系统内置超级管理员角色：默认全权限，不可配置 */
export const ROOT_ROLE_ID = "role_root";
export const ROOT_ROLE_NAME = "ROOT";
export const REGCODE_CLIENT_ROLE_ID = "role_regcode_client";
export const WEB_REGCODE_LOGIN_BLOCKED = "注册码用户请使用手机端登录，无法访问 Web 管理端";

/** 注册码子用户：仅允许登录手机端生成页，不能进入 Web 管理端 */
export function isRegCodeClientUser(user?: { roleId?: string; parentId?: string } | null) {
  if (!user) {
    return false;
  }
  if (user.roleId === REGCODE_CLIENT_ROLE_ID) {
    return true;
  }
  return !!(user.parentId && String(user.parentId).trim());
}

export function isRootRole(role?: { id?: string; roleName?: string } | string | null) {
  if (!role) return false;
  if (typeof role === "string") {
    return role.toUpperCase() === ROOT_ROLE_NAME || role === ROOT_ROLE_ID;
  }
  if (role.id === ROOT_ROLE_ID) return true;
  return (role.roleName || "").toUpperCase() === ROOT_ROLE_NAME;
}

export function resolveLoginRoles(roleId?: string, roleName?: string): string[] {
  if (isRootRole({ id: roleId, roleName })) {
    return [ROOT_ROLE_NAME];
  }
  return roleName ? [roleName] : [];
}
