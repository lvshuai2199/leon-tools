package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.DTO.RegCode;
import springboot.domain.*;
import springboot.enums.RegCodeType;
import springboot.service.*;
import springboot.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("auth")
@Slf4j
public class SysGeneralController {


    @Autowired
    private SysUsersService sysUsersService;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;
    @Autowired
    RedisUtil redisUtil = new RedisUtil();

    @Autowired
    private SysGeneralService sysGeneralService;

    @Autowired
    private SysTasksService sysTasksService;

    @Autowired
    private ComRegistrationService comRegistrationService;

    @Autowired
    private SysInfoService sysInfoService;

    @Autowired
    private SysMenusService sysMenusService;

    @Autowired
    private SysRolesService sysRolesService;

    @Autowired
    private RegCodeConfigService regCodeConfigService;

    @PostMapping("captcha")
    public ApiResponse getCaptcha() {
        // 生成验证码文本
        String captchaText = CaptchaUtil.generateCaptchaText(6);
        // 生成验证码图片
        BufferedImage captchaImage = CaptchaUtil.generateCaptchaImage(captchaText);
        // 将验证码图片转换为 Base64
        String captchaBase64 = CaptchaUtil.convertImageToBase64(captchaImage);
        // 生成验证码的唯一标识
        String captchaKey = UUID.randomUUID().toString();
        // 将验证码文本存储到 Redis 中，设置过期时间（如 5 分钟）
//        redisUtil.save(captchaKey, captchaText, 5, TimeUnit.MINUTES);
        // 返回验证码标识和 Base64 图片
        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("captchaBase64", captchaBase64);
        return ApiResponse.success(result);
    }


    @PostMapping("/login")
    public ApiResponse login(@RequestBody Map<String, String> loginData) {
        // 获取登录数据
        String username = loginData.get("username");
        String password = loginData.get("password");
        String captchaKey = loginData.get("captchaKey");
        String captchaCode = loginData.get("captchaCode");
        // 校验验证码
        String storedCaptcha = redisUtil.get(captchaKey);
//        String storedCaptcha = redisUtil.get(username);
        if (storedCaptcha == null) {
            return ApiResponse.failure("验证码已过期，请重新获取");
        }
        if (!storedCaptcha.equalsIgnoreCase(captchaCode)) {
            return ApiResponse.failure("验证码错误");
        }
        // 通过用户名和密码检索数据库中是否存在对应的数据项
        LambdaQueryWrapper<SysUsers> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(SysUsers::getUsername, username).eq(SysUsers::getPassword, password);
        SysUsers user = this.sysUsersService.getOne(lambdaQueryWrapper);
        // 校验用户名和密码
//        if (user == null) {
//            return ApiResponse.failure("用户名不存在");
//        }
//        if (!passwordEncoder.matches(password, user.getPassword())) {
//            return ApiResponse.failure("密码错误");
//        }
        // 登录成功，生成 Token 或其他逻辑
//        String token = jwtUtil.generateToken(username);
//        return ApiResponse.success("登录成功", token);
        fillRoleName(user);
        return ApiResponse.success(user);
    }

    @PostMapping("/login2")
    public ApiResponse login2(@RequestBody Map<String, String> loginData) {
        // 获取登录数据
        String username = loginData.get("username");
        String password = loginData.get("password");

        // 通过用户名和密码检索数据库中是否存在对应的数据项
        LambdaQueryWrapper<SysUsers> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(SysUsers::getUsername, username).eq(SysUsers::getPassword, password);
        SysUsers user = this.sysUsersService.getOne(lambdaQueryWrapper);
        // 校验用户名和密码
        if (user == null) {
            return ApiResponse.failure("用户不存在");
        }
        fillRoleName(user);
        return ApiResponse.success(user);
    }
    /**
     * 获取菜单路由列表
     *
     * 根据当前用户角色过滤：用户名非空时，按「用户 → 角色 → sys_role_menu」返回其可访问的菜单
     * （并补全被分配菜单的祖先目录，保证树结构完整）；超级管理员（root）与未携带用户名时返回全部菜单。
     *
     * @param username 当前登录用户名（可选）
     * */
    @GetMapping("getMenuList")
    public ApiResponse getMenuList(@RequestParam(value = "username", required = false) String username) {
        // 未携带用户名（兼容旧调用）：返回全部菜单
        if (username == null || username.isEmpty()) {
            return ApiResponse.success(listAllMenus());
        }

        // 用户 → 角色
        LambdaQueryWrapper<SysUsers> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUsers::getUsername, username);
        SysUsers user = sysUsersService.getOne(userWrapper);
        if (user == null || user.getRoleId() == null || user.getRoleId().isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }

        SysRoles role = sysRolesService.getById(user.getRoleId());
        // 超级管理员 ROOT：默认拥有全部菜单，不走角色-路由配置
        if (RoleUtils.isRoot(role)) {
            return ApiResponse.success(listAllMenus());
        }

        // 角色已分配的菜单
        List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(user.getRoleId());
        if (menuIds == null || menuIds.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }

