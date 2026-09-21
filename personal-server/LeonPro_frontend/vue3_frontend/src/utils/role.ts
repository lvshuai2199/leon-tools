/** 系统内置超级管理员角色：默认全权限，不可配置 */
export const ROOT_ROLE_ID = "role_root";
export const ROOT_ROLE_NAME = "ROOT";
export const REGCODE_CLIENT_ROLE_ID = "role_regcode_client";
export const WEB_SUBUSER_LOGIN_BLOCKED = "子用户请使用手机端登录，仅可生成注册码";
export const PHONE_LOGIN_PATH = "/h5/#/pages/login/login";

/** 模块子账号：挂了父用户，不走 Web 登录 */
export function isRegCodeClientUser(user?: { roleId?: string; parentId?: string } | null) {
  if (!user) {
    return false;
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
