import request from "@/utils/request";

const BASE = "/admin/notes";

export interface NoteSource {
  configured: boolean;
  repoUrl: string;
  branch: string;
  tokenSet: boolean;
  status: string;
  lastCommit: string | null;
  lastSyncTime: string | null;
  lastCheckTime: string | null;
  lastError: string | null;
  fileCount: number;
}

export interface NoteDoc {
  path: string;
  title: string;
  size: number;
}

export interface NoteContent {
  path: string;
  title: string;
  content: string;
}

export interface NoteDraft {
  id: string;
  title: string;
  excerpt?: string;
  content?: string;
  updateTime: string | null;
}

const NotesAPI = {
  source() {
    return request<any, NoteSource>({ url: `${BASE}/source`, method: "get" });
  },
  saveSource(data: { repoUrl: string; branch: string; accessToken?: string; clearToken?: boolean }) {
    return request<any, NoteSource>({ url: `${BASE}/source`, method: "post", data });
  },
  branches(data: { repoUrl: string; accessToken?: string }) {
    return request<any, string[]>({ url: `${BASE}/branches`, method: "post", data, timeout: 30000 });
  },
  sync() {
    return request<any, NoteSource>({ url: `${BASE}/sync`, method: "post" });
  },
  docs() {
    return request<any, NoteDoc[]>({ url: `${BASE}/docs`, method: "get" });
  },
  doc(path: string) {
    return request<any, NoteContent>({ url: `${BASE}/doc`, method: "get", params: { path } });
  },
  drafts() {
    return request<any, NoteDraft[]>({ url: `${BASE}/drafts`, method: "get" });
  },
  draft(id: string) {
    return request<any, NoteDraft>({ url: `${BASE}/draft`, method: "get", params: { id } });
  },
  saveDraft(data: { id?: string; title: string; content: string }) {
    return request<any, NoteDraft>({ url: `${BASE}/draft`, method: "post", data });
  },
  deleteDraft(id: string) {
    return request({ url: `${BASE}/draft/delete`, method: "post", data: { id } });
  },
  uploadDraft(id: string) {
    return request<any, { path: string; commit: string; title: string }>({
      url: `${BASE}/draft/upload`,
      method: "post",
      data: { id },
      timeout: 120000,
    });
  },
  async asset(path: string) {
    const res = await request<any, any>({
      url: `${BASE}/asset`,
      method: "get",
      params: { path },
      responseType: "blob",
    });
    const blob: Blob = res?.data instanceof Blob ? res.data : res;
    if (!(blob instanceof Blob) || (res?.status && res.status >= 400)) {
      throw new Error("文件加载失败");
    }
    return blob;
  },
};

export default NotesAPI;
