/** 系统内置超级管理员角色：默认全权限，不可配置 */
export const ROOT_ROLE_ID = "role_root";
export const ROOT_ROLE_NAME = "ROOT";

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
