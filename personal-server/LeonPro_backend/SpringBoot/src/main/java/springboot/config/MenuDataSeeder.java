package springboot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import springboot.domain.SysRoleMenu;
import springboot.service.RegCodeAccessService;
import springboot.service.SysMenusService;
import springboot.service.SysRoleMenuService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单相关的角色补授权（原菜单种子）。
 * <p>
 * 菜单行本身已改由页面清单同步（{@link MenuManifestSync}，清单 menus.json 打进 jar）负责插入和更新，
 * 这里不再插入任何菜单；只保留原来的补授权，并且只授予确实存在的菜单，避免产生指向不存在菜单的授权行。
 * 菜单 id 常量仍在这里定义，供权限判断引用（这些 id 必须保持不变）。
 * <p>
 * 顺序：放在菜单清单同步（Order 5）之前执行，这样首次授权（按“已有 menu_regcode / menu_crab 的角色”授用户端菜单）
 * 能看到这里补齐后的授权。
 */
@Slf4j
@Component
@Order(4)
public class MenuDataSeeder implements CommandLineRunner {

    public static final String MENU_REGCODE_CENTER = "menu_regcode_center";
    public static final String MENU_REGCODE = "menu_regcode";
    public static final String MENU_REGCODE_CONFIG = "menu_regcode_config";
    public static final String MENU_REGCODE_USER = "menu_regcode_user";
    public static final String MENU_REGISTRATION = "menu_registration";
    public static final String MENU_CRAB = "menu_crab";
    public static final String MENU_BADMINTON = "menu_badminton";
    public static final String MENU_TASKS = "menu_tasks";
    public static final String MENU_MINDMAP = "menu_mindmap";

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
        grantRegCodeMenusToManagers();
        grantCrabMenuToBusinessRoles();
        grantBadmintonMenuToBusinessRoles();
    }

    private boolean menuExists(String menuId) {
        return sysMenusService.getById(menuId) != null;
    }

    /** 已有任务管理菜单的业务角色，补插螃蟹出货；注册码客户除外。 */
    private void grantCrabMenuToBusinessRoles() {
        if (!menuExists(MENU_CRAB)) {
            return;
        }
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getMenuId, MENU_TASKS);
        Set<String> roleIds = new HashSet<>();
        for (SysRoleMenu row : sysRoleMenuService.list(wrapper)) {
            if (row.getRoldId() != null) {
                roleIds.add(row.getRoldId());
            }
        }
        for (String roleId : roleIds) {
            if (RegCodeAccessService.ROLE_REGCODE_CLIENT_ID.equals(roleId)) {
                continue;
            }
            List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(roleId);
            Set<String> owned = menuIds == null ? new HashSet<>() : new HashSet<>(menuIds);
            if (owned.contains(MENU_CRAB)) {
                continue;
            }
            SysRoleMenu extra = new SysRoleMenu();
            extra.setRoldId(roleId);
            extra.setMenuId(MENU_CRAB);
            sysRoleMenuService.save(extra);
            log.info("已为角色 {} 补齐菜单 {}。", roleId, MENU_CRAB);
        }
    }

    /** 已有任务管理、螃蟹出货或思维导图的业务角色，补插羽毛球计费；注册码客户除外。 */
    private void grantBadmintonMenuToBusinessRoles() {
        if (!menuExists(MENU_BADMINTON)) {
            return;
        }
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysRoleMenu::getMenuId, List.of(MENU_TASKS, MENU_CRAB, MENU_MINDMAP));
        Set<String> roleIds = new HashSet<>();
        for (SysRoleMenu row : sysRoleMenuService.list(wrapper)) {
            if (row.getRoldId() != null) {
                roleIds.add(row.getRoldId());
            }
        }
        for (String roleId : roleIds) {
            if (RegCodeAccessService.ROLE_REGCODE_CLIENT_ID.equals(roleId)) {
                continue;
            }
            List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(roleId);
            Set<String> owned = menuIds == null ? new HashSet<>() : new HashSet<>(menuIds);
            if (owned.contains(MENU_BADMINTON)) {
                continue;
            }
            SysRoleMenu extra = new SysRoleMenu();
            extra.setRoldId(roleId);
            extra.setMenuId(MENU_BADMINTON);
            sysRoleMenuService.save(extra);
            log.info("已为角色 {} 补齐菜单 {}。", roleId, MENU_BADMINTON);
        }
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
        if (roleIds.isEmpty()) {
            return;
        }
        List<String> existing = REGCODE_MENU_IDS.stream().filter(this::menuExists).toList();
        for (String roleId : roleIds) {
            if (RegCodeAccessService.ROLE_REGCODE_CLIENT_ID.equals(roleId)) {
                continue;
            }
            List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(roleId);
            Set<String> owned = menuIds == null ? new HashSet<>() : new HashSet<>(menuIds);
            for (String menuId : existing) {
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
}
