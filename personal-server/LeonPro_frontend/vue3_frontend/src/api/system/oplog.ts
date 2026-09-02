import request from "@/utils/request";

const OPLOG_BASE_URL = "/sysOperationLog";

const OperationLogAPI = {
  getPage(queryParams: OperationLogPageQuery) {
    return request<any, IPageResult<OperationLogVO>>({
      url: `${OPLOG_BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        operatorName: queryParams.operatorName || undefined,
        module: queryParams.module || undefined,
        status: queryParams.status || undefined,
        requestUri: queryParams.requestUri || undefined,
        action: queryParams.action || undefined,
        beginTime: queryParams.beginTime || undefined,
        endTime: queryParams.endTime || undefined,
      },
    });
  },

  getById(id: string) {
    return request<any, OperationLogVO>({
      url: `${OPLOG_BASE_URL}/${id}`,
      method: "get",
    });
  },
};

export default OperationLogAPI;

export interface OperationLogPageQuery extends PageQuery {
  operatorName?: string;
  module?: string;
  status?: string;
  requestUri?: string;
  action?: string;
  beginTime?: string;
  endTime?: string;
}

export interface OperationLogVO {
  id?: string;
  operatorId?: string;
  operatorName?: string;
  module?: string;
  action?: string;
  requestMethod?: string;
  requestUri?: string;
  requestParams?: string;
  ip?: string;
  userAgent?: string;
  status?: string;
  resultMsg?: string;
  errorMsg?: string;
  costMs?: number;
  createTime?: string;
}
