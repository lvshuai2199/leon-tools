package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import springboot.DTO.RegCodeSubUser;
import springboot.DTO.RegCodeUserForm;
import springboot.DTO.RegCodeUserVO;
import springboot.domain.RegCodeConfig;
import springboot.domain.RegCodeUser;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.AuthTokenService;
import springboot.service.RegCodeAccessService;
import springboot.service.RegCodeConfigService;
import springboot.service.RegCodeQuotaService;
import springboot.service.RegCodeUserService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.ForbiddenException;
import springboot.utils.RequestUserUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/regCodeUser")
public class RegCodeUserController {

    /**
     * 底层子用户（父用户是子账号或注册码客户，即客户在注册码页创建的）的 user_id。
     * <p>c.id 显式 COLLATE：线上 reg_code_user 是 utf8mb4_0900_ai_ci、sys_users 是 utf8mb4_unicode_ci，
     * 不加会在 {@code user_id NOT IN (...)} 处报 1267 Illegal mix of collations；显式排序规则优先级最高，
     * 本地两表同为 0900_ai_ci 时也照常可用（只改代码不动表结构）。
     */
    private static final String BOTTOM_SUB_USER_IDS_SQL =
            "SELECT c.id COLLATE utf8mb4_unicode_ci FROM sys_users c JOIN sys_users p ON p.id = c.parent_id "
                    + "WHERE (p.parent_id IS NOT NULL AND p.parent_id <> '') OR p.role_id = '"
                    + RegCodeAccessService.ROLE_REGCODE_CLIENT_ID + "'";

    private final RegCodeUserService regCodeUserService;
    private final RegCodeAccessService regCodeAccessService;
    private final RegCodeConfigService regCodeConfigService;
    private final SysUsersService sysUsersService;
    private final SysRolesService sysRolesService;
    private final RegCodeQuotaService regCodeQuotaService;
    private final AuthTokenService authTokenService;

    public RegCodeUserController(RegCodeUserService regCodeUserService,
                                 RegCodeAccessService regCodeAccessService,
                                 RegCodeConfigService regCodeConfigService,
                                 SysUsersService sysUsersService,
                                 SysRolesService sysRolesService,
                                 RegCodeQuotaService regCodeQuotaService,
                                 AuthTokenService authTokenService) {
        this.regCodeQuotaService = regCodeQuotaService;
        this.authTokenService = authTokenService;
        this.regCodeUserService = regCodeUserService;
        this.regCodeAccessService = regCodeAccessService;
        this.regCodeConfigService = regCodeConfigService;
        this.sysUsersService = sysUsersService;
        this.sysRolesService = sysRolesService;
    }

