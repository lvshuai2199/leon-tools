package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.DTO.UsersDelDto;
import springboot.DTO.UserDto;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import jakarta.servlet.http.HttpServletRequest;
import springboot.service.RegCodeAccessService;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.ForbiddenException;
import springboot.utils.RoleUtils;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * (User)表控制层
 *
 * @author makejava
 * @since 2023-05-14 10:40:35
 */
@RestController
@RequestMapping("/admin/sysUsers")

public class SysUserController {
    /**
     * 服务对象
     */
    @Autowired
    private SysUsersService sysUsersService;

    @Autowired
    private SysRolesService sysRolesService;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    @Autowired
    private RegCodeAccessService regCodeAccessService;

    /**
     * 分页查询所有数据
     *
     * @param page 分页对象
     * @param sysUsers 查询实体
     * @return 所有数据
     */
//    @GetMapping("getUsers")
//    public Page<SysUsers> selectAll(Page<SysUsers> page, SysUsers user) {
//        return this.sysUsersService.page(page, new QueryWrapper<>(user));
//    }
    @GetMapping("getUsers")
    public ApiResponse selectAll(Page<SysUsers> page, SysUsers sysUsers) {
        LambdaQueryWrapper<SysUsers> queryWrapper = new LambdaQueryWrapper<>();
        if (sysUsers.getId() != null) {
            queryWrapper.eq(SysUsers::getId, sysUsers.getId());
        }
        if (sysUsers.getUsername() != null && !sysUsers.getUsername().isBlank()) {
            queryWrapper.like(SysUsers::getUsername, sysUsers.getUsername());
        }
        boolean listingChildren = sysUsers.getParentId() != null && !sysUsers.getParentId().isBlank();
        if (listingChildren) {
            queryWrapper.eq(SysUsers::getParentId, sysUsers.getParentId().trim());
        } else {
            queryWrapper.and(w -> w.isNull(SysUsers::getParentId).or().eq(SysUsers::getParentId, ""));
            queryWrapper.and(w -> w.isNull(SysUsers::getRoleId).or().ne(SysUsers::getRoleId, "role_regcode_client"));
        }

        Page<SysUsers> result = this.sysUsersService.page(page, queryWrapper);
        fillChildCounts(result.getRecords());
        return ApiResponse.success(result);
    }

    @PostMapping("userSaveOrUpdate")
    @Validated
    public ApiResponse sysUserRegister(@RequestBody UserDto userDto, HttpServletRequest request) {
        checkSavePrivilege(userDto, request);
        // 创建或更新用户
        SysUsers sysUsers = new SysUsers();
        sysUsers.setUsername(userDto.getUsername());
        sysUsers.setEmail(userDto.getEmail());
        sysUsers.setNickname(userDto.getNickname());
        sysUsers.setRoleId(userDto.getRoleId());
        if (userDto.getParentId() != null && !userDto.getParentId().isBlank()) {
            SysUsers parent = this.sysUsersService.getById(userDto.getParentId().trim());
            if (parent == null) {
                return ApiResponse.failure("父用户不存在");
            }
            if (parent.getParentId() != null && !parent.getParentId().isBlank()) {
                return ApiResponse.failure("只能挂在主用户下");
            }
            sysUsers.setParentId(parent.getId());
            if (sysUsers.getRoleId() == null || sysUsers.getRoleId().isBlank()) {
                sysUsers.setRoleId("role_regcode_client");
            }
        }

        // 如果存在 ID，则更新用户
        if (userDto.getId() != null) {
            sysUsers.setId(userDto.getId());
            // 编辑时若填写了新密码则一并更新，留空表示不修改密码
            if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
                sysUsers.setPassword(userDto.getPassword());
            }
            boolean updated = this.sysUsersService.updateById(sysUsers);
            return ApiResponse.success(updated ? "User updated successfully." : "User update failed.");
        }

        // 新用户注册逻辑
        if (userDto.getPassword() == null || userDto.getPassword().isEmpty()) {
            return ApiResponse.failure("Password cannot be empty.");
        }

//        // 加密密码（需要使用合适的加密库来加密密码）
//        String encryptedPassword = encryptPassword(userDto.getPassword());
//        sysUsers.setPassword(encryptedPassword);

        sysUsers.setPassword(userDto.getPassword());
        if (sysUsers.getUsername() != null) {
            LambdaQueryWrapper<SysUsers> existName = new LambdaQueryWrapper<>();
            existName.eq(SysUsers::getUsername, sysUsers.getUsername().trim());
            if (this.sysUsersService.count(existName) > 0) {
                return ApiResponse.failure("用户名已存在");
            }
        }

