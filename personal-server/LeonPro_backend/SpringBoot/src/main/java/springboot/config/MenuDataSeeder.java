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
        long count = sysMenusService.count();
        if (count == 0) {
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
        menus.add(build("menu_mindmap", "menu_tool", "思维导图", "mindmap", "tool/mindmap/index", null,
                "Mindmap", "share", 4, 1, 1, 0, 0, null, now));
        menus.add(build("menu_regcode", "menu_tool", "注册码生成", "regcode", "tool/regcode/index", null,
                "RegCode", "key", 5, 1, 1, 0, 0, null, now));
        menus.add(build("menu_regcode_config", "menu_tool", "注册码配置", "regcode-config", "tool/regcode-config/index", null,
                "RegCodeConfig", "setting", 6, 1, 1, 0, 0, null, now));

        // ============ 业务管理（2） ============
        menus.add(build("menu_work", "0", "业务管理", "/work", "Layout", "/work/tasks", null,
                "todo", 2, 0, 1, 0, 0, null, now));
        menus.add(build("menu_tasks", "menu_work", "任务管理", "tasks", "work/tasks/index", null,
                "Tasks", "todo", 1, 1, 1, 0, 1, null, now));
        menus.add(build("menu_registration", "menu_work", "注册码记录", "registration", "work/registration/index", null,
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
        menus.add(build("menu_oplog", "menu_system", "操作日志", "oplog", "system/oplog/index", null,
                "Oplog", "document", 4, 1, 1, 0, 1, null, now));

        sysMenusService.saveBatch(menus);
        log.info("sys_menus 为空，已初始化 {} 条默认路由配置。", menus.size());
        return;
        }

        // 表已有数据：只补缺失的种子菜单，不改已有记录
        ensureSeedMenu();
    }

    /**
     * 仅当种子菜单不存在时插入，不覆盖已有记录。
     */
    private void ensureSeedMenu() {
        SysMenus mindmap = sysMenusService.getById("menu_mindmap");
        if (mindmap == null) {
            mindmap = build("menu_mindmap", "menu_tool", "思维导图", "mindmap",
                    "tool/mindmap/index", null, "Mindmap", "share", 4, 1, 1, 0, 0, null, new Date());
            sysMenusService.save(mindmap);
            log.info("已补插种子菜单 menu_mindmap（思维导图）。");
        }
        SysMenus regcode = sysMenusService.getById("menu_regcode");
        if (regcode == null) {
            regcode = build("menu_regcode", "menu_tool", "注册码生成", "regcode",
                    "tool/regcode/index", null, "RegCode", "key", 5, 1, 1, 0, 0, null, new Date());
            sysMenusService.save(regcode);
            log.info("已补插种子菜单 menu_regcode（注册码生成）。");
        }
        SysMenus regcodeConfig = sysMenusService.getById("menu_regcode_config");
        if (regcodeConfig == null) {
            regcodeConfig = build("menu_regcode_config", "menu_tool", "注册码配置", "regcode-config",
                    "tool/regcode-config/index", null, "RegCodeConfig", "setting", 6, 1, 1, 0, 0, null, new Date());
            sysMenusService.save(regcodeConfig);
            log.info("已补插种子菜单 menu_regcode_config（注册码配置）。");
        }
        SysMenus registration = sysMenusService.getById("menu_registration");
        if (registration == null) {
            registration = build("menu_registration", "menu_work", "注册码记录", "registration",
                    "work/registration/index", null, "Registration", "client", 2, 1, 1, 0, 1, null, new Date());
            sysMenusService.save(registration);
            log.info("已补插种子菜单 menu_registration（注册码记录）。");
        } else if ("操作日志".equals(registration.getMenuName())) {
            registration.setMenuName("注册码记录");
            sysMenusService.updateById(registration);
            log.info("已将 menu_registration 名称更正为注册码记录。");
        }
        SysMenus oplog = sysMenusService.getById("menu_oplog");
        if (oplog == null) {
            oplog = build("menu_oplog", "menu_system", "操作日志", "oplog",
                    "system/oplog/index", null, "Oplog", "document", 4, 1, 1, 0, 1, null, new Date());
            sysMenusService.save(oplog);
            log.info("已补插种子菜单 menu_oplog（操作日志）。");
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
