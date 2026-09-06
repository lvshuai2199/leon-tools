package springboot.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import springboot.domain.SysUsers;
import springboot.service.SysUsersService;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作人员：落库和展示都用用户名，历史记录里的用户 ID 会在查询时转成名称。
 */
@Component
public class OperatorUtils {

    public static final String UNKNOWN = "未知人员";
    private static final Pattern USER_ID = Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$|^[0-9a-f]{32}$");

    private static OperatorUtils instance;

    private final SysUsersService sysUsersService;

    public OperatorUtils(SysUsersService sysUsersService) {
        this.sysUsersService = sysUsersService;
        instance = this;
    }

    public static String resolve(String userIdOrName) {
        return instance == null ? fallback(userIdOrName) : instance.toDisplayName(userIdOrName);
    }

    public String toDisplayName(String userIdOrName) {
        if (userIdOrName == null || userIdOrName.isBlank()) {
            return UNKNOWN;
        }
        String value = userIdOrName.trim();
        if (!looksLikeUserId(value)) {
            return value;
        }
        SysUsers user = this.sysUsersService.getById(value);
        if (user == null) {
            return value;
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return value;
    }

    public List<String> findUserIdsByName(String keyword) {
        if (keyword == null || keyword.isBlank() || instance == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysUsers> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.like(SysUsers::getUsername, keyword.trim())
                .or()
                .like(SysUsers::getNickname, keyword.trim()));
        return this.sysUsersService.list(wrapper).stream()
                .map(SysUsers::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    public static boolean looksLikeUserId(String value) {
        return value != null && USER_ID.matcher(value.trim()).matches();
    }

    private static String fallback(String userIdOrName) {
        if (userIdOrName == null || userIdOrName.isBlank()) {
            return UNKNOWN;
        }
        return userIdOrName.trim();
    }
}
