package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springboot.DTO.RegCode;
import springboot.DTO.RegCodeConfigOptionVO;
import springboot.domain.ComRegistration;
import springboot.domain.RegCodeConfig;
import springboot.domain.SysUsers;
import springboot.enums.RegCodeType;
import springboot.service.ComRegistrationService;
import springboot.service.RegCodeAccessService;
import springboot.service.RegCodeConfigService;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.HashUtil;
import springboot.utils.OperatorUtils;

import java.util.Collections;
import java.util.List;

/**
 * 管理端和用户端共用的注册码生成接口（/common/**）。
 * 三个接口都先走 {@link RegCodeAccessService#requireRegCode}：只允许注册码用户、ROOT、
 * 以及父用户是注册码用户或 ROOT 的子账号，其他人 HTTP 403。
 */
@RestController
@RequestMapping("/common")
public class CommonRegCodeController {

    private final RegCodeAccessService regCodeAccessService;
    private final RegCodeConfigService regCodeConfigService;
    private final ComRegistrationService comRegistrationService;

    public CommonRegCodeController(RegCodeAccessService regCodeAccessService,
                                   RegCodeConfigService regCodeConfigService,
                                   ComRegistrationService comRegistrationService) {
        this.regCodeAccessService = regCodeAccessService;
        this.regCodeConfigService = regCodeConfigService;
        this.comRegistrationService = comRegistrationService;
    }

    /**
     * 生成页可选的注册码配置：只返回分配给自己的（ROOT / 未分配过的账号返回全部，沿用原规则），
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

    /** 我的生成额度（合并原 GET / POST /regCodeUser/myQuota，只按 token） */
    @GetMapping("/regCodeUser/myQuota")
    public ApiResponse myQuota(HttpServletRequest request) {
        SysUsers user = this.regCodeAccessService.requireRegCode(request);
        return ApiResponse.success(this.regCodeAccessService.quotaOf(user.getId()));
    }

    @PostMapping("/regCode/genTempRegCode")
    public ApiResponse genTempRegCode(@RequestBody RegCode regCode, HttpServletRequest request){

        RegCode one = regCode;
        // 注册码访问规则（注册码用户 / ROOT / 父用户是二者之一的子账号），不满足直接 403
        SysUsers operator = this.regCodeAccessService.requireRegCode(request);
        String operatorId = operator.getId();
        // 前端传的 applyId 一律忽略，只认 token
        one.setApplyId(operatorId);

        RegCodeConfig config = resolveRegCodeConfig(one);
        if (config == null && (one.getRegCodeType() == null || one.getRegCodeType() == 0)) {
            return ApiResponse.failure("请选择注册码配置或类型");
        }
        if (config != null) {
            String deny = this.regCodeAccessService.assertCanGenerate(operatorId, config.getId());
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
        this.comRegistrationService.save(record);
        this.regCodeAccessService.consumeQuota(operatorId);

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
