package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springboot.DTO.RegCode;
import springboot.DTO.RegCodeConfigOptionVO;
import springboot.DTO.RegCodeSubUser;
import springboot.domain.ComRegistration;
import springboot.domain.RegCodeConfig;
import springboot.domain.SysUsers;
import springboot.enums.RegCodeType;
import springboot.service.AuthTokenService;
import springboot.service.RegCodeAccessService;
import springboot.service.RegCodeConfigService;
import springboot.service.RegCodeQuotaService;
import springboot.service.SysUsersService;
import springboot.utils.ForbiddenException;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.HashUtil;
import springboot.utils.OperatorUtils;

import java.util.Collections;
import java.util.List;

/**
 * 管理端和用户端共用的注册码接口（/common/**）。
 * 所有接口都先走 {@link RegCodeAccessService#requireRegCode}（角色有注册码生成菜单 / ROOT /
 * 创建人仍有效的底层子用户），不满足 HTTP 403。
 * /common/regCode/subUsers/** 另外要求是顶层账号，且只能操作 parent_id = 自己的子用户（否则 403）。
 */
@RestController
@RequestMapping("/common")
public class CommonRegCodeController {

    private final RegCodeAccessService regCodeAccessService;
    private final RegCodeConfigService regCodeConfigService;
    private final RegCodeQuotaService regCodeQuotaService;
    private final SysUsersService sysUsersService;
    private final AuthTokenService authTokenService;

    public CommonRegCodeController(RegCodeAccessService regCodeAccessService,
                                   RegCodeConfigService regCodeConfigService,
                                   RegCodeQuotaService regCodeQuotaService,
                                   SysUsersService sysUsersService,
                                   AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
        this.regCodeAccessService = regCodeAccessService;
        this.regCodeConfigService = regCodeConfigService;
        this.regCodeQuotaService = regCodeQuotaService;
        this.sysUsersService = sysUsersService;
    }