        // 补全被分配菜单的所有祖先目录，保证目录与子菜单树结构完整
        Set<String> visibleIds = new HashSet<>(menuIds);
        List<SysMenus> allMenus = sysMenusService.list();
        Map<String, SysMenus> menuMap = allMenus.stream()
                .collect(Collectors.toMap(SysMenus::getId, m -> m, (a, b) -> a));
        for (String id : menuIds) {
            SysMenus cur = menuMap.get(id);
            while (cur != null && cur.getParentId() != null && !"0".equals(cur.getParentId())) {
                visibleIds.add(cur.getParentId());
                cur = menuMap.get(cur.getParentId());
            }
        }

        LambdaQueryWrapper<SysMenus> queryWrapper = new LambdaQueryWrapper<>();
        // 0目录 1菜单 2按钮：路由只取目录与菜单
        queryWrapper.ne(SysMenus::getMenuType, 2);
        queryWrapper.in(SysMenus::getId, visibleIds);
        queryWrapper.orderByAsc(SysMenus::getSortOrder);

        return ApiResponse.success(sysMenusService.list(queryWrapper));
    }

    /** 查询全部目录与菜单（按钮除外），按排序返回 */
    private List<SysMenus> listAllMenus() {
        LambdaQueryWrapper<SysMenus> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(SysMenus::getMenuType, 2);
        queryWrapper.orderByAsc(SysMenus::getSortOrder);
        return sysMenusService.list(queryWrapper);
    }

    private void fillRoleName(SysUsers user) {
        if (user == null || user.getRoleId() == null || user.getRoleId().isEmpty()) {
            return;
        }
        SysRoles role = sysRolesService.getById(user.getRoleId());
        if (role != null) {
            user.setRoleName(role.getRoleName());
        }
    }



    @PostMapping("abnormalUpload")
    public ApiResponse<SysTasks> abnormalUpload(@RequestBody SysTasks sysTasks) {
        // 检查任务是否存在
        SysTasks curTask = this.sysTasksService.getById(sysTasks.getId());
        if (curTask == null) {
            return ApiResponse.failure("任务不存在");
        }

        // 获取当前任务的备注并进行非空检查
        String currentRemarks = curTask.getRemarks() != null ? curTask.getRemarks() : "";
        String errInfo = sysTasks.getRemarks() != null ? sysTasks.getRemarks() : ""; // 异常信息
        String completeRemarks = currentRemarks + errInfo + "END"; // 追加并添加分隔符

        // 更新备注
        sysTasks.setRemarks(completeRemarks);

        // 更新任务信息
        boolean updated = this.sysTasksService.updateById(sysTasks);

        // 检查更新操作是否成功
        if (updated) {
            return ApiResponse.success(sysTasks); // 返回更新后的任务信息
        } else {
            return ApiResponse.failure("更新任务失败，请重试");
        }
    }


    @PostMapping("getRegCode")
    public ApiResponse genRegCode(@RequestBody ComRegistration comRegistration){

        LambdaQueryWrapper<ComRegistration> queryWrapper =new LambdaQueryWrapper<>();
        queryWrapper.eq(ComRegistration::getId,comRegistration.getId());

        ComRegistration one = this.comRegistrationService.getOne(queryWrapper);

        if (one.getRegCodeType() == null || one.getRegCodeType() == 0) {
            return ApiResponse.failure("注册码类型存在问题！");
        }


        String validCode =  MD5Util.hash(one.getRegCode() + RegCodeType.getDescriptionByCode(one.getRegCodeType()));


        // 获取前 6 位和前 12 位
        String firstSix = validCode.length() >= 6 ? validCode.substring(0, 6) : validCode;
        String firstTwelve = validCode.length() >= 12 ? validCode.substring(0, 12) : validCode;

        one.setOneMonthValid(firstSix);
        one.setLongTimeValid(firstTwelve);

        one.setApplyStatus(1);
        one.setOperator(OperatorUtils.resolve(comRegistration.getOperator()));

        this.comRegistrationService.updateById(one);

        SysInfo sysInfo = new SysInfo();
        sysInfo.setInfoDes("您的申请已通过，操作人：" + one.getOperator() + "\\n" +
                "类型：" + RegCodeType.getDescriptionByCode(comRegistration.getRegCodeType()) + "\\n"
                + "注册码：\\n" +
                "单月：" + one.getOneMonthValid() + "\\n" +
                "永久：" + one.getLongTimeValid());
        sysInfo.setInfoStatus(0);
        sysInfo.setUserId(comRegistration.getApplyId());
        sysInfo.setInfoType(2);
        sysInfo.setCreateTime(DateUtils.getNow());

        sysInfo.setPublicId("1");

        this.sysInfoService.save(sysInfo);

        return ApiResponse.success(one);
    }

    @PostMapping("genTempRegCode")
    public ApiResponse genTempRegCode(@RequestBody RegCode regCode){

        RegCode one = regCode;
        RegCodeConfig config = resolveRegCodeConfig(one);
        if (config == null && (one.getRegCodeType() == null || one.getRegCodeType() == 0)) {
            return ApiResponse.failure("请选择注册码配置或类型");
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

        String validCode = HashUtil.hash(one.getRegCode() + suffix, algorithm);

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
