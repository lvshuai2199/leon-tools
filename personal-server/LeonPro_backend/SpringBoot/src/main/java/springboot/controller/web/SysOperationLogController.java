package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.domain.SysOperationLog;
import springboot.service.SysOperationLogService;
import springboot.utils.ApiResponse;

import java.io.Serializable;
import java.util.Date;

@RestController
@RequestMapping("sysOperationLog")
public class SysOperationLogController {

    @Autowired
    private SysOperationLogService sysOperationLogService;

    @GetMapping("getAll")
    public ApiResponse selectAll(
            Page<SysOperationLog> page,
            SysOperationLog query,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date beginTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (notBlank(query.getOperatorName())) {
                wrapper.like(SysOperationLog::getOperatorName, query.getOperatorName().trim());
            }
            if (notBlank(query.getModule())) {
                wrapper.eq(SysOperationLog::getModule, query.getModule().trim());
            }
            if (notBlank(query.getStatus())) {
                wrapper.eq(SysOperationLog::getStatus, query.getStatus().trim());
            }
            if (notBlank(query.getRequestUri())) {
                wrapper.like(SysOperationLog::getRequestUri, query.getRequestUri().trim());
            }
            if (notBlank(query.getAction())) {
                wrapper.like(SysOperationLog::getAction, query.getAction().trim());
            }
        }
        if (beginTime != null) {
            wrapper.ge(SysOperationLog::getCreateTime, beginTime);
        }
        if (endTime != null) {
            wrapper.le(SysOperationLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(SysOperationLog::getCreateTime);
        return ApiResponse.success(this.sysOperationLogService.page(page, wrapper));
    }

    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.sysOperationLogService.getById(id));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
