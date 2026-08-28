import request from "@/utils/request";

/**
 * 前端增删改查 API 模板。
 *
 * 复制本文件到 src/api/<模块>/ 后：
 * 1. 改 BASE_URL 为后端 @RequestMapping
 * 2. 改下方类型字段
 * 3. 已落地示例：src/api/tool/regcode-config.ts
 */
const BASE_URL = "/yourEntity";

const CrudAPI = {
  /** 分页 GET /yourEntity/getAll?current=&size= */
  getPage(queryParams: CrudPageQuery) {
    return request<any, IPageResult<CrudPageVO>>({
      url: `${BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        name: queryParams.name || undefined,
      },
    });
  },

  /** 新增 POST /yourEntity/add */
  add(data: CrudForm) {
    return request<any, boolean>({
      url: `${BASE_URL}/add`,
      method: "post",
      data,
    });
  },

  /** 修改 POST /yourEntity/update */
  update(data: CrudForm) {
    return request<any, boolean>({
      url: `${BASE_URL}/update`,
      method: "post",
      data,
    });
  },

  /** 批量删除 POST /yourEntity/del  body: id[] */
  deleteByIds(ids: string[]) {
    return request<any, boolean>({
      url: `${BASE_URL}/del`,
      method: "post",
      data: ids,
    });
  },
};

export default CrudAPI;

export interface CrudPageQuery extends PageQuery {
  name?: string;
}

export interface CrudPageVO {
  id?: string;
  name?: string;
  createTime?: string;
}

export interface CrudForm {
  id?: string;
  name?: string;
}
