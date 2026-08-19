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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
//        if (!passwordEncoder.matches(password, user.getPassword())) {
//            return ApiResponse.failure("密码错误");
//        }
        // 登录成功，生成 Token 或其他逻辑
//        String token = jwtUtil.generateToken(username);
        return ApiResponse.success(user);
    }
    /**
     * 获取菜单路由列表
     * */
    @GetMapping("getMenuList")
    public ApiResponse getMenuList() {

        String roleid = "8b69d138f8ca6f527d816e20fa76f29e";


        List<SysMenus> menuListByRoleId = sysGeneralService.getMenuListByRoleId(roleid);

        return ApiResponse.success(menuListByRoleId);
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

        this.comRegistrationService.updateById(one);

        SysInfo sysInfo = new SysInfo();
        sysInfo.setInfoDes("您的申请已通过，操作人：" + comRegistration.getId() + "\\n" +
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
        if (one.getRegCodeType() == null || one.getRegCodeType() == 0) {
            return ApiResponse.failure("注册码类型存在问题！");
        }


        String validCode =  MD5Util.hash(one.getRegCode() + RegCodeType.getDescriptionByCode(one.getRegCodeType()));

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

        return ApiResponse.success(one);
    }

}
