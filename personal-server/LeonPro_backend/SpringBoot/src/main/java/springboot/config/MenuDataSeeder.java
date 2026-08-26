package springboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import springboot.domain.SysMenus;
import springboot.service.SysMenusService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 菜单种子数据初始化
 *
 * 首次启动（sys_menus 表为空）时，将当前系统全部路由写入数据库，
 * 前端「路由配置」模块与动态路由均以数据库为准。
 */
@Slf4j
@Component
public class MenuDataSeeder implements CommandLineRunner {

    private final SysMenusService sysMenusService;

    public MenuDataSeeder(SysMenusService sysMenusService) {
        this.sysMenusService = sysMenusService;
    }

    @Override
    public void run(String... args) {
        // 幂等修复：保证默认菜单的排序与关键字段在每次启动时对齐
        ensureSeedSortOrder();

        long count = sysMenusService.count();
        if (count > 0) {
            return;
        }

        Date now = new Date();
        List<SysMenus> menus = new ArrayList<>();

        // ============ 工具中心（1） ============
        menus.add(build("menu_tool", "0", "工具中心", "/tool", "Layout", "/tool/trace", null,
                "api", 1, 0, 1, 0, 0, null, now));
        menus.add(build("menu_trace", "menu_tool", "轨迹分析", "trace", "tool/trace/index", null,
                "Trace", "code", 1, 1, 1, 0, 0, null, now));
        menus.add(build("menu_files", "menu_tool", "文件工具", "files", "tool/files/index", null,
                "Files", "document", 2, 1, 1, 0, 0, null, now));
        menus.add(build("menu_documents", "menu_tool", "文档工具", "documents", "tool/documents/index", null,
                "Documents", "file", 3, 1, 1, 0, 0, null, now));

        // ============ 业务管理（2） ============
        menus.add(build("menu_work", "0", "业务管理", "/work", "Layout", "/work/tasks", null,
                "todo", 2, 0, 1, 0, 0, null, now));
        menus.add(build("menu_tasks", "menu_work", "任务管理", "tasks", "work/tasks/index", null,
                "Tasks", "todo", 1, 1, 1, 0, 1, null, now));
        menus.add(build("menu_registration", "menu_work", "注册申请", "registration", "work/registration/index", null,
                "Registration", "client", 2, 1, 1, 0, 1, null, now));

        // ============ 系统管理（3） ============
        menus.add(build("menu_system", "0", "系统管理", "/system", "Layout", "/system/user", null,
                "system", 3, 0, 1, 0, 0, null, now));
        menus.add(build("menu_user", "menu_system", "用户管理", "user", "system/user/index", null,
                "User", "role", 1, 1, 1, 0, 1, null, now));
        menus.add(build("menu_role", "menu_system", "角色管理", "role", "system/role/index", null,
                "Role", "role", 2, 1, 1, 0, 1, null, now));
        menus.add(build("menu_menu", "menu_system", "路由配置", "menu", "system/menu/index", null,
                "Menu", "menu", 3, 1, 1, 0, 1, null, now));

        sysMenusService.saveBatch(menus);
        log.info("sys_menus 为空，已初始化 {} 条默认路由配置。", menus.size());
    }

    /**
     * 幂等修复默认菜单的排序字段（仅更新已知种子 ID，不影响用户自定义菜单）
     */
    private void ensureSeedSortOrder() {
        updateSort("menu_tool", 1);
        updateSort("menu_work", 2);
        updateSort("menu_system", 3);
        updateSort("menu_trace", 1);
        updateSort("menu_files", 2);
        updateSort("menu_documents", 3);
        updateSort("menu_tasks", 1);
        updateSort("menu_registration", 2);
        updateSort("menu_user", 1);
        updateSort("menu_role", 2);
        updateSort("menu_menu", 3);
    }

    private void updateSort(String id, Integer sortOrder) {
        SysMenus menu = sysMenusService.getById(id);
        if (menu != null && !sortOrder.equals(menu.getSortOrder())) {
            menu.setSortOrder(sortOrder);
            sysMenusService.updateById(menu);
        }
    }

    private SysMenus build(String id, String parentId, String menuName, String menuUrl,
                           String component, String redirect, String routeName, String icon,
                           Integer sortOrder, Integer menuType, Integer visible,
                           Integer alwaysShow, Integer keepAlive, String permission, Date now) {
        SysMenus menu = new SysMenus();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(menuName);
        menu.setMenuUrl(menuUrl);
        menu.setComponent(component);
        menu.setRedirect(redirect);
        menu.setRouteName(routeName);
        menu.setIcon(icon);
        menu.setSortOrder(sortOrder);
        menu.setMenuType(menuType);
        menu.setVisible(visible);
        menu.setAlwaysShow(alwaysShow);
        menu.setKeepAlive(keepAlive);
        menu.setPermission(permission);
        menu.setCreateTime(now);
        return menu;
    }
}
