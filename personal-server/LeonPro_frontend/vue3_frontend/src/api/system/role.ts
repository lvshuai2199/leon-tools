import request from "@/utils/request";

const ROLE_BASE_URL = "/sysRoles";

/**
 * 角色 API（对接 LeonPro_backend SysRolesController）
 */
const RoleAPI = {
  /** 获取角色分页列表 GET /sysRoles/getAll?current=1&size=10 */
  getPage(queryParams?: RolePageQuery) {
    return request<any, IPageResult<RolePageVO>>({
      url: `${ROLE_BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams?.current ?? 1,
        size: queryParams?.size ?? 10,
        roleName: queryParams?.roleName || undefined,
      },
    });
  },

  /** 新增角色 */
  add(data: RoleForm) {
    return request<any, boolean>({
      url: `${ROLE_BASE_URL}/add`,
      method: "post",
      data,
    });
  },

  /** 修改角色 */
  update(data: RoleForm) {
    return request<any, boolean>({
      url: `${ROLE_BASE_URL}/update`,
      method: "post",
      data,
    });
  },

  /**
   * 批量删除角色（RequestBody 传 ID 数组）
   */
  deleteByIds(ids: string[]) {
    return request<any, boolean>({
      url: `${ROLE_BASE_URL}/del`,
      method: "post",
      data: ids,
    });
  },
};

export default RoleAPI;

/** 角色分页查询参数 */
export interface RolePageQuery extends PageQuery {
  roleName?: string;
}

/** 角色分页对象（SysRoles 映射） */
export interface RolePageVO {
  id?: string;
  roleName?: string;
  description?: string;
  isDisabled?: number;
  createTime?: string;
}

/** 角色表单 */
export interface RoleForm {
  id?: string;
  roleName?: string;
  description?: string;
  isDisabled?: number;
  createTime?: string;
}
