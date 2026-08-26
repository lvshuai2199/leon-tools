package springboot.controller.web;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.domain.SysMenus;
import springboot.service.SysMenusService;
import springboot.utils.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;

/**
 * (SysMenus)表控制层
 *
 * @author makejava
 * @since 2024-12-06 10:30:50
 */
@RestController
@RequestMapping("sysMenus")
public class SysMenusController {
    /**
     * 服务对象
     */
    @Autowired
    private SysMenusService sysMenusService;

    /**
     * 分页查询所有数据
     *
     * @param page 分页对象
     * @param sysMenus 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<SysMenus> page, SysMenus sysMenus) {
        return ApiResponse.success(this.sysMenusService.page(page, new QueryWrapper<>(sysMenus)));
    }

    /**
     * 查询全部菜单（按排序字段升序），供前端路由配置模块使用
     *
     * @return 全量菜单列表
     */
    @GetMapping("list")
    public ApiResponse list() {
        LambdaQueryWrapper<SysMenus> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(SysMenus::getSortOrder);
        return ApiResponse.success(this.sysMenusService.list(queryWrapper));
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.sysMenusService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param sysMenus 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody SysMenus sysMenus) {
        return ApiResponse.success(this.sysMenusService.save(sysMenus));
    }

    /**
     * 修改数据
     *
     * @param sysMenus 实体对象
     * @return 修改结果
     */
    @PostMapping ("update")
    public ApiResponse update(@RequestBody SysMenus sysMenus) {
        return ApiResponse.success(this.sysMenusService.updateById(sysMenus));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @PostMapping("del")
    public ApiResponse delete(@RequestBody List<String> idList) {
        return ApiResponse.success(this.sysMenusService.removeByIds(idList));
    }

}

