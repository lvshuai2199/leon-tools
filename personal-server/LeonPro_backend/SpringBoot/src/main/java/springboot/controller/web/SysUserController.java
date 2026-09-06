package springboot.controller.web;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.DTO.UsersDelDto;
import springboot.DTO.UserDto;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * (User)表控制层
 *
 * @author makejava
 * @since 2023-05-14 10:40:35
 */
@RestController
@RequestMapping("sysUsers")

public class SysUserController {
    /**
     * 服务对象
     */
    @Autowired
    private SysUsersService sysUsersService;

    @Autowired
    private SysRolesService sysRolesService;


    /**
     * 分页查询所有数据
     *
     * @param page 分页对象
     * @param sysUsers 查询实体
     * @return 所有数据
     */
//    @GetMapping("getUsers")
//    public Page<SysUsers> selectAll(Page<SysUsers> page, SysUsers user) {
//        return this.sysUsersService.page(page, new QueryWrapper<>(user));
//    }
    @GetMapping("getUsers")
    public ApiResponse selectAll(Page<SysUsers> page, SysUsers sysUsers) {

        // 创建 QueryWrapper 实例
        LambdaQueryWrapper<SysUsers> queryWrapper = new LambdaQueryWrapper<>();

        if (sysUsers.getId() != null) {
            queryWrapper.eq(SysUsers::getId, sysUsers.getId());
        }
        // 添加条件
        if (sysUsers.getUsername() != null) {
            queryWrapper.like(SysUsers::getUsername, sysUsers.getUsername());
        }
        // 用户管理只展示主用户：排除子用户和注册码客户角色
        queryWrapper.and(w -> w.isNull(SysUsers::getParentId).or().eq(SysUsers::getParentId, ""));
        queryWrapper.and(w -> w.isNull(SysUsers::getRoleId).or().ne(SysUsers::getRoleId, "role_regcode_client"));

        // 执行分页查询
        return ApiResponse.success(this.sysUsersService.page(page, queryWrapper));
    }

    @PostMapping("userSaveOrUpdate")
    @Validated
    public ApiResponse sysUserRegister(@RequestBody UserDto userDto) {
        // 创建或更新用户
        SysUsers sysUsers = new SysUsers();
        sysUsers.setUsername(userDto.getUsername());
        sysUsers.setEmail(userDto.getEmail());
        sysUsers.setNickname(userDto.getNickname());
        sysUsers.setRoleId(userDto.getRoleId());

        // 如果存在 ID，则更新用户
        if (userDto.getId() != null) {
            sysUsers.setId(userDto.getId());
            // 编辑时若填写了新密码则一并更新，留空表示不修改密码
            if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
                sysUsers.setPassword(userDto.getPassword());
            }
            boolean updated = this.sysUsersService.updateById(sysUsers);
            return ApiResponse.success(updated ? "User updated successfully." : "User update failed.");
        }

        // 新用户注册逻辑
        if (userDto.getPassword() == null || userDto.getPassword().isEmpty()) {
            return ApiResponse.failure("Password cannot be empty.");
        }

//        // 加密密码（需要使用合适的加密库来加密密码）
//        String encryptedPassword = encryptPassword(userDto.getPassword());
//        sysUsers.setPassword(encryptedPassword);

        sysUsers.setPassword(userDto.getPassword());

        boolean saved = this.sysUsersService.save(sysUsers);
        return ApiResponse.success(saved ? "User registered successfully." : "User registration failed.");
    }

    @GetMapping("getAllUsers")
    public ApiResponse getAllUsers(
            @RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize,
            SysUsers sysUsers // 假设 sysUsers 包含查询条件
    ) {
        // 创建分页对象
        Page<SysUsers> page = new Page<>(currentPage, pageSize);

        // 创建 QueryWrapper 实例
        LambdaQueryWrapper<SysUsers> queryWrapper = new LambdaQueryWrapper<>();

        // 添加条件
        if (sysUsers.getUsername() != null) {
            queryWrapper.eq(SysUsers::getUsername, sysUsers.getUsername());
        }
        // 其他条件可以继续添加，例如:
        // if (sysUsers.getEmail() != null) {
        //     queryWrapper.eq("email", sysUsers.getEmail());
        // }

        // 执行分页查询
        IPage<SysUsers> userPage = this.sysUsersService.page(page, queryWrapper);

        return ApiResponse.success(userPage);
    }

    @PostMapping("delUsers")
    public ApiResponse delUsers(@RequestBody UsersDelDto request) {
        List<String> userIds = request.getUserIds();

        if (userIds == null || userIds.isEmpty()) {
            return ApiResponse.failure("User ID list cannot be empty");
        }

        // 执行批量删除
        boolean result = sysUsersService.removeByIds(userIds);

        if (result) {
            return ApiResponse.success("Users deleted successfully");
        } else {
            return ApiResponse.failure("Failed to delete users");
        }
    }

    @GetMapping("getMyInfo")
    public ApiResponse getMyInfo(@RequestParam(required = false) String username) {
        String name = (username == null || username.isEmpty()) ? "admin" : username;

        // 通过用户名检索数据库中是否存在对应的数据项
        LambdaQueryWrapper<SysUsers> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(SysUsers::getUsername, name);
        SysUsers user = this.sysUsersService.getOne(lambdaQueryWrapper);
        if (user != null && user.getRoleId() != null && !user.getRoleId().isEmpty()) {
            SysRoles role = this.sysRolesService.getById(user.getRoleId());
            if (role != null) {
                user.setRoleName(role.getRoleName());
            }
        }
        return ApiResponse.success(user);
    }

}

