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
@RequestMapping("/admin/comRegistration")
public class ComRegistrationController {
    /**
     * 服务对象
     */
    @Autowired
    private ComRegistrationService comRegistrationService;

    @Autowired
    private OperatorUtils operatorUtils;

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
                String keyword = query.getOperator().trim();
                List<String> userIds = this.operatorUtils.findUserIdsByName(keyword);
                queryWrapper.and(w -> {
                    w.like(ComRegistration::getOperator, keyword);
                    if (!userIds.isEmpty()) {
                        w.or().in(ComRegistration::getOperator, userIds);
                    }
                });
            }
            if (query.getCompany() != null && !query.getCompany().isBlank()) {
                queryWrapper.like(ComRegistration::getCompany, query.getCompany());
            }
            if (query.getApplyName() != null && !query.getApplyName().isBlank()) {
                queryWrapper.like(ComRegistration::getApplyName, query.getApplyName());
            }
        }
        queryWrapper.orderByDesc(ComRegistration::getCreateTime);
        var result = this.comRegistrationService.page(page, queryWrapper);
        result.getRecords().forEach(row -> row.setOperator(this.operatorUtils.toDisplayName(row.getOperator())));
        return ApiResponse.success(result);
    }

}

