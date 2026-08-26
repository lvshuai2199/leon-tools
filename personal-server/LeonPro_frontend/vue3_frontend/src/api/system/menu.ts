import request from "@/utils/request";

/**
 * 菜单 API
 *
 * LeonPro_backend 的菜单接口（/auth/getMenuList）返回 SysMenus 结构，
 * 与前端路由格式不匹配，因此此处使用静态路由配置。
 * 后续如需动态菜单，可将后端 SysMenus 映射为 RouteVO。
 */
const MenuAPI = {
  /**
   * 获取当前用户的路由列表（静态配置，对接 LeonPro 功能）
   */
  getRoutes() {
    const jsonData = [
      {
        path: "/tool",
        component: "Layout",
        redirect: "/tool/trace",
        name: "/tool",
        meta: {
          title: "工具中心",
          icon: "api",
          hidden: false,
          alwaysShow: false,
          params: null,
        },
        children: [
          {
            path: "trace",
            component: "tool/trace/index",
            name: "Trace",
            meta: {
              title: "轨迹分析",
              icon: "code",
              hidden: false,
              keepAlive: false,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "files",
            component: "tool/files/index",
            name: "Files",
            meta: {
              title: "文件工具",
              icon: "document",
              hidden: false,
              keepAlive: false,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "documents",
            component: "tool/documents/index",
            name: "Documents",
            meta: {
              title: "文档工具",
              icon: "file",
              hidden: false,
              keepAlive: false,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
      {
        path: "/work",
        component: "Layout",
        redirect: "/work/tasks",
        name: "/work",
        meta: {
          title: "业务管理",
          icon: "todo",
          hidden: false,
          alwaysShow: false,
          params: null,
        },
        children: [
          {
            path: "tasks",
            component: "work/tasks/index",
            name: "Tasks",
            meta: {
              title: "任务管理",
              icon: "todo",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "registration",
            component: "work/registration/index",
            name: "Registration",
            meta: {
              title: "注册申请",
              icon: "client",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
      {
        path: "/system",
        component: "Layout",
        redirect: "/system/user",
        name: "/system",
        meta: {
          title: "系统管理",
          icon: "system",
          hidden: false,
          alwaysShow: false,
          params: null,
        },
        children: [
          {
            path: "user",
            component: "system/user/index",
            name: "User",
            meta: {
              title: "用户管理",
              icon: "role",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "role",
            component: "system/role/index",
            name: "Role",
            meta: {
              title: "角色管理",
              icon: "role",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
    ];

    // 注意：必须返回 Promise，permission store 会对返回值调用 .then()
    return Promise.resolve(jsonData);
  },
};

export default MenuAPI;

/** RouteVO，路由对象 */
export interface RouteVO {
  /** 子路由列表 */
  children: RouteVO[];
  /** 组件路径 */
  component?: string;
  /** 路由属性 */
  meta?: Meta;
  /** 路由名称 */
  name?: string;
  /** 路由路径 */
  path?: string;
  /** 跳转链接 */
  redirect?: string;
}

/** Meta，路由属性 */
export interface Meta {
  /** 【目录】只有一个子路由是否始终显示 */
  alwaysShow?: boolean;
  /** 是否隐藏(true-是 false-否) */
  hidden?: boolean;
  /** ICON */
  icon?: string;
  /** 【菜单】是否开启页面缓存 */
  keepAlive?: boolean;
  /** 路由title */
  title?: string;
}
