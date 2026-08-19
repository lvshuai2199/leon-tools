package springboot.controller.web;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.domain.SysRoles;
import springboot.service.SysRolesService;
import springboot.utils.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 分页查询所有数据
     *
     * @param page     分页对象
     * @param sysRoles 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<SysRoles> page, SysRoles sysRoles) {
        return ApiResponse.success(this.sysRolesService.page(page, new QueryWrapper<>(sysRoles)));
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
    public ApiResponse delete(@RequestBody List<Long> idList) {
        return ApiResponse.success(this.sysRolesService.removeByIds(idList));
    }

}

