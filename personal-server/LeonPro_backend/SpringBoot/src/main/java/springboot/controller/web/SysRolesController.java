package springboot.controller.web;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.domain.SysRoles;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.utils.ApiResponse;
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
@RequestMapping("sysRoles")
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
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.sysRolesService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param sysRoles 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody SysRoles sysRoles) {
        return ApiResponse.success(this.sysRolesService.save(sysRoles));
    }

    /**
     * 修改数据
     *
     * @param sysRoles 实体对象
     * @return 修改结果
     */
    @PostMapping ("update")
    public ApiResponse update(@RequestBody SysRoles sysRoles) {
        return ApiResponse.success(this.sysRolesService.updateById(sysRoles));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @PostMapping("del")
    public ApiResponse delete(@RequestBody List<String> idList) {
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
     * 分配角色可访问的菜单（路由）列表（先删后插）
     */
    @PostMapping("menus")
    public ApiResponse assignRoleMenus(@RequestBody Map<String, Object> body) {
        String roleId = (String) body.get("roleId");
        List<String> menuIds = new java.util.ArrayList<>();
        Object ids = body.get("menuIds");
        if (ids instanceof java.util.List) {
            for (Object o : (java.util.List<?>) ids) {
                menuIds.add(String.valueOf(o));
            }
        }
        sysRoleMenuService.assignMenus(roleId, menuIds);
        return ApiResponse.success("OK");
    }

}

