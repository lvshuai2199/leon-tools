import request from "@/utils/request";

const USER_BASE_URL = "/sysUsers";

/**
 * 用户 API（对接 LeonPro_backend SysUserController）
 *
 * 注意：后端分页参数为 MyBatis-Plus Page 绑定（current / size），
 * 分页返回 IPage 结构：{ records, total, size, current, pages }
 */
const UserAPI = {
  /**
   * 获取用户分页列表
   * GET /sysUsers/getUsers?current=1&size=10&username=xxx
   */
  getPage(queryParams: UserPageQuery) {
    return request<any, IPageResult<UserPageVO>>({
      url: `${USER_BASE_URL}/getUsers`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        username: queryParams.username || undefined,
      },
    });
  },

  /**
   * 获取当前登录用户信息
   * 后端目前硬编码返回用户名 "leon" 的数据
   */
  getInfo() {
    return request<any, UserInfo>({
      url: `${USER_BASE_URL}/getMyInfo`,
      method: "get",
    });
  },

  /**
   * 新增 / 更新用户
   * 有 id 时更新（仅 username/email），无 id 时注册（密码必填）
   */
  saveOrUpdate(data: UserForm) {
    return request<any, string>({
      url: `${USER_BASE_URL}/userSaveOrUpdate`,
      method: "post",
      data,
    });
  },

  /**
   * 批量删除用户
   */
  deleteByIds(userIds: string[]) {
    return request<any, string>({
      url: `${USER_BASE_URL}/delUsers`,
      method: "post",
      data: { userIds },
    });
  },
};

export default UserAPI;

/** 登录用户信息（适配 LeonPro_backend SysUsers 字段） */
export interface UserInfo {
  /** 用户ID */
  id?: string;
  /** 用户名 */
  username?: string;
  /** 昵称 */
  nickname?: string;
  /** 头像URL */
  avatarUrl?: string;
  /** 邮箱 */
  email?: string;
  /** 创建时间 */
  createTime?: string;
  /** 角色ID */
  roleId?: number;

  /** 兼容模板原有字段（提供默认值避免报错） */
  userId?: number;
  avatar?: string;
  roles: string[];
  perms: string[];
}

/** 用户分页查询参数 */
export interface UserPageQuery extends PageQuery {
  /** 用户名 */
  username?: string;
}

/** 用户分页对象（IPage 映射） */
export interface UserPageVO {
  id?: string;
  username?: string;
  nickname?: string;
  avatarUrl?: string;
  email?: string;
  createTime?: string;
  roleId?: number;
}

/** 用户表单 */
export interface UserForm {
  /** 用户ID（更新时必传） */
  id?: string;
  username: string;
  /** 密码（新增必填） */
  password?: string;
  email?: string;
}
