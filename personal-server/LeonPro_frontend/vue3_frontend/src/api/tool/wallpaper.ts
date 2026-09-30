import request from "@/utils/request";
import { ResultEnum } from "@/enums/ResultEnum";

const BASE_URL = "/admin/wallpaper";

/** 壁纸管理 API（管理端，需要登录） */
const WallpaperAPI = {
  /** 组列表（带张数与访问密钥） */
  async listGroups() {
    const list = await request<any, any[]>({
      url: `${BASE_URL}/group/list`,
      method: "get",
    });
    return (list || []).map(normalizeGroup);
  },

  async createGroup(data: WallpaperGroupForm) {
    const res = await request<any, any>({
      url: `${BASE_URL}/group`,
      method: "post",
      data: groupPayload(data),
    });
    return res && typeof res === "object" ? normalizeGroup(res) : undefined;
  },

  async updateGroup(id: WallpaperId, data: WallpaperGroupForm) {
    const res = await request<any, any>({
      url: `${BASE_URL}/group/${id}`,
      method: "put",
      data: groupPayload(data),
    });
    return res && typeof res === "object" ? normalizeGroup(res) : undefined;
  },

  /**
   * 删除组。组里还有图时后端返回 status 409（data 带 imageCount），
   * 这里把它转成 { conflict: true, imageCount }，由页面弹确认后再带 force=true 删除，
   * 避免全局拦截器直接弹错误提示。
   */
  async deleteGroup(id: WallpaperId, force = false): Promise<WallpaperDeleteResult> {
    const res = await request<any, any>({
      url: `${BASE_URL}/group/${id}`,
      method: "delete",
      params: force ? { force: true } : undefined,
      validateStatus: (code) => (code >= 200 && code < 300) || code === 409,
      transformResponse: [
        (raw: any) => {
          let body = raw;
          if (typeof raw === "string") {
            try {
              body = JSON.parse(raw);
            } catch {
              return raw;
            }
          }
          if (body && typeof body === "object" && String(body.status) === "409") {
            const n = Number(body.data?.imageCount ?? body.data ?? 0);
            return { status: ResultEnum.SUCCESS, data: { conflict: true, imageCount: n } };
          }
          return body;
        },
      ],
    });
    if (res && typeof res === "object" && res.conflict) {
      return { conflict: true, imageCount: Number(res.imageCount) || 0 };
    }
    return { conflict: false };
  },

  /** 组拖动排序，传按新顺序排好的 id 数组 */
  sortGroups(ids: WallpaperId[]) {
    return request({
      url: `${BASE_URL}/group/sort`,
      method: "put",
      data: ids,
    });
  },

  /** 重新生成组的访问密钥，返回新 token */
  regenerateToken(id: WallpaperId) {
    return request<any, string>({
      url: `${BASE_URL}/group/${id}/token/regenerate`,
      method: "post",
    });
  },

  async getImagePage(query: WallpaperImageQuery) {
    const page = await request<any, IPageResult<any>>({
      url: `${BASE_URL}/image/page`,
      method: "get",
      params: {
        groupId: query.groupId,
        current: query.current ?? 1,
        size: query.size ?? 40,
      },
    });
    return {
      ...page,
      records: (page?.records || []).map(normalizeImage),
    } as IPageResult<WallpaperImageVO>;
  },

  /**
   * 单张上传，便于逐个显示进度和单独重试。
   * 失败时抛出 WallpaperUploadError：retryable 只在网络错误/超时（没有响应）和 HTTP 5xx 时为 true；
   * 业务/校验错误（HTTP 2xx/4xx 且 body.status 不是成功）重试也没用，retryable 为 false。
   * 413（nginx 自己的 HTML 页，或 JSON body.status 为 413）统一提示「单张图片不能超过 20MB」，不可重试。
   */
  async uploadImage(groupId: WallpaperId, file: File, onProgress?: (percent: number) => void) {
    const form = new FormData();
    form.append("groupId", String(groupId));
    form.append("file", file);
    let httpStatus: number | undefined;
    let bodyMessage: string | undefined;
    let tooLarge = false;
    let res: any;
    try {
      res = await request<any, any>({
        url: `${BASE_URL}/image/upload`,
        method: "post",
        data: form,
        timeout: 0,
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
        // 成功和失败的响应都会经过这里，顺便记下 HTTP 状态码和后端的提示文字
        transformResponse: [
          (raw: any, _headers: any, status?: number) => {
            httpStatus = status;
            let body = raw;
            if (typeof raw === "string") {
              try {
                body = JSON.parse(raw);
              } catch {
                body = raw;
              }
            }
            const isResult = body && typeof body === "object" && "status" in body;
            if (status === 413 || (isResult && String(body.status) === "413")) {
              // 超过大小：nginx 的 413 是 HTML 页，不能原样交给拦截器（会弹「数据格式异常」），这里转成标准结构
              tooLarge = true;
              bodyMessage = UPLOAD_TOO_LARGE_MSG;
              body = { status: "413", message: bodyMessage, data: null };
            } else if (isResult && String(body.status) !== ResultEnum.SUCCESS) {
              bodyMessage = body.message || undefined;
            } else if (!isResult && status && status >= 400 && status < 500) {
              // 其他 4xx 但不是标准结构，同样转成标准错误让拦截器提示
              bodyMessage = `上传失败（HTTP ${status}）`;
              body = { status: String(status), message: bodyMessage, data: null };
            }
            return body;
          },
        ],
      });
    } catch (e: any) {
      const raw = typeof e === "string" ? e : e?.message;
      const retryable = !tooLarge && (httpStatus == null || httpStatus >= 500);
      throw new WallpaperUploadError(bodyMessage || raw || "上传失败", retryable);
    }
    return res && typeof res === "object" ? normalizeImage(res) : undefined;
  },

  updateImage(id: WallpaperId, data: { title?: string; enabled?: boolean }) {
    const payload: Record<string, unknown> = {};
    if (data.title !== undefined) payload.title = data.title;
    if (data.enabled !== undefined) payload.enabled = data.enabled ? 1 : 0;
    return request({
      url: `${BASE_URL}/image/${id}`,
      method: "put",
      data: payload,
    });
  },

  async replaceImage(id: WallpaperId, file: File) {
    const form = new FormData();
    form.append("file", file);
    const res = await request<any, any>({
      url: `${BASE_URL}/image/${id}/replace`,
      method: "post",
      data: form,
      timeout: 0,
      headers: { "Content-Type": "multipart/form-data" },
    });
    return res && typeof res === "object" ? normalizeImage(res) : undefined;
  },

  batch(data: WallpaperBatchForm) {
    return request({
      url: `${BASE_URL}/image/batch`,
      method: "post",
      data,
    });
  },

  /** 组内排序，传按新顺序排好的图片 id 数组 */
  sortImages(ids: WallpaperId[]) {
    return request({
      url: `${BASE_URL}/image/sort`,
      method: "put",
      data: ids,
    });
  },
};

