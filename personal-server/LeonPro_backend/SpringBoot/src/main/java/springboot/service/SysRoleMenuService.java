package springboot.service;

import com.baomidou.mybatisplus.spring.service.IService;
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

    /** 分配角色可访问的菜单（路由）列表，等同 assignMenus(roleId, menuIds, null) */
    void assignMenus(String roleId, List<String> menuIds);

    /**
     * 分配角色菜单：client 为 null 时替换全部授权，为 admin / app 时只替换该端授权；
     * 两种情况都保留指向已停用菜单的原有授权，并忽略不存在的菜单 id。
     */
    void assignMenus(String roleId, List<String> menuIds, String client);
}
