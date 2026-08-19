package springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import springboot.domain.SysMenus;
import springboot.domain.SysRoleMenu;
import springboot.mapper.SysMenusMapper;
import springboot.mapper.SysRoleMenuMapper;
import springboot.service.SysGeneralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysGeneralServiceImpl implements SysGeneralService {

    @Resource
    private SysMenusMapper sysMenusMapper;

    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<SysMenus> getMenuListByRoleId(String roleId) {

        if (roleId == null) {
            return List.of(); // 如果 roleId 为空，返回空列表
        }

        // 创建 LambdaQueryWrapper 查询 SysRoleMenu 表
        LambdaQueryWrapper<SysRoleMenu> roleMenuQueryWrapper = new LambdaQueryWrapper<>();
        roleMenuQueryWrapper.eq(SysRoleMenu::getRoldId, roleId); // 确保使用正确的字段名

        // 查询角色对应的菜单 ID 列表
        List<SysRoleMenu> roleMenuList = sysRoleMenuMapper.selectList(roleMenuQueryWrapper);

        // 获取菜单 ID 列表
        List<String> menuIds = roleMenuList.stream()
                .map(SysRoleMenu::getMenuId) // 假设 SysRoleMenu 中有 getMenuId() 方法
                .collect(Collectors.toList());

        // 如果没有找到对应的菜单 ID，返回空列表
        if (menuIds.isEmpty()) {
            return List.of(); // 返回空列表
        }

        // 根据菜单 ID 列表查询 SysMenus 表
        LambdaQueryWrapper<SysMenus> menuQueryWrapper = new LambdaQueryWrapper<>();
        menuQueryWrapper.in(SysMenus::getId, menuIds); // 假设 SysMenus 中有 getId() 方法

        // 执行查询，返回菜单列表
        return sysMenusMapper.selectList(menuQueryWrapper);
    }
}
