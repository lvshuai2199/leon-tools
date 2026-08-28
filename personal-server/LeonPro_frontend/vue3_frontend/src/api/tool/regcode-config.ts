import request from "@/utils/request";

const BASE_URL = "/regCodeConfig";

/**
 * 注册码生成配置 API（对接 LeonPro_backend RegCodeConfigController）
 */
const RegCodeConfigAPI = {
  getPage(queryParams: RegCodeConfigPageQuery) {
    return request<any, IPageResult<RegCodeConfigVO>>({
      url: `${BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        company: queryParams.company || undefined,
        name: queryParams.name || undefined,
        componentName: queryParams.componentName || undefined,
      },
    });
  },

  /** 生成页用：全部配置 */
  list() {
    return request<any, RegCodeConfigVO[]>({
      url: `${BASE_URL}/list`,
      method: "get",
    });
  },

  add(data: RegCodeConfigForm) {
    return request<any, boolean>({
      url: `${BASE_URL}/add`,
      method: "post",
      data,
    });
  },

  update(data: RegCodeConfigForm) {
    return request<any, boolean>({
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

export default RegCodeConfigAPI;

export const ENCRYPT_TYPE_OPTIONS = ["MD5", "SHA-256"] as const;

export interface RegCodeConfigPageQuery extends PageQuery {
  company?: string;
  name?: string;
  componentName?: string;
}

export interface RegCodeConfigVO {
  id?: string;
  company?: string;
  name?: string;
  componentName?: string;
  encryptType?: string;
  encryptSuffix?: string;
  sortOrder?: number;
  createTime?: string;
  updateTime?: string;
}

export interface RegCodeConfigForm {
  id?: string;
  company?: string;
  name?: string;
  componentName?: string;
  encryptType?: string;
  encryptSuffix?: string;
  sortOrder?: number;
}
