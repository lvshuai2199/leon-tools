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
import springboot.domain.BadmintonBill;
import springboot.domain.SysUsers;
import springboot.service.BadmintonBillBizService;
import springboot.service.RegCodeAccessService;
import springboot.utils.ApiResponse;

import java.util.List;
import java.util.Set;

/**
 * 用户端羽毛球计费（/app/badmintonBill/**）。
 * 能用的人：{@link RegCodeAccessService#requireBadminton}；数据范围按 operator_id。
 */
@RestController
@RequestMapping("/app/badmintonBill")
public class AppBadmintonBillController {

    private final BadmintonBillBizService bizService;
    private final RegCodeAccessService regCodeAccessService;

    public AppBadmintonBillController(BadmintonBillBizService bizService,
                                      RegCodeAccessService regCodeAccessService) {
        this.bizService = bizService;
        this.regCodeAccessService = regCodeAccessService;
    }

    @GetMapping("/getAll")
    public ApiResponse selectAll(Page<BadmintonBill> page,
                                 @RequestParam(value = "playDate", required = false) String playDate,
                                 @RequestParam(value = "playDateStart", required = false) String playDateStart,
                                 @RequestParam(value = "playDateEnd", required = false) String playDateEnd,
                                 @RequestParam(value = "title", required = false) String title,
                                 HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireBadminton(request);
        return ApiResponse.success(bizService.page(page, playDate, playDateStart, playDateEnd, title, bizService.scopeOf(user)));
    }

    @GetMapping("/{id}")
    public ApiResponse selectOne(@PathVariable String id, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireBadminton(request);
        return bizService.detail(id, bizService.scopeOf(user));
    }

    @PostMapping("/save")
    public ApiResponse save(@RequestBody BadmintonBill body, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireBadminton(request);
        return bizService.save(body, user, bizService.scopeOf(user));
    }

    @PostMapping("/preview")
    public ApiResponse preview(@RequestBody BadmintonBill body, HttpServletRequest request) {
        regCodeAccessService.requireBadminton(request);
        return bizService.preview(body);
    }

    /** 名称预览：body 取 id / title / playDate，返回 {name, playDate}，与保存时的名称规则一致 */
    @PostMapping("/namePreview")
    public ApiResponse namePreview(@RequestBody BadmintonBill body, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireBadminton(request);
        return bizService.namePreview(body, user, bizService.scopeOf(user));
    }

    @PostMapping("/del")
    public ApiResponse delete(@RequestBody List<String> idList, HttpServletRequest request) {
        SysUsers user = regCodeAccessService.requireBadminton(request);
        Set<String> scope = bizService.scopeOf(user);
        return bizService.delete(idList, scope);
    }
}
