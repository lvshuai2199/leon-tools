import request from "@/utils/request";

const MENU_BASE_URL = "/sysMenus";

/**
 * 菜单 / 路由 API
 *
 * - getRoutes(): 从后端 /auth/getMenuList 拉取菜单数据并映射为 RouteVO 树，
 *   供 permission store 动态注册路由（侧边栏由数据库驱动）
 * - CRUD: 路由配置模块增删改查，对应 sys_menus 表
 */
const MenuAPI = {
  /**
   * 获取路由配置树（供 permission store 注册动态路由）
   */
  getRoutes() {
    return request<any, SysMenuVO[]>({
      url: "/auth/getMenuList",
      method: "get",
    }).then((list) => buildRouteTree(list || []));
  },

  /**
   * 获取全部菜单（扁平列表，按 sortOrder 排序），路由配置模块使用
   */
  getList() {
    return request<any, SysMenuVO[]>({
      url: `${MENU_BASE_URL}/list`,
      method: "get",
    });
  },

  /** 新增菜单 */
  add(data: MenuForm) {
    return request<any, boolean>({
      url: `${MENU_BASE_URL}/add`,
      method: "post",
      data,
    });
  },

  /** 修改菜单 */
  update(data: MenuForm) {
    return request<any, boolean>({
      url: `${MENU_BASE_URL}/update`,
      method: "post",
      data,
    });
  },

  /** 批量删除菜单（RequestBody 传 ID 数组） */
  del(ids: string[]) {
    return request<any, boolean>({
      url: `${MENU_BASE_URL}/del`,
      method: "post",
      data: ids,
    });
  },
};

/**
 * 将后端扁平菜单列表映射为前端路由树（RouteVO）
 *
 * 转换规则：
 * - menuType 0 目录 → component=Layout，name 取 routeName 或 path
 * - menuType 1 菜单 → component=component 字段（相对 src/views/ 的路径）
 * - menuType 2 按钮 → 不参与路由
 * - visible=0 → meta.hidden=true
 */
function buildRouteTree(menus: SysMenuVO[]): RouteVO[] {
  const nodes = (menus || []).filter((menu) => menu.menuType !== 2);
  const map = new Map<string, RouteVO>();
  const roots: RouteVO[] = [];

  nodes.forEach((menu) => {
    const isCatalog = menu.menuType === 0;
    map.set(menu.id!, {
      path: menu.menuUrl,
      component: isCatalog ? "Layout" : menu.component,
      redirect: menu.redirect || undefined,
      name:
        menu.routeName ||
        (isCatalog ? menu.menuUrl : capitalizeFirst(menu.menuUrl || "")),
      meta: {
        title: menu.menuName,
        icon: menu.icon,
        hidden: (menu.visible ?? 1) === 0,
        keepAlive: menu.keepAlive === 1,
        alwaysShow: menu.alwaysShow === 1,
      },
      children: [],
    });
  });

  nodes.forEach((menu) => {
    const node = map.get(menu.id!);
    const parent = menu.parentId && map.get(menu.parentId);
    if (parent) {
      parent.children!.push(node!);
    } else {
      roots.push(node!);
    }
  });

  return roots;
}

function capitalizeFirst(value: string) {
  if (!value) return value;
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export default MenuAPI;

/** 菜单 / 路由记录（SysMenus 映射） */
export interface SysMenuVO {
  id?: string;
  /** 菜单名称 */
  menuName?: string;
  /** 菜单链接（路由路径） */
  menuUrl?: string;
  /** 父级ID，顶级为 "0" */
  parentId?: string;
  /** 排序 */
  sortOrder?: number;
  /** 图标 */
  icon?: string;
  /** 是否显示 1显示 0隐藏 */
  visible?: number;
  /** 目录类型 0目录 1菜单 2按钮 */
  menuType?: number;
  /** 权限标识 */
  permission?: string;
  /** 组件路径（相对 src/views/，如 tool/trace/index） */
  component?: string;
  /** 路由名称（如 Trace） */
  routeName?: string;
  /** 是否缓存页面 1开启 0关闭 */
  keepAlive?: number;
  /** 始终显示 1是 0否 */
  alwaysShow?: number;
  /** 目录跳转地址 */
  redirect?: string;
  createTime?: string;
  updateTime?: string;
  /** 树形子节点（前端组装） */
  children?: SysMenuVO[];
}

/** 菜单表单 */
export interface MenuForm {
  id?: string;
  parentId?: string;
  menuName?: string;
  menuUrl?: string;
  sortOrder?: number;
  icon?: string;
  visible?: number;
  menuType?: number;
  permission?: string;
  component?: string;
  routeName?: string;
  keepAlive?: number;
  alwaysShow?: number;
  redirect?: string;
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
