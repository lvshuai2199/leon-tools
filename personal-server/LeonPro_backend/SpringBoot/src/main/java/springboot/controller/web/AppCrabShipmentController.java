package springboot.controller.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.DTO.CrabShipmentBatchRequest;
import springboot.DTO.CrabShipmentParseRequest;
import springboot.domain.CrabShipment;
import springboot.domain.SysUsers;
import springboot.service.CrabShipmentBizService;
import springboot.service.RegCodeAccessService;
import springboot.utils.ApiResponse;

import java.util.List;
import java.util.Set;

/**
 * 用户端螃蟹出货（/app/crabShipment/**）。
 * 能用的人：非注册码客户（requireCrab，否则 403）。
 * 数据范围按 operator_id：ROOT 全部；子账号只有自己的；主账号是自己 + 自己名下子账号的。
 * 范围外记录的详情 / 改状态 / 修改 / 删除都是 403；新建记录记在当前用户名下。
 */
@RestController
@RequestMapping("/app/crabShipment")
public class AppCrabShipmentController {

    private final CrabShipmentBizService bizService;
    private final RegCodeAccessService regCodeAccessService;

    public AppCrabShipmentController(CrabShipmentBizService bizService, RegCodeAccessService regCodeAccessService) {
        this.bizService = bizService;
        this.regCodeAccessService = regCodeAccessService;
    }

    @GetMapping("/getAll")
    public ApiResponse selectAll(Page<CrabShipment> page,
                                 CrabShipment query,
                                 @RequestParam(value = "shipDateStart", required = false) String shipDateStart,
                                 @RequestParam(value = "shipDateEnd", required = false) String shipDateEnd,
                                 HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireCrab(request);
        return ApiResponse.success(bizService.page(page, query, shipDateStart, shipDateEnd, bizService.scopeOf(user)));
    }

    @GetMapping("/{id}")
    public ApiResponse selectOne(@PathVariable String id, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireCrab(request);
        return bizService.detail(id, bizService.scopeOf(user));
    }

    @PostMapping("/save")
    public ApiResponse save(@RequestBody CrabShipment body, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireCrab(request);
        return bizService.save(body, user, bizService.scopeOf(user));
    }

    @PostMapping("/status")
    public ApiResponse updateStatus(@RequestBody CrabShipment body, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireCrab(request);
        return bizService.updateStatus(body, bizService.scopeOf(user));
    }

    @PostMapping("/batchSave")
    public ApiResponse batchSave(@RequestBody CrabShipmentBatchRequest req, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireCrab(request);
        return bizService.batchSave(req, user);
    }

    @PostMapping("/parse")
    public ApiResponse parse(@RequestBody CrabShipmentParseRequest req, HttpServletRequest request) {
        regCodeAccessService.requireCrab(request);
        return bizService.parse(req == null ? null : req.getText());
    }

    @PostMapping("/del")
    public ApiResponse delete(@RequestBody List<String> idList, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireCrab(request);
        Set<String> scope = bizService.scopeOf(user);
        return bizService.delete(idList, scope);
    }
}
