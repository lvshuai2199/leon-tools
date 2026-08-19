import request from "@/utils/request";
// 菜单基础URL
const MENU_BASE_URL = "/api/v1/menus";

const MenuAPI = {
  /**
   * 获取当前用户的路由列表
   * <p/>
   * 无需传入角色，后端解析token获取角色自行判断是否拥有路由的权限
   *
   * @returns 路由列表
   */
  getRoutes() {
    const jsonData = [
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
              icon: "el-icon-User",
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
          {
            path: "menu",
            component: "system/menu/index",
            name: "SysMenu",
            meta: {
              title: "菜单管理",
              icon: "menu",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "dept",
            component: "system/dept/index",
            name: "Dept",
            meta: {
              title: "部门管理",
              icon: "tree",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "dict",
            component: "system/dict/index",
            name: "Dict",
            meta: {
              title: "字典管理",
              icon: "dict",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "log",
            component: "system/log/index",
            name: "Log",
            meta: {
              title: "系统日志",
              icon: "document",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "dict-data",
            component: "system/dict/data",
            name: "DictData",
            meta: {
              title: "字典数据",
              icon: "",
              hidden: true,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "config",
            component: "system/config/index",
            name: "Config",
            meta: {
              title: "系统配置",
              icon: "setting",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "notice",
            component: "system/notice/index",
            name: "Notice",
            meta: {
              title: "通知公告",
              icon: "",
              hidden: false,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
      {
        path: "/codegen",
        component: "Layout",
        name: "/codegen",
        meta: {
          title: "系统工具",
          icon: "menu",
          hidden: false,
          alwaysShow: false,
          params: null,
        },
        children: [
          {
            path: "codegen",
            component: "codegen/index",
            name: "Codegen",
            meta: {
              title: "代码生成",
              icon: "code",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
      {
        path: "/api",
        component: "Layout",
        name: "/api",
        meta: {
          title: "接口文档",
          icon: "api",
          hidden: false,
          alwaysShow: true,
          params: null,
        },
        children: [
          {
            path: "apifox",
            component: "demo/api/apifox",
            name: "Apifox",
            meta: {
              title: "Apifox",
              icon: "api",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
      {
        path: "/doc",
        component: "Layout",
        redirect: "https://juejin.cn/post/7228990409909108793",
        name: "/doc",
        meta: {
          title: "平台文档",
          icon: "document",
          hidden: false,
          alwaysShow: false,
          params: null,
        },
        children: [
          {
            path: "internal-doc",
            component: "demo/internal-doc",
            name: "InternalDoc",
            meta: {
              title: "document",
              icon: "document",
              hidden: false,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "https://juejin.cn/post/7228990409909108793",
            name: "Https://juejin.cn/post/7228990409909108793",
            meta: {
              title: "平台文档(外链)",
              icon: "link",
              hidden: false,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
      {
        path: "/multi-level",
        component: "Layout",
        name: "/multiLevel",
        meta: {
          title: "多级菜单",
          icon: "cascader",
          hidden: false,
          alwaysShow: true,
          params: null,
        },
        children: [
          {
            path: "multi-level1",
            component: "demo/multi-level/level1",
            name: "MultiLevel1",
            meta: {
              title: "菜单一级",
              icon: "",
              hidden: false,
              alwaysShow: true,
              params: null,
            },
            children: [
              {
                path: "multi-level2",
                component: "demo/multi-level/children/level2",
                name: "MultiLevel2",
                meta: {
                  title: "菜单二级",
                  icon: "",
                  hidden: false,
                  alwaysShow: false,
                  params: null,
                },
                children: [
                  {
                    path: "multi-level3-1",
                    component: "demo/multi-level/children/children/level3-1",
                    name: "MultiLevel31",
                    meta: {
                      title: "菜单三级-1",
                      icon: "",
                      hidden: false,
                      keepAlive: true,
                      alwaysShow: false,
                      params: null,
                    },
                  },
                  {
                    path: "multi-level3-2",
                    component: "demo/multi-level/children/children/level3-2",
                    name: "MultiLevel32",
                    meta: {
                      title: "菜单三级-2",
                      icon: "",
                      hidden: false,
                      keepAlive: true,
                      alwaysShow: false,
                      params: null,
                    },
                  },
                ],
              },
            ],
          },
        ],
      },
      {
        path: "/component",
        component: "Layout",
        name: "/component",
        meta: {
          title: "组件封装",
          icon: "menu",
          hidden: false,
          alwaysShow: false,
          params: null,
        },
        children: [
          {
            path: "curd",
            component: "demo/curd/index",
            name: "Curd",
            meta: {
              title: "增删改查",
              icon: "",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "table-select",
            component: "demo/table-select/index",
            name: "TableSelect",
            meta: {
              title: "列表选择器",
              icon: "",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "wang-editor",
            component: "demo/wang-editor",
            name: "WangEditor",
            meta: {
              title: "富文本编辑器",
              icon: "",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "upload",
            component: "demo/upload",
            name: "Upload",
            meta: {
              title: "图片上传",
              icon: "",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "dict-demo",
            component: "demo/dictionary",
            name: "DictDemo",
            meta: {
              title: "字典组件",
              icon: "",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "icon-selector",
            component: "demo/icon-selector",
            name: "IconSelector",
            meta: {
              title: "图标选择器",
              icon: "",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
      {
        path: "/route-param",
        component: "Layout",
        name: "/routeParam",
        meta: {
          title: "路由参数",
          icon: "el-icon-ElementPlus",
          hidden: false,
          alwaysShow: true,
          params: null,
        },
        children: [
          {
            path: "route-param-type1",
            component: "demo/route-param",
            name: "RouteParamType1",
            meta: {
              title: "参数(type=1)",
              icon: "el-icon-Star",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: {
                type: "1",
              },
            },
          },
          {
            path: "route-param-type2",
            component: "demo/route-param",
            name: "RouteParamType2",
            meta: {
              title: "参数(type=2)",
              icon: "el-icon-StarFilled",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: {
                type: "2",
              },
            },
          },
        ],
      },
      {
        path: "/function",
        component: "Layout",
        name: "/function",
        meta: {
          title: "功能演示",
          icon: "menu",
          hidden: false,
          alwaysShow: false,
          params: null,
        },
        children: [
          {
            path: "icon-demo",
            component: "demo/icons",
            name: "IconDemo",
            meta: {
              title: "Icons",
              icon: "el-icon-Notification",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "/function/websocket",
            component: "demo/websocket",
            name: "/function/websocket",
            meta: {
              title: "Websocket",
              icon: "",
              hidden: false,
              keepAlive: true,
              alwaysShow: false,
              params: null,
            },
          },
          {
            path: "other/:id",
            component: "demo/other",
            name: "Other/:id",
            meta: {
              title: "敬请期待...",
              icon: "",
              hidden: false,
              alwaysShow: false,
              params: null,
            },
          },
        ],
      },
    ];

    return jsonData;
    // return request<any, RouteVO[]>({
    //   url: `${MENU_BASE_URL}/routes`,
    //   method: "get",
    // });
  },

  /**
   * 获取菜单树形列表
   *
   * @param queryParams 查询参数
   * @returns 菜单树形列表
   */
  getList(queryParams: MenuQuery) {
    return request<any, MenuVO[]>({
      url: `${MENU_BASE_URL}`,
      method: "get",
      params: queryParams,
    });
  },

  /**
   * 获取菜单下拉数据源
   *
   * @returns 菜单下拉数据源
   */
  getOptions(onlyParent?: boolean) {
    return request<any, OptionType[]>({
      url: `${MENU_BASE_URL}/options`,
      method: "get",
      params: { onlyParent: onlyParent },
    });
  },

  /**
   * 获取菜单表单数据
   *
   * @param id 菜单ID
   */
  getFormData(id: string) {
    return request<any, MenuForm>({
      url: `${MENU_BASE_URL}/${id}/form`,
      method: "get",
    });
  },

  /**
   * 添加菜单
   *
   * @param data 菜单表单数据
   * @returns 请求结果
   */
  add(data: MenuForm) {
    return request({
      url: `${MENU_BASE_URL}`,
      method: "post",
      data: data,
    });
  },

  /**
   * 修改菜单
   *
   * @param id 菜单ID
   * @param data 菜单表单数据
   * @returns 请求结果
   */
  update(id: string, data: MenuForm) {
    return request({
      url: `${MENU_BASE_URL}/${id}`,
      method: "put",
      data: data,
    });
  },

  /**
   * 删除菜单
   *
   * @param id 菜单ID
   * @returns 请求结果
   */
  deleteById(id: number) {
    return request({
      url: `${MENU_BASE_URL}/${id}`,
      method: "delete",
    });
  },
};

export default MenuAPI;

import type { MenuTypeEnum } from "@/enums/MenuTypeEnum";

/** 菜单查询参数 */
export interface MenuQuery {
  /** 搜索关键字 */
  keywords?: string;
}

/** 菜单视图对象 */
export interface MenuVO {
  /** 子菜单 */
  children?: MenuVO[];
  /** 组件路径 */
  component?: string;
  /** ICON */
  icon?: string;
  /** 菜单ID */
  id?: string;
  /** 菜单名称 */
  name?: string;
  /** 父菜单ID */
  parentId?: string;
  /** 按钮权限标识 */
  perm?: string;
  /** 跳转路径 */
  redirect?: string;
  /** 路由名称 */
  routeName?: string;
  /** 路由相对路径 */
  routePath?: string;
  /** 菜单排序(数字越小排名越靠前) */
  sort?: number;
  /** 菜单 */
  type?: MenuTypeEnum;
  /** 菜单是否可见(1:显示;0:隐藏) */
  visible?: number;
}

/** 菜单表单对象 */
export interface MenuForm {
  /** 菜单ID */
  id?: string;
  /** 父菜单ID */
  parentId?: string;
  /** 菜单名称 */
  name?: string;
  /** 菜单是否可见(1-是 0-否) */
  visible: number;
  /** ICON */
  icon?: string;
  /** 排序 */
  sort?: number;
  /** 路由名称 */
  routeName?: string;
  /** 路由路径 */
  routePath?: string;
  /** 组件路径 */
  component?: string;
  /** 跳转路由路径 */
  redirect?: string;
  /** 菜单 */
  type?: MenuTypeEnum;
  /** 权限标识 */
  perm?: string;
  /** 【菜单】是否开启页面缓存 */
  keepAlive?: number;
  /** 【目录】只有一个子路由是否始终显示 */
  alwaysShow?: number;
  /** 参数 */
  params?: KeyValue[];
}

interface KeyValue {
  key: string;
  value: string;
}

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
