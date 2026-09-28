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

    /**
     * 分配角色菜单：client 必填（admin / app），只替换该端授权，另一端的授权保留；
     * 保留指向已停用菜单的原有授权，并忽略不存在的菜单 id。client 为空或无效时抛 IllegalArgumentException，不改任何数据。
     */
    void assignMenus(String roleId, List<String> menuIds, String client);
}
