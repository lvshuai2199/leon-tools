import request from "@/utils/request";
import type { UserInfo } from "@/api/system/user";

const AUTH_BASE_URL = "";

/**
 * 认证 API（对接 LeonPro_backend）
 *
 * 后端响应统一为 ApiResponse: { status, message, data, timestamp }
 * request 拦截器已校验 status === 200 并直接返回 data
 */
const AuthAPI = {
  /**
   * 登录接口（无需验证码）
   *
   * 后端返回 data 为用户对象（含登录凭证 token，不再返回 password）：
   * { id, username, nickname, avatarUrl, email, createTime, roleId, roleName, token }
   *
   * 标记 no-auth：不带旧 token，且密码错误等失败不会被当成「登录失效」处理；
   * 错误提示由登录页统一弹出（skipErrorMessage 避免拦截器再弹一次）
   */
  login(data: LoginFormData) {
    return request<any, LoginUserVO>({
      url: `${AUTH_BASE_URL}/auth/login`,
      method: "post",
      data: {
        username: data.username,
        password: data.password,
        source: "web",
      },
      headers: {
        "Content-Type": "application/json",
        Authorization: "no-auth",
      },
      skipErrorMessage: true,
    });
  },

  /**
   * 注销登录：后端作废当前 token（需携带 token）。
   * 尽力而为：401 不触发「登录失效」跳转、出错不弹提示，调用方无论结果都清理本地会话
   */
  logout() {
    return request<any, unknown>({
      url: `${AUTH_BASE_URL}/auth/logout`,
      method: "post",
      timeout: 5000,
      skipAuthRedirect: true,
      skipErrorMessage: true,
    });
  },

  /** 当前登录用户（只按 token 取，不传 username） */
  getMe() {
    return request<any, UserInfo>({
      url: `${AUTH_BASE_URL}/auth/me`,
      method: "get",
    });
  },

  /** 修改自己的资料：只认 nickname / email / password，password 留空表示不改 */
  updateMe(data: { nickname?: string; email?: string; password?: string }) {
    return request<any, string>({
      url: `${AUTH_BASE_URL}/auth/me`,
      method: "post",
      data,
    });
  },
};

export default AuthAPI;

/** 登录表单数据 */
export interface LoginFormData {
  /** 用户名 */
  username: string;
  /** 密码 */
  password: string;
}

/** 登录成功返回的用户信息（SysUsers） */
export interface LoginUserVO {
  id?: string;
  username?: string;
  nickname?: string;
  avatarUrl?: string;
  email?: string;
  createTime?: string;
  /** 角色ID（对应 sys_roles.id） */
  roleId?: string;
  /** 角色名称（登录时由后端回填） */
  roleName?: string;
  /** 父用户 ID；有值表示注册码子用户 */
  parentId?: string;
  menuIds?: string[] | null;
  /** 登录凭证：之后请求头携带 Authorization: Bearer <token> */
  token?: string;
}
