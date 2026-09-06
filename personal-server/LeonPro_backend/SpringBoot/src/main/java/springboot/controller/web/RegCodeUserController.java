package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import springboot.DTO.RegCodeUserForm;
import springboot.DTO.RegCodeUserVO;
import springboot.domain.RegCodeConfig;
import springboot.domain.RegCodeUser;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.RegCodeConfigService;
import springboot.service.RegCodeUserService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.RequestUserUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("regCodeUser")
public class RegCodeUserController {

    private final RegCodeUserService regCodeUserService;
    private final RegCodeAccessService regCodeAccessService;
    private final RegCodeConfigService regCodeConfigService;
    private final SysUsersService sysUsersService;
    private final SysRolesService sysRolesService;

    public RegCodeUserController(RegCodeUserService regCodeUserService,
                                 RegCodeAccessService regCodeAccessService,
                                 RegCodeConfigService regCodeConfigService,
                                 SysUsersService sysUsersService,
                                 SysRolesService sysRolesService) {
        this.regCodeUserService = regCodeUserService;
        this.regCodeAccessService = regCodeAccessService;
        this.regCodeConfigService = regCodeConfigService;
        this.sysUsersService = sysUsersService;
        this.sysRolesService = sysRolesService;
    }

    @GetMapping("getAll")
    public ApiResponse selectAll(Page<RegCodeUser> page, String username, String parentId, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(RequestUserUtils.currentUserId(request));
        if (err != null) {
            return ApiResponse.failure(err);
        }

        LambdaQueryWrapper<RegCodeUser> wrapper = new LambdaQueryWrapper<>();
        List<String> userIds = null;
        if ((username != null && !username.isBlank()) || (parentId != null && !parentId.isBlank())) {
            LambdaQueryWrapper<SysUsers> userWrapper = new LambdaQueryWrapper<>();
            if (username != null && !username.isBlank()) {
                userWrapper.like(SysUsers::getUsername, username.trim());
            }
            if (parentId != null && !parentId.isBlank()) {
                userWrapper.eq(SysUsers::getParentId, parentId.trim());
            }
            userIds = sysUsersService.list(userWrapper).stream()
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
        wrapper.orderByDesc(RegCodeUser::getCreateTime);
        Page<RegCodeUser> result = this.regCodeUserService.page(page, wrapper);

        Page<RegCodeUserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return ApiResponse.success(voPage);
    }

    @GetMapping("myQuota")
    public ApiResponse myQuota(HttpServletRequest request) {
        return quota(RequestUserUtils.currentUserId(request), null);
    }

    @PostMapping("myQuota")
    public ApiResponse myQuotaPost(@RequestBody(required = false) java.util.Map<String, String> body,
                                   HttpServletRequest request) {
        String userId = RequestUserUtils.currentUserId(request);
        String username = body == null ? null : body.get("username");
        if (userId == null || userId.isBlank()) {
            userId = body == null ? null : body.get("userId");
        }
        return quota(userId, username);
    }

    private ApiResponse quota(String userId, String username) {
        springboot.domain.SysUsers user = this.regCodeAccessService.findUser(userId, username);
        if (user == null) {
            return ApiResponse.failure("请先登录");
        }
        return ApiResponse.success(this.regCodeAccessService.quotaOf(user.getId()));
    }

    @PostMapping("save")
    public ApiResponse save(@RequestBody RegCodeUserForm form, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(RequestUserUtils.currentUserId(request));
        if (err != null) {
            return ApiResponse.failure(err);
        }
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
        entity.setGenerateLimit(form.getGenerateLimit());
        entity.setGenerateUsed(0);
        entity.setRemark(form.getRemark());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        this.regCodeUserService.save(entity);
        this.regCodeAccessService.replaceConfigs(user.getId(), form.getConfigIds());
        return ApiResponse.success("保存成功");
    }

    @PostMapping("update")
    public ApiResponse update(@RequestBody RegCodeUserForm form, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(RequestUserUtils.currentUserId(request));
        if (err != null) {
            return ApiResponse.failure(err);
        }
        if (form.getId() == null || form.getId().isBlank()) {
            return ApiResponse.failure("缺少主键");
        }
        err = validateForm(form, false);
        if (err != null) {
            return ApiResponse.failure(err);
        }

        RegCodeUser entity = this.regCodeUserService.getById(form.getId());
        if (entity == null) {
            return ApiResponse.failure("注册码用户不存在");
        }
        form.setUserId(entity.getUserId());
        SysUsers user = resolveOrCreateUser(form, false);
        if (user == null) {
            return ApiResponse.failure("用户不存在");
        }

        entity.setGenerateLimit(form.getGenerateLimit());
        if (form.getGenerateUsed() != null) {
            entity.setGenerateUsed(Math.max(form.getGenerateUsed(), 0));
        }
        entity.setRemark(form.getRemark());
        entity.setUpdateTime(DateUtils.getNow());
        this.regCodeUserService.updateById(entity);
        this.regCodeAccessService.replaceConfigs(entity.getUserId(), form.getConfigIds());
        return ApiResponse.success("保存成功");
    }

    @PostMapping("del")
    public ApiResponse delete(@RequestBody List<String> idList, HttpServletRequest request) {
        String err = regCodeAccessService.requireManager(RequestUserUtils.currentUserId(request));
        if (err != null) {
            return ApiResponse.failure(err);
        }
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        List<RegCodeUser> rows = this.regCodeUserService.listByIds(idList);
        List<String> childUserIds = new ArrayList<>();
        for (RegCodeUser row : rows) {
            this.regCodeAccessService.replaceConfigs(row.getUserId(), Collections.emptyList());
            SysUsers user = this.sysUsersService.getById(row.getUserId());
            if (user == null) {
                continue;
            }
            boolean isChild = (user.getParentId() != null && !user.getParentId().isBlank())
                    || RegCodeAccessService.ROLE_REGCODE_CLIENT_ID.equals(user.getRoleId());
            if (isChild) {
                childUserIds.add(user.getId());
            }
        }
        boolean removed = this.regCodeUserService.removeByIds(idList);
        if (!childUserIds.isEmpty()) {
            this.sysUsersService.removeByIds(childUserIds);
        }
        return ApiResponse.success(removed);
    }

    private String validateForm(RegCodeUserForm form, boolean creating) {
        if (form.getGenerateLimit() == null || form.getGenerateLimit() < 1) {
            return "请设置可用生成次数（至少 1 次）";
        }
        if (form.getConfigIds() == null || form.getConfigIds().isEmpty()) {
            return "请至少分配一种注册码配置";
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
        if (form.getGenerateUsed() != null && form.getGenerateUsed() < 0) {
            return "已用次数不能为负数";
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
        vo.setGenerateLimit(entity.getGenerateLimit());
        vo.setGenerateUsed(entity.getGenerateUsed() == null ? 0 : entity.getGenerateUsed());
        int limit = entity.getGenerateLimit() == null ? 0 : entity.getGenerateLimit();
        vo.setRemaining(Math.max(limit - vo.getGenerateUsed(), 0));
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
                labels.add(config.getCompany() + " / " + config.getName());
            }
            vo.setConfigLabels(labels);
        } else {
            vo.setConfigLabels(Collections.emptyList());
        }
        return vo;
    }
}
