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
import springboot.utils.ForbiddenException;

import java.util.List;

/**
 * 管理端羽毛球计费（/admin/badmintonBill/**）：看全部记录。
 * 权限由 AdminAuthInterceptor 按 menu_badminton 校验。
 */
@RestController
@RequestMapping("/admin/badmintonBill")
public class AdminBadmintonBillController {

    private final BadmintonBillBizService bizService;
    private final RegCodeAccessService regCodeAccessService;

    public AdminBadmintonBillController(BadmintonBillBizService bizService,
                                        RegCodeAccessService regCodeAccessService) {
        this.bizService = bizService;
        this.regCodeAccessService = regCodeAccessService;
    }

    @GetMapping("/getAll")
    public ApiResponse selectAll(Page<BadmintonBill> page,
                                 @RequestParam(value = "playDate", required = false) String playDate,
                                 @RequestParam(value = "playDateStart", required = false) String playDateStart,
                                 @RequestParam(value = "playDateEnd", required = false) String playDateEnd,
                                 @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.success(bizService.page(page, playDate, playDateStart, playDateEnd, title, null));
    }

    @GetMapping("/{id}")
    public ApiResponse selectOne(@PathVariable String id) {
        return bizService.detail(id, null);
    }

    @PostMapping("/save")
    public ApiResponse save(@RequestBody BadmintonBill body, HttpServletRequest request) {
        return bizService.save(body, currentUser(request), null);
    }

    @PostMapping("/preview")
    public ApiResponse preview(@RequestBody BadmintonBill body) {
        return bizService.preview(body);
    }

    /** 名称预览：与保存时的名称规则一致（重名范围按这条记录的 operator_id，新建按当前用户） */
    @PostMapping("/namePreview")
    public ApiResponse namePreview(@RequestBody BadmintonBill body, HttpServletRequest request) {
        return bizService.namePreview(body, currentUser(request), null);
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