        boolean saved = this.sysUsersService.save(sysUsers);
        return ApiResponse.success(saved ? "User registered successfully." : "User registration failed.");
    }

    @PostMapping("delUsers")
    public ApiResponse delUsers(@RequestBody UsersDelDto request, HttpServletRequest httpRequest) {
        List<String> userIds = request.getUserIds();

        if (userIds == null || userIds.isEmpty()) {
            return ApiResponse.failure("User ID list cannot be empty");
        }
        checkDeletePrivilege(userIds, httpRequest);

        LambdaQueryWrapper<SysUsers> children = new LambdaQueryWrapper<>();
        children.in(SysUsers::getParentId, userIds);
        List<String> childIds = this.sysUsersService.list(children).stream()
                .map(SysUsers::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        java.util.LinkedHashSet<String> allIds = new java.util.LinkedHashSet<>(userIds);
        allIds.addAll(childIds);
        boolean result = sysUsersService.removeByIds(allIds);

        if (result) {
            return ApiResponse.success("Users deleted successfully");
        } else {
            return ApiResponse.failure("Failed to delete users");
        }
    }

    /**
     * 非 ROOT 操作者新增 / 编辑用户的限制（不满足抛 403）：
     * 不能编辑 ROOT 用户；不能授予 ROOT 角色；不能改自己的角色；只能授予菜单是自己菜单子集的角色。
     */
    private void checkSavePrivilege(UserDto dto, HttpServletRequest request) {
        SysUsers operator = this.regCodeAccessService.currentUser(request);
        if (operator == null) {
            throw new ForbiddenException();
        }
        if (this.regCodeAccessService.isRootUser(operator)) {
            return;
        }
        SysUsers target = null;
        if (dto.getId() != null && !dto.getId().isBlank()) {
            target = this.sysUsersService.getById(dto.getId());
            if (target != null && this.regCodeAccessService.isRootUser(target)) {
                throw new ForbiddenException("不能修改 ROOT 用户");
            }
        }
        String roleId = dto.getRoleId() == null ? null : dto.getRoleId().trim();
        if (roleId == null || roleId.isEmpty()) {
            if (dto.getParentId() != null && !dto.getParentId().isBlank()
                    && (target == null || target.getRoleId() == null || target.getRoleId().isBlank())) {
                // 挂父用户且没选角色时，保存逻辑会默认授予注册码客户角色，同样要过子集校验
                roleId = RegCodeAccessService.ROLE_REGCODE_CLIENT_ID;
            } else {
                return;
            }
        }
        boolean roleChanged = target == null || !roleId.equals(target.getRoleId() == null ? null : target.getRoleId().trim());
        if (!roleChanged) {
            return;
        }
        if (target != null && Objects.equals(target.getId(), operator.getId())) {
            throw new ForbiddenException("不能修改自己的角色");
        }
        SysRoles role = this.sysRolesService.getById(roleId);
        if (RoleUtils.isRoot(roleId, null) || RoleUtils.isRoot(role)) {
            throw new ForbiddenException("不能授予 ROOT 角色");
        }
        if (role == null) {
            throw new ForbiddenException("角色不存在");
        }
        List<String> roleMenus = this.sysRoleMenuService.getMenuIdsByRole(roleId);
        List<String> ownMenus = this.regCodeAccessService.menuIdsOf(operator);
        Set<String> own = new HashSet<>(ownMenus == null ? List.of() : ownMenus);
        if (roleMenus != null && !own.containsAll(roleMenus)) {
            throw new ForbiddenException("只能授予权限不超过自己的角色");
        }
    }

    /** 非 ROOT 操作者不能删除 ROOT 用户，也不能删除自己（不满足抛 403） */
    private void checkDeletePrivilege(List<String> userIds, HttpServletRequest request) {
        SysUsers operator = this.regCodeAccessService.currentUser(request);
        if (operator == null) {
            throw new ForbiddenException();
        }
        if (this.regCodeAccessService.isRootUser(operator)) {
            return;
        }
        if (userIds.contains(operator.getId())) {
            throw new ForbiddenException("不能删除自己");
        }
        List<SysUsers> targets = this.sysUsersService.listByIds(userIds);
        if (targets != null && targets.stream().anyMatch(this.regCodeAccessService::isRootUser)) {
            throw new ForbiddenException("不能删除 ROOT 用户");
        }
    }

    private void fillChildCounts(List<SysUsers> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> ids = records.stream()
                .map(SysUsers::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<SysUsers> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUsers::getParentId, ids);
        java.util.Map<String, Long> counts = this.sysUsersService.list(wrapper).stream()
                .filter(item -> item.getParentId() != null)
                .collect(java.util.stream.Collectors.groupingBy(SysUsers::getParentId, java.util.stream.Collectors.counting()));
        for (SysUsers user : records) {
            user.setChildCount(counts.getOrDefault(user.getId(), 0L).intValue());
        }
    }

}