export default WallpaperAPI;

/** id 一律按字符串处理（后端是 32 位字符串） */
export type WallpaperId = string;

/** 上传失败；retryable=false 表示重试也不会成功（格式不支持、超过大小等），只能移除 */
export class WallpaperUploadError extends Error {
  retryable: boolean;
  constructor(message: string, retryable: boolean) {
    super(message);
    this.name = "WallpaperUploadError";
    this.retryable = retryable;
  }
}

export interface WallpaperDeleteResult {
  conflict: boolean;
  imageCount?: number;
}

const toBool = (v: unknown) => v === true || v === 1 || v === "1" || v === "true";

/** 后端 0/1 转成前端布尔值 */
function normalizeGroup(g: any): WallpaperGroupVO {
  return {
    ...g,
    id: String(g.id),
    isPublic: toBool(g.isPublic),
    imageCount: Number(g.imageCount ?? 0),
  };
}
function normalizeImage(i: any): WallpaperImageVO {
  return { ...i, id: String(i.id), groupId: String(i.groupId), enabled: toBool(i.enabled) };
}
/** 前端布尔值转成后端 0/1；sort 为空时不传，新建组由后端排到最后 */
function groupPayload(f: WallpaperGroupForm) {
  const payload: Record<string, unknown> = { ...f, isPublic: f.isPublic ? 1 : 0 };
  if (payload.sort == null) delete payload.sort;
  return payload;
}

