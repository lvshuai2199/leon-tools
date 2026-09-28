package springboot.controller.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
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
import springboot.utils.ForbiddenException;

import java.util.List;

/**
 * 管理端螃蟹出货（/admin/crabShipment/**）：看全部记录。
 * 权限由 AdminAuthInterceptor 按 menu_crab 校验。
 */
@RestController
@RequestMapping("/admin/crabShipment")
public class AdminCrabShipmentController {

    private final CrabShipmentBizService bizService;
    private final RegCodeAccessService regCodeAccessService;

    public AdminCrabShipmentController(CrabShipmentBizService bizService, RegCodeAccessService regCodeAccessService) {
        this.bizService = bizService;
        this.regCodeAccessService = regCodeAccessService;
    }

    @GetMapping("/getAll")
    public ApiResponse selectAll(Page<CrabShipment> page,
                                 CrabShipment query,
                                 @RequestParam(value = "shipDateStart", required = false) String shipDateStart,
                                 @RequestParam(value = "shipDateEnd", required = false) String shipDateEnd) {
        return ApiResponse.success(bizService.page(page, query, shipDateStart, shipDateEnd, null));
    }

    @PostMapping("/save")
    public ApiResponse save(@RequestBody CrabShipment body, HttpServletRequest request) {
        return bizService.save(body, currentUser(request), null);
    }

    @PostMapping("/status")
    public ApiResponse updateStatus(@RequestBody CrabShipment body) {
        return bizService.updateStatus(body, null);
    }

    @PostMapping("/batchSave")
    public ApiResponse batchSave(@RequestBody CrabShipmentBatchRequest req, HttpServletRequest request) {
        return bizService.batchSave(req, currentUser(request));
    }

    @PostMapping("/parse")
    public ApiResponse parse(@RequestBody CrabShipmentParseRequest req) {
        return bizService.parse(req == null ? null : req.getText());
    }

    @PostMapping("/del")
    public ApiResponse delete(@RequestBody List<String> idList) {
        return bizService.delete(idList, null);
    }

    private SysUsers currentUser(HttpServletRequest request) {
        SysUsers user = regCodeAccessService.currentUser(request);
        if (user == null) {
            throw new ForbiddenException();
        }
        return user;
    }
}
