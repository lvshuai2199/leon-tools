package springboot.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import springboot.domain.SysUsers;
import springboot.utils.ForbiddenException;

import java.util.List;

/** 用户端笔记接口：ROOT，或角色里勾了用户端「笔记」菜单的账号。 */
@Service
public class NoteAccess {

    public static final String APP_ROUTE = "/notes";

    private final RegCodeAccessService access;
    private final JdbcTemplate jdbc;

    public NoteAccess(RegCodeAccessService access, JdbcTemplate jdbc) {
        this.access = access;
        this.jdbc = jdbc;
    }

    public SysUsers requireApp(HttpServletRequest request) {
        SysUsers user = access.currentUser(request);
        if (user == null) {
            throw new ForbiddenException("请先登录");
        }
        if (access.isRootUser(user)) {
            return user;
        }
        SysUsers governing = access.appMenuGoverningUser(user);
        if (governing == null) {
            throw new ForbiddenException("无笔记权限");
        }
        if (access.isRootUser(governing)) {
            return user;
        }
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM sys_menus WHERE client = 'app' AND route_key = ? AND (disabled IS NULL OR disabled = 0)",
                String.class, APP_ROUTE);
        if (ids.isEmpty() || !access.hasMenu(governing, ids.get(0))) {
            throw new ForbiddenException("无笔记权限");
        }
        return user;
    }
}