export interface WallpaperGroupVO {
  id: WallpaperId;
  name: string;
  groupKey: string;
  description?: string;
  sort?: number;
  isPublic: boolean;
  imageCount?: number;
  token?: string;
}

export interface WallpaperGroupForm {
  name: string;
  groupKey: string;
  description?: string;
  sort?: number;
  isPublic: boolean;
}

export interface WallpaperImageVO {
  id: WallpaperId;
  groupId: WallpaperId;
  title?: string;
  url: string;
  thumbUrl?: string;
  width?: number;
  height?: number;
  fileSize?: number;
  sort?: number;
  enabled: boolean;
  createTime?: string;
}

export interface WallpaperImageQuery {
  groupId: WallpaperId;
  current?: number;
  size?: number;
}

export type WallpaperBatchAction = "enable" | "disable" | "move" | "delete";

export interface WallpaperBatchForm {
  ids: WallpaperId[];
  action: WallpaperBatchAction;
  targetGroupId?: WallpaperId;
}

/** 后端返回的相对路径拼成可直接访问的完整地址 */
export function wallpaperFileUrl(path?: string) {
  if (!path) return "";
  if (/^https?:\/\//i.test(path)) return path;
  const base = import.meta.env.VITE_APP_BASE_API || "";
  return `${window.location.origin}${base}${path.startsWith("/") ? "" : "/"}${path}`;
}

/** 管理端复制用：带 group 和 token 的完整随机接口地址 */
export function wallpaperRandomUrl(group: Pick<WallpaperGroupVO, "groupKey" | "token">) {
  const base = import.meta.env.VITE_APP_BASE_API || "";
  const params = new URLSearchParams({ group: group.groupKey });
  if (group.token) params.set("token", group.token);
  return `${window.location.origin}${base}/public/wallpaper/random?${params.toString()}`;
}

/**
 * 拖拽排序成功后本地同步 sort，规则与后端 reassignSorts 一致：
 * 取这些项现有的 sort 升序（null 视为 0），按新顺序依次分配，重复值顺延为严格递增。
 */
export function reassignSorts<T extends { sort?: number }>(ordered: T[]) {
  const values = ordered.map((x) => Number(x.sort ?? 0)).sort((a, b) => a - b);
  for (let k = 1; k < values.length; k++) {
    if (values[k] <= values[k - 1]) values[k] = values[k - 1] + 1;
  }
  ordered.forEach((x, i) => (x.sort = values[i]));
}

/** 后端时间（ISO 字符串，UTC）转本地 YYYY-MM-DD HH:mm:ss */
export function formatDateTime(v?: string | number | Date) {
  if (v == null || v === "") return "-";
  const d = new Date(v);
  if (isNaN(d.getTime())) return String(v);
  const p = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  );
}

/** 与后端 app.wallpaper.max-file-size 一致 */
export const WALLPAPER_MAX_FILE_SIZE = 20 * 1024 * 1024;
/** 上传被 413 拒绝（nginx 或后端）时给用户看的提示 */
const UPLOAD_TOO_LARGE_MSG = "单张图片不能超过 20MB";

export function formatFileSize(bytes?: number) {
  if (bytes == null || isNaN(bytes)) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}
