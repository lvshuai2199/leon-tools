import request from "@/utils/request";

const BASE_URL = "/admin/regCodeUser";

/**
 * 注册码客户 API（管理端）
 *
 * 客户相关接口的 id 两种都认：列表项的 `id`（reg_code_user.id）或 `userId`（客户的用户 id），
 * 前端统一传 `userId`，没有时退回 `id`（见 customerKey）。
 */
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

  /** 注册码生成页用：自己各配置的次数 */
  myQuota() {
    return request<any, RegCodeQuotaVO>({
      url: "/common/regCodeUser/myQuota",
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

  /** 删除客户：它创建的子用户保留但停用，未用次数作废 */
  deleteByIds(ids: string[]) {
    return request<any, RegCodeUserDeleteResult>({
      url: `${BASE_URL}/del`,
      method: "post",
      data: ids,
    });
  },

  /** 某个客户创建的子用户 */
  listSubUsers(customerId: string) {
    return request<any, SubUserListVO>({
      url: `${BASE_URL}/${encodeURIComponent(customerId)}/subUsers`,
      method: "get",
    });
  },

  /** 子用户各配置的次数 */
  getSubUserQuota(subId: string) {
    return request<any, SubUserQuotaVO>({
      url: `${BASE_URL}/subUsers/${encodeURIComponent(subId)}/quota`,
      method: "get",
    });
  },

  /**
   * 调整子用户次数（管理员）：正数直接加给子用户，不从客户扣；
   * 负数收回子用户未用的次数并作废，不退给客户
   */
  adjustSubUserQuota(subId: string, items: SubUserQuotaDelta[]) {
    return request<any, SubUserQuotaVO>({
      url: `${BASE_URL}/subUsers/${encodeURIComponent(subId)}/quota`,
      method: "post",
      data: { items },
    });
  },
};

export default RegCodeUserAPI;

/** 客户接口用的 id：优先 userId，没有时用 id */
export function customerKey(row: Pick<RegCodeUserVO, "id" | "userId">) {
  return String(row.userId || row.id || "");
}

export interface RegCodeUserPageQuery extends PageQuery {
  username?: string;
  parentId?: string;
}

/** 某个配置的次数明细 */
export interface RegCodeQuotaItem {
  configId?: string;
  configName?: string;
  allocated?: number;
  used?: number;
  remaining?: number;
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
  /** 各配置次数合计（兼容旧字段） */
  generateLimit?: number;
  generateUsed?: number;
  remaining?: number;
  remark?: string;
  configIds?: string[];
  configLabels?: string[];
  createTime?: string;
  /** 各配置次数明细 */
  quotas?: RegCodeQuotaItem[];
  /** 可创建的子用户数，0 = 不能创建 */
  maxSubUsers?: number;
  /** 1 启用 0 停用 */
  status?: number;
  /** 启用中的子用户数 */
  subUserCount?: number;
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
  remark?: string;
  configIds?: string[];
  /** 各配置次数上限（不能低于已用；不在列表里的配置会被移除） */
  quotas?: { configId: string; count: number }[];
  maxSubUsers?: number;
}

export interface RegCodeUserDeleteResult {
  removed?: boolean;
  retiredSubUsers?: number;
  voidedTotal?: number;
}

export interface SubUserVO {
  id?: string;
  username?: string;
  nickname?: string;
  /** 1 启用 0 停用 */
  status?: number;
  createTime?: string;
  usedTotal?: number;
  allocatedTotal?: number;
}

export interface SubUserListVO {
  createdCount?: number;
  maxSubUsers?: number;
  canCreate?: boolean;
  items?: SubUserVO[];
}

export interface SubUserQuotaVO {
  subUserId?: string;
  items?: RegCodeQuotaItem[];
  refundableTotal?: number;
  /** 创建人自己各配置的剩余（创建人已删除时为空） */
  creatorRemaining?: RegCodeQuotaItem[];
}

export interface SubUserQuotaDelta {
  configId: string;
  delta: number;
}

export interface RegCodeQuotaVO {
  unlimited?: boolean;
  /** 各配置次数明细（ROOT 为空） */
  items?: RegCodeQuotaItem[];
  generateLimit?: number;
  generateUsed?: number;
  remaining?: number;
}
