import request from "@/utils/request";

const BASE_URL = "/regCodeUser";

const RegCodeUserAPI = {
  getPage(queryParams: RegCodeUserPageQuery) {
    return request<any, IPageResult<RegCodeUserVO>>({
      url: `${BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        username: queryParams.username || undefined,
        parentId: queryParams.parentId || undefined,
      },
    });
  },

  myQuota() {
    return request<any, RegCodeQuotaVO>({
      url: `${BASE_URL}/myQuota`,
      method: "get",
    });
  },

  save(data: RegCodeUserForm) {
    return request<any, string>({
      url: `${BASE_URL}/save`,
      method: "post",
      data,
    });
  },

  update(data: RegCodeUserForm) {
    return request<any, string>({
      url: `${BASE_URL}/update`,
      method: "post",
      data,
    });
  },

  deleteByIds(ids: string[]) {
    return request<any, boolean>({
      url: `${BASE_URL}/del`,
      method: "post",
      data: ids,
    });
  },
};

export default RegCodeUserAPI;

export interface RegCodeUserPageQuery extends PageQuery {
  username?: string;
  parentId?: string;
}

export interface RegCodeUserVO {
  id?: string;
  userId?: string;
  parentId?: string;
  parentUsername?: string;
  parentNickname?: string;
  username?: string;
  nickname?: string;
  email?: string;
  roleId?: string;
  roleName?: string;
  generateLimit?: number;
  generateUsed?: number;
  remaining?: number;
  remark?: string;
  configIds?: string[];
  configLabels?: string[];
  createTime?: string;
}

export interface RegCodeUserForm {
  id?: string;
  userId?: string;
  parentId?: string;
  username?: string;
  password?: string;
  nickname?: string;
  email?: string;
  roleId?: string;
  generateLimit?: number;
  generateUsed?: number;
  remark?: string;
  configIds?: string[];
}

export interface RegCodeQuotaVO {
  unlimited?: boolean;
  generateLimit?: number;
  generateUsed?: number;
  remaining?: number;
}
