package springboot.controller.web;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.domain.ComRegistration;
import springboot.service.ComRegistrationService;
import org.springframework.web.bind.annotation.*;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.OperatorUtils;

import java.io.Serializable;
import java.util.List;

/**
 * (ComRegistration)表控制层
 *
 * @author makejava
 * @since 2025-04-27 13:47:00
 */
@RestController
@RequestMapping("comRegistration")
public class ComRegistrationController {
    /**
     * 服务对象
     */
    @Autowired
    private ComRegistrationService comRegistrationService;

    /**
     * 分页查询所有数据
     *
     * @param page            分页对象
     * @param comRegistration 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<ComRegistration> page, ComRegistration query) {
        LambdaQueryWrapper<ComRegistration> queryWrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getOperator() != null && !query.getOperator().isBlank()) {
                queryWrapper.like(ComRegistration::getOperator, query.getOperator());
            }
            if (query.getCompany() != null && !query.getCompany().isBlank()) {
                queryWrapper.like(ComRegistration::getCompany, query.getCompany());
            }
            if (query.getApplyName() != null && !query.getApplyName().isBlank()) {
                queryWrapper.like(ComRegistration::getApplyName, query.getApplyName());
            }
        }
        queryWrapper.orderByDesc(ComRegistration::getCreateTime);
        return ApiResponse.success(this.comRegistrationService.page(page, queryWrapper));
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.comRegistrationService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param comRegistration 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody ComRegistration comRegistration) {
        comRegistration.setCreateTime(DateUtils.getNow());
        comRegistration.setOperator(OperatorUtils.resolve(
                comRegistration.getOperator() != null ? comRegistration.getOperator() : comRegistration.getApplyId()));
        if (comRegistration.getRemarks() == null || comRegistration.getRemarks().isBlank()) {
            comRegistration.setRemarks("注册码操作");
        }
        boolean save = this.comRegistrationService.save(comRegistration);
        if (save) {
            return ApiResponse.success("已写入注册码记录");
        }
        return ApiResponse.failure("写入注册码记录失败");
    }

    /**
     * 修改数据
     *
     * @param comRegistration 实体对象
     * @return 修改结果
     */
    @PostMapping ("update")
    public ApiResponse update(@RequestBody ComRegistration comRegistration) {
        return ApiResponse.success(this.comRegistrationService.updateById(comRegistration));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @PostMapping("del")
    public ApiResponse delete(@RequestParam("idList") List<Long> idList) {
        return ApiResponse.success(this.comRegistrationService.removeByIds(idList));
    }
}

