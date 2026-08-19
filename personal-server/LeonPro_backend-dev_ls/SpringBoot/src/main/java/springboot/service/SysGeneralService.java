package springboot.service;

import springboot.domain.SysMenus;

import java.util.List;

public interface SysGeneralService {

    List<SysMenus> getMenuListByRoleId(String roleId);
}
