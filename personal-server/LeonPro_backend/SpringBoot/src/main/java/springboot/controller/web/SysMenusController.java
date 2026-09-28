package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.domain.SysMenus;
import springboot.service.SysMenusService;
import springboot.service.menu.MenuClients;
import springboot.utils.ApiResponse;

import java.util.List;

/**
 * 路由配置（/admin/sysMenus，仅 ROOT）。
 * 由页面清单 menus.json 管理的菜单（managed=1）每次启动都会被清单覆盖，所以这里不允许修改或删除，
 * 想改名称、排序、组件就改清单；手工菜单（managed=0）照旧可以增删改。
 * client / route_key / managed / disabled 四个字段只由清单同步维护，接口传了也会被忽略（新增时 client 可选 admin/app）。
 */
@RestController
@RequestMapping("/admin/sysMenus")
public class SysMenusController {

    static final String MANAGED_MESSAGE = "该菜单由页面清单（menus.json）管理，请修改清单后重新发布";

    @Autowired
    private SysMenusService sysMenusService;

    /**
     * 全部菜单（按排序升序），含 client、routeKey、managed、disabled 字段；可选 client=admin/app 只看一端。
     */
    @GetMapping("list")
    public ApiResponse list(@RequestParam(value = "client", required = false) String client) {
        LambdaQueryWrapper<SysMenus> queryWrapper = new LambdaQueryWrapper<>();
        if (client != null && !client.isBlank()) {
            if (!MenuClients.isValid(client.trim())) {
                return ApiResponse.failure("client 只能是 admin 或 app");
            }
            queryWrapper.eq(SysMenus::getClient, client.trim());
        }
        queryWrapper.orderByAsc(SysMenus::getSortOrder);
        return ApiResponse.success(this.sysMenusService.list(queryWrapper));
    }

    /** 新增手工菜单（managed=0）；client 不传默认 admin */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody SysMenus sysMenus) {
        String client = sysMenus.getClient() == null || sysMenus.getClient().isBlank()
                ? MenuClients.ADMIN : sysMenus.getClient().trim();
        if (!MenuClients.isValid(client)) {
            return ApiResponse.failure("client 只能是 admin 或 app");
        }
        sysMenus.setClient(client);
        // route_key 由下次启动的清单同步按路径回填
        sysMenus.setRouteKey(null);
        sysMenus.setManaged(0);
        sysMenus.setDisabled(0);
        return ApiResponse.success(this.sysMenusService.save(sysMenus));
    }

    /** 修改手工菜单；清单管理的菜单拒绝修改 */
    @PostMapping("update")
    public ApiResponse update(@RequestBody SysMenus sysMenus) {
        SysMenus existing = sysMenus.getId() == null ? null : this.sysMenusService.getById(sysMenus.getId());
        if (existing == null) {
            return ApiResponse.failure("菜单不存在");
        }
        if (Integer.valueOf(1).equals(existing.getManaged())) {
            return ApiResponse.failure(MANAGED_MESSAGE);
        }
        sysMenus.setClient(null);
        sysMenus.setRouteKey(null);
        sysMenus.setManaged(null);
        sysMenus.setDisabled(null);
        boolean ok = this.sysMenusService.updateById(sysMenus);
        if (ok) {
            // 路径可能变了：清空 route_key，下次启动按新路径重新回填
            this.sysMenusService.update(new LambdaUpdateWrapper<SysMenus>()
                    .set(SysMenus::getRouteKey, null)
                    .eq(SysMenus::getId, existing.getId())
                    .eq(SysMenus::getManaged, 0));
        }
        return ApiResponse.success(ok);
    }

    /** 删除手工菜单；列表里只要有清单管理的菜单就整体拒绝 */
    @PostMapping("del")
    public ApiResponse delete(@RequestBody List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的菜单");
        }
        List<String> managed = this.sysMenusService.listByIds(idList).stream()
                .filter(m -> Integer.valueOf(1).equals(m.getManaged()))
                .map(m -> m.getMenuName() == null ? m.getId() : m.getMenuName())
                .toList();
        if (!managed.isEmpty()) {
            return ApiResponse.failure(MANAGED_MESSAGE + "：" + String.join("、", managed));
        }
        return ApiResponse.success(this.sysMenusService.removeByIds(idList));
    }
}
