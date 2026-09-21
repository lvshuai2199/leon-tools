import request from "@/utils/request";

const BASE_URL = "/crabShipment";

const CrabShipmentAPI = {
  getPage(queryParams: CrabShipmentPageQuery) {
    return request<any, IPageResult<CrabShipmentVO>>({
      url: `${BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        shipDate: queryParams.shipDateStart || queryParams.shipDateEnd ? undefined : queryParams.shipDate || undefined,
        shipDateStart: queryParams.shipDateStart || undefined,
        shipDateEnd: queryParams.shipDateEnd || undefined,
        customerName: queryParams.customerName || undefined,
        phone: queryParams.phone || undefined,
        paid: queryParams.paid,
        shipped: queryParams.shipped,
      },
    });
  },

  getById(id: string) {
    return request<any, CrabShipmentVO>({
      url: `${BASE_URL}/${id}`,
      method: "get",
    });
  },

  save(data: CrabShipmentForm) {
    return request<any, CrabShipmentVO>({
      url: `${BASE_URL}/save`,
      method: "post",
      data,
    });
  },

  updateStatus(data: CrabShipmentStatusForm) {
    return request<any, CrabShipmentVO>({
      url: `${BASE_URL}/status`,
      method: "post",
      data,
    });
  },

  batchSave(data: CrabShipmentBatchForm) {
    return request<any, CrabShipmentVO[]>({
      url: `${BASE_URL}/batchSave`,
      method: "post",
      data,
    });
  },

  parse(text: string) {
    return request<any, CrabParseResult>({
      url: `${BASE_URL}/parse`,
      method: "post",
      data: { text },
    });
  },

  deleteByIds(ids: string[]) {
    return request<any, boolean>({
      url: `${BASE_URL}/del`,
      method: "post",
      data: ids,
    });
  },

  publicView(publicId: string) {
    return request<any, CrabPublicVO>({
      url: `/public/crabShipment/${publicId}`,
      method: "get",
      headers: { Authorization: "no-auth" },
    });
  },
};

export default CrabShipmentAPI;

export function crabShareUrl(item: Pick<CrabShipmentVO, "publicId" | "sharePath">) {
  const path = item.sharePath || (item.publicId ? `/h5/#/pages/crab/share?id=${item.publicId}` : "");
  if (!path) return "";
  if (typeof window === "undefined") return path;
  return `${window.location.origin}${path}`;
}

export interface CrabShipmentPageQuery extends PageQuery {
  shipDate?: string;
  shipDateStart?: string;
  shipDateEnd?: string;
  customerName?: string;
  phone?: string;
  paid?: number;
  shipped?: number;
}

export interface CrabShipmentVO {
  id?: string;
  seqNo?: number;
  customerName?: string;
  phone?: string;
  address?: string;
  spec?: string;
  quantity?: number;
  paid?: number;
  shipped?: number;
  trackingNo?: string;
  shipDate?: string;
  remark?: string;
  publicId?: string;
  sharePath?: string;
  operatorName?: string;
  createTime?: string;
  updateTime?: string;
}

export interface CrabShipmentForm {
  id?: string;
  seqNo?: number;
  customerName?: string;
  phone?: string;
  address?: string;
  spec?: string;
  quantity?: number | null;
  paid?: number;
  shipped?: number;
  trackingNo?: string;
  shipDate?: string;
  remark?: string;
}

export interface CrabShipmentStatusForm {
  id: string;
  paid?: number;
  shipped?: number;
  trackingNo?: string;
}

export interface CrabShipmentBatchForm {
  shipDate?: string;
  records: CrabShipmentForm[];
}

export interface CrabParseResult {
  records?: CrabShipmentForm[];
  count?: number;
}

export interface CrabPublicVO {
  publicId?: string;
  seqNo?: number;
  customerName?: string;
  phone?: string;
  address?: string;
  spec?: string;
  quantity?: number;
  paid?: number;
  shipped?: number;
  trackingNo?: string;
  shipDate?: string;
  updateTime?: string;
}
