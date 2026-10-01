package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.domain.SysRoles;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.utils.ApiResponse;
import springboot.utils.RoleUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import java.io.Serializable;
import java.util.List;

/**
 * (SysRoles)表控制层
 *
 * @author makejava
 * @since 2024-12-06 11:26:18
 */
@RestController
@RequestMapping("/admin/sysRoles")
public class SysRolesController {
    /**
     * 服务对象
     */
    @Autowired
    private SysRolesService sysRolesService;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    /**
     * 分页查询所有数据
     *
     * @param page     分页对象
     * @param sysRoles 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<SysRoles> page, SysRoles sysRoles) {
        LambdaQueryWrapper<SysRoles> queryWrapper = new LambdaQueryWrapper<>();
        if (sysRoles.getRoleName() != null && !sysRoles.getRoleName().isEmpty()) {
            queryWrapper.like(SysRoles::getRoleName, sysRoles.getRoleName());
        }
        if (sysRoles.getIsDisabled() != null) {
            queryWrapper.eq(SysRoles::getIsDisabled, sysRoles.getIsDisabled());
        }
        queryWrapper.orderByAsc(SysRoles::getCreateTime);
        return ApiResponse.success(this.sysRolesService.page(page, queryWrapper));
    }

    /**
     * 新增数据
     *
     * @param sysRoles 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody SysRoles sysRoles) {
        if (RoleUtils.isRoot(sysRoles)) {
            return ApiResponse.failure("ROOT 为系统默认角色，不允许新建同名角色");
        }
        if (sysRoles.getCreateTime() == null) {
            sysRoles.setCreateTime(new java.util.Date());
        }
        return ApiResponse.success(this.sysRolesService.save(sysRoles));
    }

    @PostMapping ("update")
    public ApiResponse update(@RequestBody SysRoles sysRoles) {
        if (sysRoles.getId() != null && RoleUtils.isRoot(this.sysRolesService.getById(sysRoles.getId()))) {
            return ApiResponse.failure("系统默认角色 ROOT 不允许修改");
        }
        if (RoleUtils.isRoot(sysRoles)) {
            return ApiResponse.failure("不允许将角色改为 ROOT");
        }
        return ApiResponse.success(this.sysRolesService.updateById(sysRoles));
    }

    @PostMapping("del")
    public ApiResponse delete(@RequestBody List<String> idList) {
        if (idList != null) {
            for (String id : idList) {
                if (RoleUtils.isRoot(this.sysRolesService.getById(id))) {
                    return ApiResponse.failure("系统默认角色 ROOT 不允许删除");
                }
            }
        }
        return ApiResponse.success(this.sysRolesService.removeByIds(idList));
    }

    /**
     * 查询角色已分配的菜单（路由）ID 列表
     */
    @GetMapping("menus")
    public ApiResponse getRoleMenus(@RequestParam String roleId) {
        return ApiResponse.success(sysRoleMenuService.getMenuIdsByRole(roleId));
    }

    /**
     * 分配角色可访问的菜单（路由）列表。body: {roleId, menuIds, client}；
     * client 必填（admin / app），只替换该端的授权，另一端的授权原样保留；不传 client 直接返回业务错误、什么都不改
     * （避免旧页面一次保存把另一端的授权清掉）。已停用菜单的原有授权始终保留；不存在的菜单 id 忽略。
     */
    @PostMapping("menus")
    public ApiResponse assignRoleMenus(@RequestBody Map<String, Object> body) {
        String roleId = body == null || body.get("roleId") == null ? null : String.valueOf(body.get("roleId")).trim();
        if (roleId == null || roleId.isEmpty()) {
            return ApiResponse.failure("缺少角色 id");
        }
        Object clientValue = body.get("client");
        String client = clientValue == null || String.valueOf(clientValue).isBlank() ? null : String.valueOf(clientValue).trim();
        if (client == null) {
            return ApiResponse.failure("保存角色菜单必须指定端：client 传 admin（管理端）或 app（用户端）");
        }
        if (!springboot.service.menu.MenuClients.isValid(client)) {
            return ApiResponse.failure("client 只能是 admin 或 app");
        }
        if (RoleUtils.isRoot(this.sysRolesService.getById(roleId))) {
            return ApiResponse.failure("系统默认角色 ROOT 拥有全部权限，无需配置");
        }
        List<String> menuIds = new java.util.ArrayList<>();
        Object ids = body.get("menuIds");
        if (ids instanceof java.util.List) {
            for (Object o : (java.util.List<?>) ids) {
                menuIds.add(String.valueOf(o));
            }
        }
        sysRoleMenuService.assignMenus(roleId, menuIds, client);
        return ApiResponse.success("OK");
    }

}

