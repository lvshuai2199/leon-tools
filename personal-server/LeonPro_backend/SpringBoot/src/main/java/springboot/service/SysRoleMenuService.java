package springboot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import springboot.domain.SysRoleMenu;

import java.util.List;

/**
 * @author 13326
 * @description 针对表【sys_role_menu】的数据库操作Service
 * @createDate 2025-04-15 17:13:27
 */
public interface SysRoleMenuService extends IService<SysRoleMenu> {

    /** 查询角色已分配的菜单（路由）ID 列表 */
    List<String> getMenuIdsByRole(String roleId);

    /** 分配角色可访问的菜单（路由）列表（先删后插） */
    void assignMenus(String roleId, List<String> menuIds);
}
