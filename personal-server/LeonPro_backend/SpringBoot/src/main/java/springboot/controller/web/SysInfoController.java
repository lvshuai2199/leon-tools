package springboot.controller.web;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.domain.SysInfo;
import springboot.domain.SysUsers;
import springboot.service.SysInfoService;
import org.springframework.web.bind.annotation.*;
import springboot.utils.ApiResponse;

import java.io.Serializable;
import java.util.List;

/**
 * (SysInfo)表控制层
 *
 * @author makejava
 * @since 2025-05-08 22:17:17
 */
@RestController
@RequestMapping("sysInfo")
public class SysInfoController {
    /**
     * 服务对象
     */
    @Autowired
    private SysInfoService sysInfoService;

    /**
     * 分页查询所有数据
     *
     * @param page    分页对象
     * @param sysInfo 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<SysInfo> page, SysInfo sysInfo) {
        // 创建 QueryWrapper 实例
        LambdaQueryWrapper<SysInfo> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(SysInfo::getUserId, sysInfo.getUserId());

        return ApiResponse.success(this.sysInfoService.page(page, new QueryWrapper<>(sysInfo)));
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.sysInfoService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param sysInfo 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody SysInfo sysInfo) {
        return ApiResponse.success(this.sysInfoService.save(sysInfo));
    }

    /**
     * 修改数据
     *
     * @param sysInfo 实体对象
     * @return 修改结果
     */
    @PostMapping ("update")
    public ApiResponse update(@RequestBody SysInfo sysInfo) {
        return ApiResponse.success(this.sysInfoService.updateById(sysInfo));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @DeleteMapping
    public ApiResponse delete(@RequestParam("idList") List<Long> idList) {
        return ApiResponse.success(this.sysInfoService.removeByIds(idList));
    }
}

