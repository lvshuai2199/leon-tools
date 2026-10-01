package springboot.controller.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.domain.BadmintonBill;
import springboot.domain.SysUsers;
import springboot.service.BadmintonBillStore;
import springboot.service.RegCodeAccessService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.RequestUserUtils;

import java.io.Serializable;
import java.util.List;

/**
 * 羽毛球计费：场地费 / 用球费 / 人数，计算个人应付。
 */
@RestController
public class BadmintonBillController {

    private final BadmintonBillStore store;
    private final SysUsersService sysUsersService;
    private final RegCodeAccessService regCodeAccessService;

    public BadmintonBillController(BadmintonBillStore store,
                                   SysUsersService sysUsersService,
                                   RegCodeAccessService regCodeAccessService) {
        this.store = store;
        this.sysUsersService = sysUsersService;
        this.regCodeAccessService = regCodeAccessService;
    }

    @GetMapping("badmintonBill/getAll")
    public ApiResponse selectAll(Page<BadmintonBill> page,
                                 @RequestParam(value = "playDate", required = false) String playDate,
                                 @RequestParam(value = "playDateStart", required = false) String playDateStart,
                                 @RequestParam(value = "playDateEnd", required = false) String playDateEnd,
                                 @RequestParam(value = "title", required = false) String title,
                                 HttpServletRequest request) {
        ApiResponse deny = denyUnlessAllowed(request);
        if (deny != null) {
            return deny;
        }
        return ApiResponse.success(store.page(page, playDate, playDateStart, playDateEnd, title));
    }

    @GetMapping("badmintonBill/{id}")
    public ApiResponse selectOne(@PathVariable Serializable id, HttpServletRequest request) {
        ApiResponse deny = denyUnlessAllowed(request);
        if (deny != null) {
            return deny;
        }
        BadmintonBill entity = store.getWithItems(id == null ? null : String.valueOf(id));
        if (entity == null) {
            return ApiResponse.failure("记录不存在");
        }
        return ApiResponse.success(entity);
    }

    @PostMapping("badmintonBill/save")
    public ApiResponse save(@RequestBody BadmintonBill body, HttpServletRequest request) {
        ApiResponse deny = denyUnlessAllowed(request);
        if (deny != null) {
            return deny;
        }
        try {
            Operator operator = resolveOperator(request);
            return ApiResponse.success(store.save(body, operator.id, operator.name));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("badmintonBill/preview")
    public ApiResponse preview(@RequestBody BadmintonBill body, HttpServletRequest request) {
        ApiResponse deny = denyUnlessAllowed(request);
        if (deny != null) {
            return deny;
        }
        return ApiResponse.success(store.preview(body));
    }

    @PostMapping("badmintonBill/del")
    public ApiResponse delete(@RequestBody List<String> idList, HttpServletRequest request) {
        ApiResponse deny = denyUnlessAllowed(request);
        if (deny != null) {
            return deny;
        }
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        return ApiResponse.success(store.deleteByIds(idList));
    }

    private ApiResponse denyUnlessAllowed(HttpServletRequest request) {
        String userId = RequestUserUtils.currentUserId(request);
        if (userId == null || userId.isBlank()) {
            return ApiResponse.failure("请先登录");
        }
        SysUsers user = sysUsersService.getById(userId);
        if (user == null) {
            return ApiResponse.failure("请先登录");
        }
        if (!regCodeAccessService.canUseCrab(user)) {
            return ApiResponse.failure("无羽毛球计费权限");
        }
        return null;
    }

    private Operator resolveOperator(HttpServletRequest request) {
        Operator operator = new Operator();
        String userId = RequestUserUtils.currentUserId(request);
        if (userId != null && !userId.isBlank()) {
            SysUsers user = sysUsersService.getById(userId);
            if (user != null) {
                operator.id = user.getId();
                operator.name = notBlank(user.getNickname()) ? user.getNickname() : user.getUsername();
                return operator;
            }
            operator.id = userId;
        }
        return operator;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static final class Operator {
        private String id;
        private String name;
    }
}
