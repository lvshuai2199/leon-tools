import request from "@/utils/request";

const BASE_URL = "/mindmap";

/**
 * 思维导图存储 API：保存 PNG 到服务端，换取可外访链接。
 */
const MindmapAPI = {
  getPage(queryParams: MindmapPageQuery) {
    return request<any, IPageResult<MindmapVO>>({
      url: `${BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        title: queryParams.title || undefined,
      },
    });
  },

  getById(id: string) {
    return request<any, MindmapVO>({
      url: `${BASE_URL}/${id}`,
      method: "get",
    });
  },

  save(data: MindmapSaveForm) {
    return request<any, MindmapVO>({
      url: `${BASE_URL}/save`,
      method: "post",
      data,
      timeout: 120000,
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

export default MindmapAPI;

export interface MindmapPageQuery extends PageQuery {
  title?: string;
}

export interface MindmapVO {
  id?: string;
  title?: string;
  markdown?: string;
  publicId?: string;
  url?: string;
  createTime?: string;
  updateTime?: string;
}

export interface MindmapSaveForm {
  id?: string;
  title?: string;
  markdown?: string;
  imageBase64?: string;
}

/** 给外部使用的稳定图片地址（不含变化的查询参数） */
export function mindmapPublicUrl(item: Pick<MindmapVO, "publicId" | "url">) {
  const path = item.url || (item.publicId ? `/public/mindmap/${item.publicId}.png` : "");
  const base = import.meta.env.VITE_APP_BASE_API || "";
  return `${window.location.origin}${base}${path}`;
}

/** 列表预览用，避免缓存旧图；不用于对外链接 */
export function mindmapPreviewUrl(item: Pick<MindmapVO, "publicId" | "url" | "updateTime">) {
  const base = mindmapPublicUrl(item);
  const t = item.updateTime ? String(item.updateTime) : String(Date.now());
  return `${base}${base.includes("?") ? "&" : "?"}t=${encodeURIComponent(t)}`;
}
