package springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.domain.SysRoleMenu;
import springboot.mapper.SysRoleMenuMapper;
import springboot.service.SysRoleMenuService;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author 13326
* @description 针对表【sys_role_menu】的数据库操作Service实现
* @createDate 2025-04-15 17:13:27
*/
@Service
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu>
    implements SysRoleMenuService {

    @Override
    public List<String> getMenuIdsByRole(String roleId) {
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoldId, roleId);
        return this.list(wrapper).stream()
            .map(SysRoleMenu::getMenuId)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(String roleId, List<String> menuIds) {
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoldId, roleId);
        this.remove(wrapper);
        if (menuIds != null && !menuIds.isEmpty()) {
            List<SysRoleMenu> relations = menuIds.stream()
                .map(menuId -> {
                    SysRoleMenu rm = new SysRoleMenu();
                    rm.setRoldId(roleId);
                    rm.setMenuId(menuId);
                    return rm;
                })
                .collect(Collectors.toList());
            this.saveBatch(relations);
        }
    }
}




