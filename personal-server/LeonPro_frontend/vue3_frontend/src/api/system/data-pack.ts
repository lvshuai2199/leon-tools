import request from "@/utils/request";

const BASE_URL = "/systemData";

const SystemDataAPI = {
  status() {
    return request<any, SystemDataStatusVO>({
      url: `${BASE_URL}/status`,
      method: "get",
    });
  },

  export() {
    return request<any, Blob>({
      url: `${BASE_URL}/export`,
      method: "get",
      responseType: "blob",
      timeout: 120000,
    });
  },
};

export default SystemDataAPI;

export interface SystemDataStatusVO {
  packOnClasspath?: boolean;
  packOnDisk?: boolean;
  classpathPath?: string;
  diskPath?: string;
  mindmapCount?: number;
  regCodeConfigCount?: number;
  storageDir?: string;
}