    @GetMapping("getAll")
    public ApiResponse selectAll(Page<RegCodeUser> page, String username, String parentId, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(request);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        SysUsers operator = regCodeAccessService.currentUser(request);
        if (operator == null || operator.getId() == null || operator.getId().isBlank()) {
            return ApiResponse.failure("请先登录");
        }
        boolean root = regCodeAccessService.isRootUser(operator);
        String scopedParentId = root ? parentId : operator.getId();

        LambdaQueryWrapper<RegCodeUser> wrapper = new LambdaQueryWrapper<>();
        if ((username != null && !username.isBlank()) || (scopedParentId != null && !scopedParentId.isBlank())) {
            LambdaQueryWrapper<SysUsers> userWrapper = new LambdaQueryWrapper<>();
            if (username != null && !username.isBlank()) {
                userWrapper.like(SysUsers::getUsername, username.trim());
            }
            if (scopedParentId != null && !scopedParentId.isBlank()) {
                userWrapper.eq(SysUsers::getParentId, scopedParentId.trim());
            }
            List<String> userIds = sysUsersService.list(userWrapper).stream()
                    .map(SysUsers::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (userIds.isEmpty()) {
                Page<RegCodeUserVO> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
                empty.setRecords(Collections.emptyList());
                return ApiResponse.success(empty);
            }
            wrapper.in(RegCodeUser::getUserId, userIds);
        }
        // 列表只显示客户（顶层）；客户在注册码页创建的子用户走 /admin/regCodeUser/{customerId}/subUsers
        wrapper.notInSql(RegCodeUser::getUserId, BOTTOM_SUB_USER_IDS_SQL);
        wrapper.orderByDesc(RegCodeUser::getCreateTime);
        Page<RegCodeUser> result = this.regCodeUserService.page(page, wrapper);

        Page<RegCodeUserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return ApiResponse.success(voPage);
    }

    @PostMapping("save")
    public ApiResponse save(@RequestBody RegCodeUserForm form, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(request);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        bindParent(form, request);
        err = validateForm(form, true);
        if (err != null) {
            return ApiResponse.failure(err);
        }

        if (form.getUserId() == null || form.getUserId().isBlank()) {
            LambdaQueryWrapper<SysUsers> existName = new LambdaQueryWrapper<>();
            existName.eq(SysUsers::getUsername, form.getUsername().trim());
            if (this.sysUsersService.count(existName) > 0) {
                return ApiResponse.failure("用户名已存在");
            }
        }
        SysUsers user = resolveOrCreateUser(form, true);
        if (user == null) {
            return ApiResponse.failure("用户创建失败");
        }
        if (regCodeAccessService.getAssignment(user.getId()) != null) {
            return ApiResponse.failure("该用户已是注册码用户");
        }

        Date now = DateUtils.getNow();
        RegCodeUser entity = new RegCodeUser();
        entity.setUserId(user.getId());
        entity.setGenerateLimit(0);
        entity.setGenerateUsed(0);
        entity.setMaxSubUsers(form.getMaxSubUsers() == null ? 0 : form.getMaxSubUsers());
        entity.setStatus(RegCodeAccessService.STATUS_ENABLED);
        entity.setRemark(form.getRemark());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        this.regCodeUserService.save(entity);
        this.regCodeQuotaService.replaceCustomerQuotas(user.getId(), form.getQuotas());
        return ApiResponse.success("保存成功");
    }

    @PostMapping("update")
    public ApiResponse update(@RequestBody RegCodeUserForm form, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(request);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        if (form.getId() == null || form.getId().isBlank()) {
            return ApiResponse.failure("缺少主键");
        }
        // 先确认客户存在（404）、归当前管理员管理（403），再校验表单
        RegCodeUser entity = resolveRow(form.getId());
        if (entity == null) {
            return notFound();
        }
        SysUsers existing = this.sysUsersService.getById(entity.getUserId());
        requireManages(request, existing);
        bindParent(form, request);
        err = validateForm(form, false);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        form.setUserId(entity.getUserId());
        SysUsers user = resolveOrCreateUser(form, false);
        if (user == null) {
            return ApiResponse.failure("用户不存在");
        }

        if (form.getMaxSubUsers() != null) {
            // 调低到小于已有子用户数时，已有子用户保留，只是不能再新建
            entity.setMaxSubUsers(form.getMaxSubUsers());
        }
        entity.setRemark(form.getRemark());
        entity.setUpdateTime(DateUtils.getNow());
        this.regCodeUserService.updateById(entity);
        this.regCodeQuotaService.replaceCustomerQuotas(entity.getUserId(), form.getQuotas());
        return ApiResponse.success("保存成功");
    }

    /**
     * 删除客户（id 可以是列表里的 id（reg_code_user.id），也可以是 userId）：删除客户的次数记录和注册码账号；
     * 客户本身是子账号或注册码客户角色时一并删除其登录账号。
     * 客户在注册码页创建的子用户<b>不删除</b>：保留账号（以后可转给别的客户），自动停用、未用次数作废（不退给任何人），
     * 并吊销它们已登录的 token。返回 {removed, retiredSubUsers, voidedTotal}。
     */
    @PostMapping("del")
    public ApiResponse delete(@RequestBody List<String> idList, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(request);
        if (err != null) {
            return ApiResponse.failure(err);
        }
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        List<RegCodeUser> rows = new ArrayList<>();
        for (String id : idList) {
            RegCodeUser row = resolveRow(id);
            if (row == null) {
                return notFound();
            }
            if (rows.stream().noneMatch(r -> r.getId().equals(row.getId()))) {
                rows.add(row);
            }
        }
        // 先整体做权限检查，避免删到一半才发现某条无权操作
        for (RegCodeUser row : rows) {
            requireManages(request, this.sysUsersService.getById(row.getUserId()));
        }
        List<String> accountUserIds = new ArrayList<>();
        List<String> retired = new ArrayList<>();
        int voided = 0;
        for (RegCodeUser row : rows) {
            SysUsers user = this.sysUsersService.getById(row.getUserId());
            this.regCodeQuotaService.removeAll(row.getUserId());
            if (user == null) {
                continue;
            }
            if (!this.regCodeAccessService.isAdminAccount(user)) {
                RegCodeQuotaService.RetireResult r = this.regCodeQuotaService.retireSubUsers(user.getId());
                retired.addAll(r.getUserIds());
                voided += r.getVoidedTotal();
            }
            boolean isChild = (user.getParentId() != null && !user.getParentId().isBlank())
                    || RegCodeAccessService.ROLE_REGCODE_CLIENT_ID.equals(user.getRoleId());
            if (isChild) {
                accountUserIds.add(user.getId());
            }
        }
        boolean removed = this.regCodeUserService.removeByIds(rows.stream().map(RegCodeUser::getId).toList());
        if (!accountUserIds.isEmpty()) {
            this.sysUsersService.removeByIds(accountUserIds);
        }
        for (String id : accountUserIds) {
            this.authTokenService.revokeAllForUser(id);
        }
        for (String id : retired) {
            this.authTokenService.revokeAllForUser(id);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("removed", removed);
        result.put("retiredSubUsers", retired.size());
        result.put("voidedTotal", voided);
        return ApiResponse.success(result);
    }

    /** 某客户在注册码页创建的子用户，返回结构与 GET /common/regCode/subUsers 相同 */
    @GetMapping("{customerId}/subUsers")
    public ApiResponse listSubUsers(@PathVariable("customerId") String customerId, HttpServletRequest request) {
        requireManagerOrThrow(request);
        SysUsers customer = resolveUser(customerId);
        if (customer == null) {
            return notFound();
        }
        requireManages(request, customer);
        return ApiResponse.success(this.regCodeQuotaService.subUserList(customer));
    }

    /** 子用户各配置次数，返回结构与 GET /common/regCode/subUsers/{id}/quota 相同（creatorRemaining 为创建人的剩余） */
    @GetMapping("subUsers/{subId}/quota")
    public ApiResponse subUserQuota(@PathVariable("subId") String subId, HttpServletRequest request) {
        requireManagerOrThrow(request);
        SysUsers sub = requireSubUser(request, subId);
        return ApiResponse.success(this.regCodeQuotaService.subUserQuota(sub, this.sysUsersService.getById(sub.getParentId())));
    }

    /**
     * 管理员调整子用户次数，body 与 POST /common/regCode/subUsers/{id}/quota 相同 {items:[{configId, delta}]}：
     * 正数由管理员直接增加（不从创建人扣）；负数收回子用户未用的次数并作废（不退回创建人）。返回调整后的次数。
     */
    @PostMapping("subUsers/{subId}/quota")
    public ApiResponse adjustSubUserQuota(@PathVariable("subId") String subId,
                                          @RequestBody(required = false) RegCodeSubUser.DeltaForm form,
                                          HttpServletRequest request) {
        requireManagerOrThrow(request);
        SysUsers sub = requireSubUser(request, subId);
        this.regCodeQuotaService.adjustByAdmin(sub, form == null ? null : form.getItems());
        return ApiResponse.success(this.regCodeQuotaService.subUserQuota(sub, this.sysUsersService.getById(sub.getParentId())));
    }

    /**
     * 客户 / 子用户 id 两种都认：先按 sys_users.id 找，找不到再按 reg_code_user.id 找到对应账号。
     * 管理端列表（getAll）每条同时有 id（reg_code_user.id）和 userId。
     */
    private SysUsers resolveUser(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.trim();
        SysUsers user = this.sysUsersService.getById(key);
        if (user != null) {
            return user;
        }
        RegCodeUser row = this.regCodeUserService.getById(key);
        return row == null || row.getUserId() == null ? null : this.sysUsersService.getById(row.getUserId());
    }

    /** reg_code_user 行：先按行 id 找，找不到再按 user_id 找 */
    private RegCodeUser resolveRow(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.trim();
        RegCodeUser row = this.regCodeUserService.getById(key);
        return row != null ? row : this.regCodeAccessService.getAssignment(key);
    }

    /** 找不到客户：业务错误 {status: 404}（HTTP 200），不是 500 */
    private static ApiResponse notFound() {
        return ApiResponse.withStatus(404, "注册码用户不存在", null);
    }

    private void requireManagerOrThrow(HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(request);
        if (err != null) {
            throw new ForbiddenException(err);
        }
    }

    static final String NOT_OWN_CUSTOMER = "只能管理自己账户下的客户";
    static final String NOT_OWN_SUB_USER = "只能管理自己账户下的子用户";

    /** 非 ROOT 管理员只能看 / 改自己名下的客户（客户的 parent_id = 自己），否则 HTTP 403 */
    private void requireManages(HttpServletRequest request, SysUsers customer) {
        requireManages(request, customer, NOT_OWN_CUSTOMER);
    }

    private void requireManages(HttpServletRequest request, SysUsers customer, String message) {
        String err = denyIfNotOwnChild(request, customer);
        if (err != null) {
            throw new ForbiddenException(NOT_OWN_CUSTOMER.equals(err) ? message : err);
        }
    }

    /** subId 必须是某个客户在注册码页创建的子用户，且该客户归当前管理员管理；否则 403 */
    private SysUsers requireSubUser(HttpServletRequest request, String subId) {
        SysUsers sub = resolveUser(subId);
        if (sub == null || !this.regCodeAccessService.isBottomSubUser(sub)) {
            throw new ForbiddenException("不是注册码页创建的子用户");
        }
        SysUsers customer = this.sysUsersService.getById(sub.getParentId());
        if (customer == null) {
            // 创建人已删除：只有 ROOT 能处理
            if (!this.regCodeAccessService.isRootUser(this.regCodeAccessService.currentUser(request))) {
                throw new ForbiddenException(NOT_OWN_SUB_USER);
            }
            return sub;
        }
        // 目标是子用户：它的创建人不归当前管理员管理时，用“子用户”的说法
        requireManages(request, customer, NOT_OWN_SUB_USER);
        return sub;
    }

    private String validateForm(RegCodeUserForm form, boolean creating) {
        if (form.getQuotas() == null && form.getConfigIds() != null) {
            // 兼容旧版表单：configIds + generateLimit → 每个配置都给 generateLimit 次
            List<RegCodeSubUser.QuotaCount> legacy = new ArrayList<>();
            for (String configId : form.getConfigIds()) {
                RegCodeSubUser.QuotaCount q = new RegCodeSubUser.QuotaCount();
                q.setConfigId(configId);
                q.setCount(form.getGenerateLimit() == null ? 0 : form.getGenerateLimit());
                legacy.add(q);
            }
            form.setQuotas(legacy);
        }
        if (form.getQuotas() == null || form.getQuotas().stream()
                .noneMatch(q -> q != null && q.getConfigId() != null && !q.getConfigId().isBlank())) {
            return "请至少分配一种注册码配置";
        }
        for (RegCodeSubUser.QuotaCount q : form.getQuotas()) {
            if (q != null && (q.getCount() == null || q.getCount() < 0)) {
                return "请填写每个配置的可用次数（不能为负数）";
            }
        }
        if (form.getMaxSubUsers() != null && form.getMaxSubUsers() < 0) {
            return "子用户数量上限不能为负数";
        }
        if (creating && (form.getUserId() == null || form.getUserId().isBlank())) {
            if (form.getUsername() == null || form.getUsername().isBlank()) {
                return "请输入用户名";
            }
            String username = form.getUsername().trim();
            if (username.length() < 3 || username.length() > 20) {
                return "用户名长度须为 3-20 个字符";
            }
            if (form.getPassword() == null || form.getPassword().length() < 6) {
                return "密码长度不能少于6位";
            }
        }
        if (form.getParentId() == null || form.getParentId().isBlank()) {
            return "请选择所属父用户";
        }
        SysUsers parent = this.sysUsersService.getById(form.getParentId());
        if (parent == null) {
            return "父用户不存在";
        }
        if (parent.getParentId() != null && !parent.getParentId().isBlank()) {
            return "只能挂在主用户下，不能再挂到子用户下";
        }
        if (form.getUserId() != null && form.getUserId().equals(form.getParentId())) {
            return "不能把用户挂到自己下面";
        }
        return null;
    }

    private void bindParent(RegCodeUserForm form, HttpServletRequest request) {
        SysUsers operator = this.regCodeAccessService.currentUser(request);
        if (operator == null || this.regCodeAccessService.isRootUser(operator)) {
            return;
        }
        form.setParentId(operator.getId());
    }

    private String denyIfNotOwnChild(HttpServletRequest request, SysUsers target) {
        SysUsers operator = this.regCodeAccessService.currentUser(request);
        if (operator == null) {
            return "请先登录";
        }
        if (this.regCodeAccessService.isRootUser(operator)) {
            return null;
        }
        if (target == null) {
            return null;
        }
        if (target.getParentId() == null || !operator.getId().equals(target.getParentId())) {
            return NOT_OWN_CUSTOMER;
        }
        return null;
    }

    private SysUsers resolveOrCreateUser(RegCodeUserForm form, boolean creating) {
        if (form.getUserId() != null && !form.getUserId().isBlank()) {
            SysUsers exist = this.sysUsersService.getById(form.getUserId());
            if (exist == null) {
                return null;
            }
            if (form.getNickname() != null) {
                exist.setNickname(form.getNickname());
            }
            if (form.getEmail() != null) {
                exist.setEmail(form.getEmail());
            }
            if (form.getPassword() != null && !form.getPassword().isBlank()) {
                exist.setPassword(form.getPassword());
            }
            if (form.getRoleId() != null && !form.getRoleId().isBlank()) {
                exist.setRoleId(form.getRoleId());
            }
            if (form.getParentId() != null && !form.getParentId().isBlank()) {
                exist.setParentId(form.getParentId());
            }
            this.sysUsersService.updateById(exist);
            return exist;
        }

        if (!creating) {
            return null;
        }

        SysUsers user = new SysUsers();
        user.setUsername(form.getUsername().trim());
        user.setPassword(form.getPassword());
        user.setNickname(form.getNickname());
        user.setEmail(form.getEmail());
        user.setRoleId(form.getRoleId() == null || form.getRoleId().isBlank()
                ? RegCodeAccessService.ROLE_REGCODE_CLIENT_ID
                : form.getRoleId());
        user.setParentId(form.getParentId());
        user.setCreateTime(DateUtils.getNow());
        this.sysUsersService.save(user);
        return user;
    }

    private RegCodeUserVO toVO(RegCodeUser entity) {
        RegCodeUserVO vo = new RegCodeUserVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        List<RegCodeSubUser.QuotaItem> quotas = this.regCodeAccessService.quotaItems(entity.getUserId());
        vo.setQuotas(quotas);
        vo.setGenerateLimit(quotas.stream().mapToInt(RegCodeSubUser.QuotaItem::getAllocated).sum());
        vo.setGenerateUsed(quotas.stream().mapToInt(RegCodeSubUser.QuotaItem::getUsed).sum());
        vo.setRemaining(quotas.stream().mapToInt(RegCodeSubUser.QuotaItem::getRemaining).sum());
        vo.setMaxSubUsers(entity.getMaxSubUsers() == null ? 0 : entity.getMaxSubUsers());
        vo.setStatus(entity.getStatus() == null ? RegCodeAccessService.STATUS_ENABLED : entity.getStatus());
        vo.setSubUserCount(this.regCodeAccessService.enabledSubUserCount(entity.getUserId()));
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());

        SysUsers user = this.sysUsersService.getById(entity.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setEmail(user.getEmail());
            vo.setRoleId(user.getRoleId());
            vo.setParentId(user.getParentId());
            if (user.getRoleId() != null) {
                SysRoles role = this.sysRolesService.getById(user.getRoleId());
                if (role != null) {
                    vo.setRoleName(role.getRoleName());
                }
            }
            if (user.getParentId() != null && !user.getParentId().isBlank()) {
                SysUsers parent = this.sysUsersService.getById(user.getParentId());
                if (parent != null) {
                    vo.setParentUsername(parent.getUsername());
                    vo.setParentNickname(parent.getNickname());
                }
            }
        }

        List<String> configIds = this.regCodeAccessService.listAssignedConfigIds(entity.getUserId());
        vo.setConfigIds(configIds);
        if (!configIds.isEmpty()) {
            Map<String, RegCodeConfig> configMap = this.regCodeConfigService.listByIds(configIds).stream()
                    .collect(Collectors.toMap(RegCodeConfig::getId, Function.identity(), (a, b) -> a));
            List<String> labels = new ArrayList<>();
            for (String configId : configIds) {
                RegCodeConfig config = configMap.get(configId);
                if (config == null) {
                    continue;
                }
                labels.add(RegCodeAccessService.configLabel(config));
            }
            vo.setConfigLabels(labels);
        } else {
            vo.setConfigLabels(Collections.emptyList());
        }
        return vo;
    }
}
