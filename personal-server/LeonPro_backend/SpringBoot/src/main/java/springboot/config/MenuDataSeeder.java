package springboot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import springboot.domain.SysMenus;
import springboot.domain.SysRoleMenu;
import springboot.service.SysMenusService;
import springboot.service.SysRoleMenuService;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单种子数据初始化
 *
 * 首次启动（sys_menus 表为空）时，将当前系统全部路由写入数据库，
 * 前端「路由配置」模块与动态路由均以数据库为准。
 */
@Slf4j
@Component
public class MenuDataSeeder implements CommandLineRunner {

    public static final String MENU_REGCODE_CENTER = "menu_regcode_center";
    public static final String MENU_REGCODE = "menu_regcode";
    public static final String MENU_REGCODE_CONFIG = "menu_regcode_config";
    public static final String MENU_REGCODE_USER = "menu_regcode_user";
    public static final String MENU_REGISTRATION = "menu_registration";

    private static final List<String> REGCODE_MENU_IDS = List.of(
            MENU_REGCODE_CENTER,
            MENU_REGCODE,
            MENU_REGCODE_CONFIG,
            MENU_REGCODE_USER,
            MENU_REGISTRATION
    );

    private final SysMenusService sysMenusService;
    private final SysRoleMenuService sysRoleMenuService;

    public MenuDataSeeder(SysMenusService sysMenusService, SysRoleMenuService sysRoleMenuService) {
        this.sysMenusService = sysMenusService;
        this.sysRoleMenuService = sysRoleMenuService;
    }

    @Override
    public void run(String... args) {
        long count = sysMenusService.count();
        if (count == 0) {
            Date now = new Date();
            List<SysMenus> menus = new ArrayList<>();

            menus.add(build("menu_tool", "0", "工具中心", "/tool", "Layout", "/tool/trace", null,
                    "api", 1, 0, 1, 0, 0, null, now));
            menus.add(build("menu_trace", "menu_tool", "轨迹分析", "trace", "tool/trace/index", null,
                    "Trace", "code", 1, 1, 1, 0, 0, null, now));
            menus.add(build("menu_files", "menu_tool", "文件工具", "files", "tool/files/index", null,
                    "Files", "document", 2, 1, 1, 0, 0, null, now));
            menus.add(build("menu_documents", "menu_tool", "文档工具", "documents", "tool/documents/index", null,
                    "Documents", "file", 3, 1, 1, 0, 0, null, now));
            menus.add(build("menu_mindmap", "menu_tool", "思维导图", "mindmap", "tool/mindmap/index", null,
                    "Mindmap", "share", 4, 1, 1, 0, 0, null, now));

            menus.add(build(MENU_REGCODE_CENTER, "0", "注册码", "/regcode", "Layout", "/regcode/generate",
                    "RegCodeCenter", "key", 2, 0, 1, 1, 0, null, now));
            menus.add(build(MENU_REGCODE, MENU_REGCODE_CENTER, "注册码生成", "generate", "tool/regcode/index", null,
                    "RegCode", "key", 1, 1, 1, 0, 0, null, now));
            menus.add(build(MENU_REGCODE_CONFIG, MENU_REGCODE_CENTER, "注册码配置", "config", "tool/regcode-config/index", null,
                    "RegCodeConfig", "setting", 2, 1, 1, 0, 0, null, now));
            menus.add(build(MENU_REGCODE_USER, MENU_REGCODE_CENTER, "注册码用户", "user", "tool/regcode-user/index", null,
                    "RegCodeUser", "user", 3, 1, 1, 0, 1, null, now));
            menus.add(build(MENU_REGISTRATION, MENU_REGCODE_CENTER, "注册码记录", "records", "work/registration/index", null,
                    "Registration", "client", 4, 1, 1, 0, 1, null, now));

            menus.add(build("menu_work", "0", "业务管理", "/work", "Layout", "/work/tasks", null,
                    "todo", 3, 0, 1, 0, 0, null, now));
            menus.add(build("menu_tasks", "menu_work", "任务管理", "tasks", "work/tasks/index", null,
                    "Tasks", "todo", 1, 1, 1, 0, 1, null, now));

            menus.add(build("menu_system", "0", "系统管理", "/system", "Layout", "/system/user", null,
                    "system", 4, 0, 1, 0, 0, null, now));
            menus.add(build("menu_user", "menu_system", "用户管理", "user", "system/user/index", null,
                    "User", "role", 1, 1, 1, 0, 1, null, now));
            menus.add(build("menu_role", "menu_system", "角色管理", "role", "system/role/index", null,
                    "Role", "role", 2, 1, 1, 0, 1, null, now));
            menus.add(build("menu_menu", "menu_system", "路由配置", "menu", "system/menu/index", null,
                    "Menu", "menu", 3, 1, 1, 0, 1, null, now));
            menus.add(build("menu_oplog", "menu_system", "操作日志", "oplog", "system/oplog/index", null,
                    "Oplog", "document", 4, 1, 1, 0, 1, null, now));

            sysMenusService.saveBatch(menus);
            log.info("sys_menus 为空，已初始化 {} 条默认路由配置。", menus.size());
            return;
        }

        ensureSeedMenu();
        groupRegCodeMenus();
        grantRegCodeMenusToManagers();
    }

