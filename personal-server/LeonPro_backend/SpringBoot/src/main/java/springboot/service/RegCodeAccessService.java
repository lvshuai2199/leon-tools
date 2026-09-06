package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.DTO.RegCodeQuotaVO;
import springboot.domain.RegCodeUser;
import springboot.domain.RegCodeUserConfig;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.utils.RoleUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 注册码权限：管理员可用全部配置且不限次数；客户仅能使用分配的配置并受配额约束。
 */
@Service
public class RegCodeAccessService {

    public static final String MENU_REGCODE_USER = "menu_regcode_user";
    public static final String MENU_REGCODE_CONFIG = "menu_regcode_config";
    public static final String ROLE_REGCODE_CLIENT_ID = "role_regcode_client";

    private final SysUsersService sysUsersService;
    private final SysRolesService sysRolesService;
    private final SysRoleMenuService sysRoleMenuService;
    private final RegCodeUserService regCodeUserService;
    private final RegCodeUserConfigService regCodeUserConfigService;

    public RegCodeAccessService(SysUsersService sysUsersService,
                                SysRolesService sysRolesService,
                                SysRoleMenuService sysRoleMenuService,
                                RegCodeUserService regCodeUserService,
                                RegCodeUserConfigService regCodeUserConfigService) {
        this.sysUsersService = sysUsersService;
        this.sysRolesService = sysRolesService;
        this.sysRoleMenuService = sysRoleMenuService;
        this.regCodeUserService = regCodeUserService;
        this.regCodeUserConfigService = regCodeUserConfigService;
    }

    public boolean isClient(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        SysUsers user = sysUsersService.getById(userId);
        return user != null && ROLE_REGCODE_CLIENT_ID.equals(user.getRoleId());
    }

    public boolean isManager(String userId) {
        if (userId == null || userId.isBlank() || isClient(userId)) {
            return false;
        }
        SysUsers user = sysUsersService.getById(userId);
        if (user == null || user.getRoleId() == null || user.getRoleId().isBlank()) {
            return false;
        }
        SysRoles role = sysRolesService.getById(user.getRoleId());
        if (RoleUtils.isRoot(role)) {
            return true;
        }
        List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(user.getRoleId());
        return menuIds != null && (menuIds.contains(MENU_REGCODE_USER) || menuIds.contains(MENU_REGCODE_CONFIG));
    }

    public String requireManager(String userId) {
        if (userId == null || userId.isBlank()) {
            return "请先登录";
        }
        if (!isManager(userId)) {
            return "无权限管理注册码用户或配置";
        }
        return null;
    }

    public RegCodeUser getAssignment(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<RegCodeUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegCodeUser::getUserId, userId).last("LIMIT 1");
        return regCodeUserService.getOne(wrapper, false);
    }

    public List<String> listAssignedConfigIds(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<RegCodeUserConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegCodeUserConfig::getUserId, userId);
        return regCodeUserConfigService.list(wrapper).stream()
                .map(RegCodeUserConfig::getConfigId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * @return null 表示管理员可用全部；空列表表示无权；否则仅这些配置
     */
    public List<String> allowedConfigIds(String userId) {
        if (getAssignment(userId) != null) {
            return listAssignedConfigIds(userId);
        }
        if (isManager(userId)) {
            return null;
        }
        return listAssignedConfigIds(userId);
    }

    public String assertCanGenerate(String userId, String configId) {
        if (userId == null || userId.isBlank()) {
            return "请先登录";
        }
        RegCodeUser assignment = getAssignment(userId);
        if (assignment != null) {
            if (configId == null || configId.isBlank()) {
                return "请选择注册码配置";
            }
            List<String> configIds = listAssignedConfigIds(userId);
            if (!configIds.contains(configId)) {
                return "无权使用该注册码配置";
            }
            int used = assignment.getGenerateUsed() == null ? 0 : assignment.getGenerateUsed();
            int limit = assignment.getGenerateLimit() == null ? 0 : assignment.getGenerateLimit();
            if (used >= limit) {
                return "生成次数已用完";
            }
            return null;
        }
        if (isManager(userId)) {
            return null;
        }
        return "未分配注册码生成权限";
    }

    public RegCodeQuotaVO quotaOf(String userId) {
        RegCodeQuotaVO vo = new RegCodeQuotaVO();
        RegCodeUser assignment = getAssignment(userId);
        if (assignment != null) {
            vo.setUnlimited(false);
            int used = assignment.getGenerateUsed() == null ? 0 : assignment.getGenerateUsed();
            int limit = assignment.getGenerateLimit() == null ? 0 : assignment.getGenerateLimit();
            vo.setGenerateUsed(used);
            vo.setGenerateLimit(limit);
            vo.setRemaining(Math.max(limit - used, 0));
            return vo;
        }
        if (isManager(userId)) {
            vo.setUnlimited(true);
            return vo;
        }
        vo.setUnlimited(false);
        vo.setGenerateUsed(0);
        vo.setGenerateLimit(0);
        vo.setRemaining(0);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void consumeQuota(String userId) {
        RegCodeUser assignment = getAssignment(userId);
        if (assignment == null) {
            return;
        }
        int used = assignment.getGenerateUsed() == null ? 0 : assignment.getGenerateUsed();
        assignment.setGenerateUsed(used + 1);
        assignment.setUpdateTime(springboot.utils.DateUtils.getNow());
        regCodeUserService.updateById(assignment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceConfigs(String userId, List<String> configIds) {
        LambdaQueryWrapper<RegCodeUserConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegCodeUserConfig::getUserId, userId);
        regCodeUserConfigService.remove(wrapper);
        if (configIds == null || configIds.isEmpty()) {
            return;
        }
        List<RegCodeUserConfig> rows = configIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .map(configId -> {
                    RegCodeUserConfig row = new RegCodeUserConfig();
                    row.setUserId(userId);
                    row.setConfigId(configId);
                    return row;
                })
                .collect(Collectors.toList());
        if (!rows.isEmpty()) {
            regCodeUserConfigService.saveBatch(rows);
        }
    }
}
