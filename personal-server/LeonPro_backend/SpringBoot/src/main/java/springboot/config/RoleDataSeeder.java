package springboot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import springboot.domain.SysRoleMenu;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.RoleUtils;

import java.util.Date;
import java.util.List;

/**
 * 保证系统始终只有一条内置 ROOT（id=role_root）。
 * 历史库里用 UUID 建过同名 ROOT 的，启动时合并过来，不重复插入。
 */
@Slf4j
@Component
@Order(1)
public class RoleDataSeeder implements CommandLineRunner {

    private final SysRolesService sysRolesService;
    private final SysUsersService sysUsersService;
    private final SysRoleMenuService sysRoleMenuService;
    private final JdbcTemplate jdbcTemplate;

    public RoleDataSeeder(SysRolesService sysRolesService, SysUsersService sysUsersService,
                          SysRoleMenuService sysRoleMenuService, JdbcTemplate jdbcTemplate) {
        this.sysRolesService = sysRolesService;
        this.sysUsersService = sysUsersService;
        this.sysRoleMenuService = sysRoleMenuService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        SysRoles root = ensureCanonicalRoot();
        mergeDuplicateRoots(root.getId());
        assignRootToUnscopedAdmins(root.getId());
        ensureAdminUser(root.getId());
    }

    private SysRoles ensureCanonicalRoot() {
        SysRoles root = sysRolesService.getById(RoleUtils.ROOT_ROLE_ID);
        if (root == null) {
            jdbcTemplate.update(
                    "INSERT INTO sys_roles (id, role_name, description, is_disabled, create_time) VALUES (?, ?, ?, ?, ?)",
                    RoleUtils.ROOT_ROLE_ID,
                    RoleUtils.ROOT_ROLE_NAME,
                    "系统内置超级管理员，默认拥有全部路由权限，不可配置",
                    0,
                    new Date());
            log.info("已初始化系统默认角色 ROOT（id={}）。", RoleUtils.ROOT_ROLE_ID);
            return sysRolesService.getById(RoleUtils.ROOT_ROLE_ID);
        }
        boolean changed = false;
        if (!RoleUtils.ROOT_ROLE_NAME.equals(root.getRoleName())) {
            root.setRoleName(RoleUtils.ROOT_ROLE_NAME);
            changed = true;
        }
        if (root.getIsDisabled() != null && root.getIsDisabled() != 0) {
            root.setIsDisabled(0);
            changed = true;
        }
        if (changed) {
            sysRolesService.updateById(root);
        }
        return root;
    }

    /** 把其它名为 ROOT 的角色（旧 UUID）并入 role_root 后删除 */
    private void mergeDuplicateRoots(String canonicalId) {
        List<SysRoles> extras = sysRolesService.list().stream()
                .filter(RoleUtils::isRoot)
                .filter(r -> r.getId() != null && !canonicalId.equals(r.getId()))
                .toList();
        for (SysRoles extra : extras) {
            LambdaQueryWrapper<SysUsers> users = new LambdaQueryWrapper<>();
            users.eq(SysUsers::getRoleId, extra.getId());
            List<SysUsers> bound = sysUsersService.list(users);
            for (SysUsers user : bound) {
                user.setRoleId(canonicalId);
                sysUsersService.updateById(user);
            }
            LambdaQueryWrapper<SysRoleMenu> menus = new LambdaQueryWrapper<>();
            menus.eq(SysRoleMenu::getRoldId, extra.getId());
            sysRoleMenuService.remove(menus);
            sysRolesService.removeById(extra.getId());
            log.info("已合并重复 ROOT 角色 {} 到 {}，迁移用户 {} 个。", extra.getId(), canonicalId, bound.size());
        }
    }

    /** 未绑定角色的 admin / root 账号自动挂上 ROOT */
    private void assignRootToUnscopedAdmins(String rootRoleId) {
        LambdaQueryWrapper<SysUsers> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUsers::getUsername, List.of("admin", "root"));
        wrapper.and(w -> w.isNull(SysUsers::getRoleId).or().eq(SysUsers::getRoleId, ""));
        List<SysUsers> users = sysUsersService.list(wrapper);
        for (SysUsers user : users) {
            user.setRoleId(rootRoleId);
            sysUsersService.updateById(user);
            log.info("已为用户 {} 绑定系统默认角色 ROOT。", user.getUsername());
        }
    }

    /** 仅当不存在 username=admin 时插入一次，不改已有账号的密码或角色 */
    private void ensureAdminUser(String rootRoleId) {
        LambdaQueryWrapper<SysUsers> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUsers::getUsername, "admin");
        if (sysUsersService.count(wrapper) > 0) {
            return;
        }
        SysUsers admin = new SysUsers();
        admin.setUsername("admin");
        admin.setNickname("管理员");
        admin.setPassword("admin123");
        admin.setEmail("admin@leonpro.local");
        admin.setRoleId(rootRoleId);
        admin.setCreateTime(new Date());
        sysUsersService.save(admin);
        log.info("库中无管理员，已创建默认账号 admin。");
    }
}