    private void ensureSeedMenu() {
        Date now = new Date();
        if (sysMenusService.getById("menu_mindmap") == null) {
            sysMenusService.save(build("menu_mindmap", "menu_tool", "思维导图", "mindmap",
                    "tool/mindmap/index", null, "Mindmap", "share", 4, 1, 1, 0, 0, null, now));
            log.info("已补插种子菜单 menu_mindmap（思维导图）。");
        }
        if (sysMenusService.getById("menu_oplog") == null) {
            sysMenusService.save(build("menu_oplog", "menu_system", "操作日志", "oplog",
                    "system/oplog/index", null, "Oplog", "document", 4, 1, 1, 0, 1, null, now));
            log.info("已补插种子菜单 menu_oplog（操作日志）。");
        }
    }

    /** 把注册码相关菜单收拢到同一目录下，并补齐缺失项 */
    private void groupRegCodeMenus() {
        Date now = new Date();
        upsert(MENU_REGCODE_CENTER, "0", "注册码", "/regcode", "Layout", "/regcode/generate",
                "RegCodeCenter", "key", 2, 0, 1, 1, 0, now);
        upsert(MENU_REGCODE, MENU_REGCODE_CENTER, "注册码生成", "generate", "tool/regcode/index",
                null, "RegCode", "key", 1, 1, 1, 0, 0, now);
        upsert(MENU_REGCODE_CONFIG, MENU_REGCODE_CENTER, "注册码配置", "config", "tool/regcode-config/index",
                null, "RegCodeConfig", "setting", 2, 1, 1, 0, 0, now);
        upsert(MENU_REGCODE_USER, MENU_REGCODE_CENTER, "注册码用户", "user", "tool/regcode-user/index",
                null, "RegCodeUser", "user", 3, 1, 1, 0, 1, now);
        upsert(MENU_REGISTRATION, MENU_REGCODE_CENTER, "注册码记录", "records", "work/registration/index",
                null, "Registration", "client", 4, 1, 1, 0, 1, now);
    }

    private void upsert(String id, String parentId, String menuName, String menuUrl, String component,
                        String redirect, String routeName, String icon, Integer sortOrder,
                        Integer menuType, Integer visible, Integer alwaysShow, Integer keepAlive, Date now) {
        SysMenus menu = sysMenusService.getById(id);
        if (menu == null) {
            sysMenusService.save(build(id, parentId, menuName, menuUrl, component, redirect,
                    routeName, icon, sortOrder, menuType, visible, alwaysShow, keepAlive, null, now));
            log.info("已补插种子菜单 {}（{}）。", id, menuName);
            return;
        }
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
        sysMenusService.updateById(menu);
    }

    /** 已有任一注册码菜单的角色，补齐整组（含目录和注册码用户） */
    private void grantRegCodeMenusToManagers() {
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysRoleMenu::getMenuId, List.of(MENU_REGCODE, MENU_REGCODE_CONFIG, MENU_REGCODE_USER, MENU_REGISTRATION));
        Set<String> roleIds = new HashSet<>();
        for (SysRoleMenu row : sysRoleMenuService.list(wrapper)) {
            if (row.getRoldId() != null) {
                roleIds.add(row.getRoldId());
            }
        }
        for (String roleId : roleIds) {
            List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(roleId);
            Set<String> owned = menuIds == null ? new HashSet<>() : new HashSet<>(menuIds);
            for (String menuId : REGCODE_MENU_IDS) {
                if (owned.contains(menuId)) {
                    continue;
                }
                SysRoleMenu extra = new SysRoleMenu();
                extra.setRoldId(roleId);
                extra.setMenuId(menuId);
                sysRoleMenuService.save(extra);
                log.info("已为角色 {} 补齐菜单 {}。", roleId, menuId);
            }
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
