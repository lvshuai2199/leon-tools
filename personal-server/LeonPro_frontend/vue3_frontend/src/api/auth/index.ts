import request from "@/utils/request";

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
   * 后端返回 data 为 SysUsers 对象：
   * { id, username, nickname, avatarUrl, password, email, createTime, roleId }
   */
  login(data: LoginFormData) {
    return request<any, LoginUserVO>({
      url: `${AUTH_BASE_URL}/auth/login2`,
      method: "post",
      data: {
        username: data.username,
        password: data.password,
      },
      headers: {
        "Content-Type": "application/json",
      },
    });
  },

  /**
   * 登录接口（带验证码）
   */
  loginWithCaptcha(data: LoginFormData) {
    return request<any, LoginUserVO>({
      url: `${AUTH_BASE_URL}/auth/login`,
      method: "post",
      data: {
        username: data.username,
        password: data.password,
        captchaKey: data.captchaKey,
        captchaCode: data.captchaCode,
      },
      headers: {
        "Content-Type": "application/json",
      },
    });
  },

  /**
   * 获取图形验证码
   * 返回 { captchaKey, captchaBase64 }
   */
  getCaptcha() {
    return request<any, CaptchaInfo>({
      url: `${AUTH_BASE_URL}/auth/captcha`,
      method: "post",
      headers: {
        Authorization: "no-auth",
      },
    });
  },

  /**
   * 注销登录
   * LeonPro_backend 无登出接口，由前端本地清理会话
   */
  logout() {
    return Promise.resolve();
  },
};

export default AuthAPI;

/** 登录表单数据 */
export interface LoginFormData {
  /** 用户名 */
  username: string;
  /** 密码 */
  password: string;
  /** 验证码缓存key */
  captchaKey?: string;
  /** 验证码 */
  captchaCode?: string;
}

/** 登录成功返回的用户信息（SysUsers） */
export interface LoginUserVO {
  id?: string;
  username?: string;
  nickname?: string;
  avatarUrl?: string;
  email?: string;
  createTime?: string;
  roleId?: number;
}

/** 验证码信息 */
export interface CaptchaInfo {
  /** 验证码缓存key */
  captchaKey: string;
  /** 验证码图片Base64字符串 */
  captchaBase64: string;
}