    /**
     * 生成页可选的注册码配置：只返回分配了次数的（ROOT 返回全部；没有分配时返回空数组，前端显示“暂无可用配置”），
     * 不返回 encryptSuffix、encryptType。合并了原 GET /regCodeConfig/list 与 POST /regCodeConfig/available。
     */
    @GetMapping("/regCodeConfig/list")
    public ApiResponse listConfigs(HttpServletRequest request) {
        SysUsers user = this.regCodeAccessService.requireRegCode(request);
        List<String> allowed = this.regCodeAccessService.allowedConfigIds(user);
        if (allowed != null && allowed.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }
        LambdaQueryWrapper<RegCodeConfig> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(RegCodeConfig::getId, allowed);
        }
        wrapper.orderByAsc(RegCodeConfig::getSortOrder)
                .orderByAsc(RegCodeConfig::getCompany)
                .orderByAsc(RegCodeConfig::getName);
        return ApiResponse.success(this.regCodeConfigService.list(wrapper).stream()
                .map(RegCodeConfigOptionVO::of)
                .toList());
    }

    /** 我的各配置次数（合并原 GET / POST /regCodeUser/myQuota，只按 token）；子用户就是创建人分给自己的次数 */
    @GetMapping("/regCodeUser/myQuota")
    public ApiResponse myQuota(HttpServletRequest request) {
        SysUsers user = this.regCodeAccessService.requireRegCode(request);
        return ApiResponse.success(this.regCodeAccessService.quotaOf(user));
    }

    // ------------------------------------------------------------ 子用户（只有顶层账号能管理，只能管理 parent_id = 自己的）

    /** 我创建的子用户：{createdCount（启用中的）, maxSubUsers, canCreate, items:[{id, username, nickname, status, createTime, usedTotal, allocatedTotal}]} */
    @GetMapping("/regCode/subUsers")
    public ApiResponse listSubUsers(HttpServletRequest request) {
        SysUsers me = requireSubUserManager(request);
        return ApiResponse.success(this.regCodeQuotaService.subUserList(me));
    }

    /** 新建子用户：body {username, nickname, password, quotas:[{configId, count}]}，次数从自己剩余里划拨 */
    @PostMapping("/regCode/subUsers")
    public ApiResponse createSubUser(@RequestBody(required = false) RegCodeSubUser.CreateForm form,
                                     HttpServletRequest request) {
        SysUsers me = requireSubUserManager(request);
        SysUsers created = this.regCodeQuotaService.createSubUser(me, form);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", created.getId());
        data.put("username", created.getUsername());
        return ApiResponse.success(data);
    }

    /** 子用户各配置 {used, allocated, remaining}、停用会退回的合计 refundableTotal，以及我自己各配置的剩余 */
    @GetMapping("/regCode/subUsers/{id}/quota")
    public ApiResponse subUserQuota(@PathVariable("id") String id, HttpServletRequest request) {
        SysUsers me = requireSubUserManager(request);
        SysUsers sub = ownSubUser(me, id);
        return ApiResponse.success(this.regCodeQuotaService.subUserQuota(sub, me));
    }

    /** 调整子用户次数：body {items:[{configId, delta}]}，正数从我的剩余追加，负数收回未用的 */
    @PostMapping("/regCode/subUsers/{id}/quota")
    public ApiResponse adjustSubUserQuota(@PathVariable("id") String id,
                                          @RequestBody(required = false) RegCodeSubUser.DeltaForm form,
                                          HttpServletRequest request) {
        SysUsers me = requireSubUserManager(request);
        SysUsers sub = ownSubUser(me, id);
        this.regCodeQuotaService.adjustByCreator(me, sub, form == null ? null : form.getItems());
        return ApiResponse.success(this.regCodeQuotaService.subUserQuota(sub, me));
    }

    /** 停用 / 启用：body {status: 0|1}；停用返回 {status, refunded:[{configId, count}], refundedTotal} */
    @PostMapping("/regCode/subUsers/{id}/status")
    public ApiResponse setSubUserStatus(@PathVariable("id") String id,
                                        @RequestBody(required = false) RegCodeSubUser.StatusForm form,
                                        HttpServletRequest request) {
        SysUsers me = requireSubUserManager(request);
        SysUsers sub = ownSubUser(me, id);
        if (form == null || form.getStatus() == null) {
            return ApiResponse.failure("请指定状态");
        }
        RegCodeSubUser.StatusResult result = this.regCodeQuotaService.setStatus(me, sub, form.getStatus());
        if (form.getStatus() == RegCodeAccessService.STATUS_DISABLED) {
            // 停用后该子用户已登录的 token 全部作废，之后任何请求都是 401
            this.authTokenService.revokeAllForUser(sub.getId());
        }
        return ApiResponse.success(result);
    }

    /** 重置子用户密码：返回 {password}，新密码只在这一次返回 */
    @PostMapping("/regCode/subUsers/{id}/resetPassword")
    public ApiResponse resetSubUserPassword(@PathVariable("id") String id, HttpServletRequest request) {
        SysUsers me = requireSubUserManager(request);
        SysUsers sub = ownSubUser(me, id);
        String password = this.regCodeQuotaService.resetPassword(sub);
        // 重置密码后旧 token 全部作废
        this.authTokenService.revokeAllForUser(sub.getId());
        return ApiResponse.success(java.util.Map.of("password", password));
    }

    private SysUsers requireSubUserManager(HttpServletRequest request) {
        SysUsers me = this.regCodeAccessService.requireRegCode(request);
        if (!this.regCodeAccessService.canManageSubUsers(me)) {
            throw new ForbiddenException("子用户不能再创建或管理子用户");
        }
        return me;
    }

    /**
     * 必须是 parent_id = 我 的注册码子用户（角色 role_regcode_client；螃蟹出货等其他子账号不归这里管）；
     * 不存在也按无权限处理（403），不泄露 id 是否存在
     */
    private SysUsers ownSubUser(SysUsers me, String id) {
        SysUsers sub = id == null || id.isBlank() ? null : this.sysUsersService.getById(id.trim());
        if (sub == null || sub.getParentId() == null || !sub.getParentId().trim().equals(me.getId())
                || !RegCodeAccessService.isRegCodeRole(sub.getRoleId())) {
            throw new ForbiddenException("只能管理自己创建的子用户");
        }
        return sub;
    }

    @PostMapping("/regCode/genTempRegCode")
    public ApiResponse genTempRegCode(@RequestBody RegCode regCode, HttpServletRequest request){

        RegCode one = regCode;
        // 注册码访问规则见 RegCodeAccessService#canUseRegCode，不满足直接 403
        SysUsers operator = this.regCodeAccessService.requireRegCode(request);
        String operatorId = operator.getId();
        // 前端传的 applyId 一律忽略，只认 token
        one.setApplyId(operatorId);

        boolean root = this.regCodeAccessService.isRootUser(operator);
        if (!root) {
            List<String> allowed = this.regCodeAccessService.allowedConfigIds(operator);
            if (allowed == null || allowed.isEmpty()) {
                return ApiResponse.failure("暂无可用配置，请联系管理员分配");
            }
        }
        RegCodeConfig config = resolveRegCodeConfig(one);
        if (config == null && (one.getRegCodeType() == null || one.getRegCodeType() == 0)) {
            return ApiResponse.failure("请选择注册码配置或类型");
        }
        if (config != null) {
            String deny = this.regCodeAccessService.assertCanGenerate(operator, config.getId());
            if (deny != null) {
                return ApiResponse.failure(deny);
            }
        } else if (!this.regCodeAccessService.adminMayGenerate(operator)) {
            return ApiResponse.failure("无权使用未分配的注册码类型");
        }

        String suffix;
        String algorithm = "MD5";
        if (config != null) {
            suffix = config.getEncryptSuffix() == null ? "" : config.getEncryptSuffix();
            if (config.getEncryptType() != null && !config.getEncryptType().isBlank()) {
                algorithm = config.getEncryptType();
            }
            if (one.getCompany() == null || one.getCompany().isBlank()) {
                one.setCompany(config.getCompany());
            }
            if (one.getApplyName() == null || one.getApplyName().isBlank()) {
                one.setApplyName(config.getName());
            }
        } else {
            suffix = RegCodeType.getDescriptionByCode(one.getRegCodeType());
        }

        // charge-tool 的 SHA-256 规则是 HMAC-SHA256(message=注册码, key=配置后缀)，
        // 不是将后缀直接拼到注册码后再做普通 SHA-256。
        String validCode = "SHA-256".equalsIgnoreCase(algorithm) || "SHA256".equalsIgnoreCase(algorithm)
                ? HashUtil.hmacSha256(one.getRegCode(), suffix)
                : HashUtil.hash(one.getRegCode() + suffix, algorithm);

        // 获取前 6 位和前 12 位
        String firstSix = validCode.length() >= 6 ? validCode.substring(0, 6) : validCode;
        String firstSeven = validCode.length() >= 12 ? validCode.substring(0, 7) : validCode;
        String firstEight = validCode.length() >= 12 ? validCode.substring(0, 8) : validCode;
        String firstNine = validCode.length() >= 12 ? validCode.substring(0, 9) : validCode;
        String firstTen = validCode.length() >= 12 ? validCode.substring(0, 10) : validCode;
        String firstTwelve = validCode.length() >= 12 ? validCode.substring(0, 12) : validCode;

        one.setOneMonthValid(firstSix);
        one.setTwoMonthValid(firstSeven);
        one.setFourMonthValid(firstEight);
        one.setSixMonthValid(firstNine);
        one.setThirteenMonthValid(firstTen);
        one.setLongTimeValid(firstTwelve);

        ComRegistration record = new ComRegistration();
        record.setApplyName(one.getApplyName());
        record.setCompany(one.getCompany());
        record.setRegCode(one.getRegCode());
        record.setRegCodeType(one.getRegCodeType());
        record.setOneMonthValid(firstSix);
        record.setLongTimeValid(firstTwelve);
        record.setApplyId(one.getApplyId());
        record.setOperator(OperatorUtils.resolve(one.getApplyId()));
        record.setApplyStatus(1);
        record.setRemarks(config != null
                ? "临时注册码生成 / " + config.getCompany() + " / " + config.getName()
                : "临时注册码生成");
        record.setCreateTime(DateUtils.getNow());
        // 扣次数（条件更新）与保存记录在同一事务里，次数被并发用完时不会多生成
        this.regCodeQuotaService.consumeAndRecord(operator, config == null ? null : config.getId(), record);

        return ApiResponse.success(one);
    }

    private RegCodeConfig resolveRegCodeConfig(RegCode one) {
        if (one.getConfigId() != null && !one.getConfigId().isBlank()) {
            return this.regCodeConfigService.getById(one.getConfigId());
        }
        if (one.getCompany() != null && !one.getCompany().isBlank()
                && one.getApplyName() != null && !one.getApplyName().isBlank()) {
            LambdaQueryWrapper<RegCodeConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RegCodeConfig::getCompany, one.getCompany())
                    .eq(RegCodeConfig::getName, one.getApplyName())
                    .last("LIMIT 1");
            return this.regCodeConfigService.getOne(wrapper, false);
        }
        return null;
    }
}
