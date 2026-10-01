package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import springboot.domain.RegCodeConfig;
import springboot.service.RegCodeAccessService;
import springboot.service.RegCodeConfigService;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.RequestUserUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 注册码生成配置 CRUD。
 */
@RestController
@RequestMapping("/admin/regCodeConfig")
public class RegCodeConfigController {

    @Autowired
    private RegCodeConfigService regCodeConfigService;

    @Autowired
    private RegCodeAccessService regCodeAccessService;

    @GetMapping("getAll")
    public ApiResponse selectAll(Page<RegCodeConfig> page, RegCodeConfig query, HttpServletRequest request) {
        String err = this.regCodeAccessService.requireManager(request);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        return ApiResponse.success(this.regCodeConfigService.page(page, buildQuery(query)));
    }

    /**
     * 管理端版配置列表（注册码客户页给客户分配配置用）：返回全部配置、完整字段（含加密后缀）。
     * 生成页请用 /common/regCodeConfig/list（只返回分配给自己的、且不含加密字段）。
     */
    @GetMapping("list")
    public ApiResponse listAll(RegCodeConfig query, HttpServletRequest request) {
        String err = this.regCodeAccessService.requireManager(request);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        return ApiResponse.success(this.regCodeConfigService.list(buildQuery(query)));
    }

    @PostMapping("add")
    public ApiResponse insert(@RequestBody RegCodeConfig entity, HttpServletRequest request) {
        String deny = this.regCodeAccessService.requireManager(request);
        if (deny != null) {
            return ApiResponse.failure(deny);
        }
        String err = validate(entity, true);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        Date now = DateUtils.getNow();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getEncryptType() == null || entity.getEncryptType().isBlank()) {
            entity.setEncryptType("MD5");
        }
        return ApiResponse.success(this.regCodeConfigService.save(entity));
    }

    @PostMapping("update")
    public ApiResponse update(@RequestBody RegCodeConfig entity, HttpServletRequest request) {
        String deny = this.regCodeAccessService.requireManager(request);
        if (deny != null) {
            return ApiResponse.failure(deny);
        }
        if (entity.getId() == null || entity.getId().isBlank()) {
            return ApiResponse.failure("缺少主键");
        }
        String err = validate(entity, false);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        entity.setUpdateTime(DateUtils.getNow());
        return ApiResponse.success(this.regCodeConfigService.updateById(entity));
    }

    @PostMapping("del")
    public ApiResponse delete(@RequestBody List<String> idList, HttpServletRequest request) {
        String deny = this.regCodeAccessService.requireManager(request);
        if (deny != null) {
            return ApiResponse.failure(deny);
        }
        return ApiResponse.success(this.regCodeConfigService.removeByIds(idList));
    }

    private LambdaQueryWrapper<RegCodeConfig> buildQuery(RegCodeConfig query) {
        LambdaQueryWrapper<RegCodeConfig> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getCompany() != null && !query.getCompany().isBlank()) {
                wrapper.like(RegCodeConfig::getCompany, query.getCompany());
            }
            if (query.getName() != null && !query.getName().isBlank()) {
                wrapper.like(RegCodeConfig::getName, query.getName());
            }
            if (query.getComponentName() != null && !query.getComponentName().isBlank()) {
                wrapper.like(RegCodeConfig::getComponentName, query.getComponentName());
            }
        }
        wrapper.orderByAsc(RegCodeConfig::getSortOrder)
                .orderByAsc(RegCodeConfig::getCompany)
                .orderByAsc(RegCodeConfig::getName);
        return wrapper;
    }

    private String validate(RegCodeConfig entity, boolean creating) {
        if (entity.getCompany() == null || entity.getCompany().isBlank()) {
            return "请填写公司";
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            return "请填写名称";
        }
        if (entity.getComponentName() == null || entity.getComponentName().isBlank()) {
            return "请填写组件名称";
        }
        if (entity.getEncryptSuffix() == null || entity.getEncryptSuffix().isBlank()) {
            return "请填写加密字符后缀";
        }
        if (isDuplicate(entity, creating)) {
            return "同一公司下名称或组件名称已存在";
        }
        return null;
    }

    private boolean isDuplicate(RegCodeConfig entity, boolean creating) {
        LambdaQueryWrapper<RegCodeConfig> byName = new LambdaQueryWrapper<>();
        byName.eq(RegCodeConfig::getCompany, entity.getCompany().trim())
                .eq(RegCodeConfig::getName, entity.getName().trim());
        if (!creating && entity.getId() != null) {
            byName.ne(RegCodeConfig::getId, entity.getId());
        }
        if (this.regCodeConfigService.count(byName) > 0) {
            return true;
        }
        LambdaQueryWrapper<RegCodeConfig> byComponent = new LambdaQueryWrapper<>();
        byComponent.eq(RegCodeConfig::getCompany, entity.getCompany().trim())
                .eq(RegCodeConfig::getComponentName, entity.getComponentName().trim());
        if (!creating && entity.getId() != null) {
            byComponent.ne(RegCodeConfig::getId, entity.getId());
        }
        return this.regCodeConfigService.count(byComponent) > 0;
    }
}
