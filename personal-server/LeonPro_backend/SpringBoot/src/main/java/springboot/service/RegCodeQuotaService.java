package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.DTO.RegCodeSubUser;
import springboot.domain.ComRegistration;
import springboot.domain.RegCodeConfig;
import springboot.domain.RegCodeUser;
import springboot.domain.RegCodeUserConfig;
import springboot.domain.SysUsers;
import springboot.utils.BizException;
import springboot.utils.DateUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 注册码按配置次数的所有写操作：生成扣次数、给子用户分配 / 收回、停用退回、管理员调整、客户次数保存。
 * <p>
 * 每个方法都是一个数据库事务；次数变化一律用条件更新
 * （如 {@code UPDATE ... SET generate_limit = generate_limit - ? WHERE id = ? AND generate_limit - generate_used >= ?}），
 * 影响 0 行即次数不足并抛 {@link BizException} 让整个事务回滚；涉及创建人的操作先 {@code SELECT ... FOR UPDATE}
 * 锁住创建人的 reg_code_user 行，把同一创建人的并发分配 / 新建子用户串行化。
 */
@Service
public class RegCodeQuotaService {

    public static final int MIN_PASSWORD_LENGTH = 6;
    private static final String PASSWORD_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final SysUsersService sysUsersService;
    private final RegCodeUserService regCodeUserService;
    private final RegCodeUserConfigService regCodeUserConfigService;
    private final RegCodeConfigService regCodeConfigService;
    private final ComRegistrationService comRegistrationService;
    private final RegCodeAccessService regCodeAccessService;

    public RegCodeQuotaService(JdbcTemplate jdbc,
                               SysUsersService sysUsersService,
                               RegCodeUserService regCodeUserService,
                               RegCodeUserConfigService regCodeUserConfigService,
                               RegCodeConfigService regCodeConfigService,
                               ComRegistrationService comRegistrationService,
                               RegCodeAccessService regCodeAccessService) {
        this.jdbc = jdbc;
        this.sysUsersService = sysUsersService;
        this.regCodeUserService = regCodeUserService;
        this.regCodeUserConfigService = regCodeUserConfigService;
        this.regCodeConfigService = regCodeConfigService;
        this.comRegistrationService = comRegistrationService;
        this.regCodeAccessService = regCodeAccessService;
    }

    // ------------------------------------------------------------------ 生成

    /**
     * 扣 1 次并保存生成记录，同一事务：扣次数失败（已用完 / 并发抢光）则不保存记录。ROOT 不扣次数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeAndRecord(SysUsers operator, String configId, ComRegistration record) {
        if (configId != null && !regCodeAccessService.isRootUser(operator)) {
            List<String> ids = jdbc.queryForList(
                    "SELECT id FROM reg_code_user_config WHERE user_id = ? AND config_id = ? "
                            + "AND generate_used < generate_limit ORDER BY id LIMIT 1",
                    String.class, operator.getId(), configId);
            int updated = ids.isEmpty() ? 0 : jdbc.update(
                    "UPDATE reg_code_user_config SET generate_used = generate_used + 1 "
                            + "WHERE id = ? AND generate_used < generate_limit",
                    ids.get(0));
            if (updated == 0) {
                throw new BizException("该配置的生成次数已用完");
            }
        }
        comRegistrationService.save(record);
    }

    // ------------------------------------------------------------------ 客户（注册码页）管理子用户

    /**
     * 客户新建子用户：角色 role_regcode_client、parent_id = 创建人；按 quotas 从创建人剩余次数里划拨（立即扣减）。
     * 启用中的子用户数达到 max_sub_users、任一配置超过创建人剩余，整个操作回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUsers createSubUser(SysUsers creator, RegCodeSubUser.CreateForm form) {
        if (form == null) {
            throw new BizException("请填写子用户信息");
        }
        String username = form.getUsername() == null ? "" : form.getUsername().trim();
        if (username.length() < 3 || username.length() > 20) {
            throw new BizException("用户名长度须为 3-20 个字符");
        }
        if (form.getPassword() == null || form.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new BizException("密码长度不能少于6位");
        }
        int max = lockCreator(creator.getId());
        if (regCodeAccessService.enabledSubUserCount(creator.getId()) >= max) {
            throw new BizException(max <= 0 ? "当前账号不能创建子用户，请联系管理员" : "子用户数量已达上限（" + max + " 个）");
        }
        if (sysUsersService.count(new LambdaQueryWrapper<SysUsers>().eq(SysUsers::getUsername, username)) > 0) {
            throw new BizException("用户名已存在");
        }
        Date now = DateUtils.getNow();
        SysUsers user = new SysUsers();
        user.setUsername(username);
        user.setNickname(form.getNickname() == null ? null : form.getNickname().trim());
        // 与登录校验、后台用户管理一致的存储方式（现有系统未做密码哈希）
        user.setPassword(form.getPassword());
        user.setRoleId(RegCodeAccessService.ROLE_REGCODE_CLIENT_ID);
        user.setParentId(creator.getId());
        user.setCreateTime(now);
        sysUsersService.save(user);

        RegCodeUser account = new RegCodeUser();
        account.setUserId(user.getId());
        account.setGenerateLimit(0);
        account.setGenerateUsed(0);
        account.setMaxSubUsers(0);
        account.setStatus(RegCodeAccessService.STATUS_ENABLED);
        account.setRemark("注册码页创建的子用户");
        account.setCreateTime(now);
        account.setUpdateTime(now);
        regCodeUserService.save(account);

        for (Map.Entry<String, Integer> e : merge(form.getQuotas()).entrySet()) {
            if (e.getValue() < 0) {
                throw new BizException("分配次数不能为负数");
            }
            if (e.getValue() > 0) {
                moveFromCreator(creator.getId(), user.getId(), e.getKey(), e.getValue());
            }
        }
        return user;
    }

    /** 客户调整子用户次数：delta 正数从创建人剩余里追加，负数收回子用户未用的次数（已用的不能收回） */
    @Transactional(rollbackFor = Exception.class)
    public void adjustByCreator(SysUsers creator, SysUsers subUser, List<RegCodeSubUser.DeltaItem> items) {
        lockCreator(creator.getId());
        for (Map.Entry<String, Integer> e : mergeDeltas(items).entrySet()) {
            int delta = e.getValue();
            if (delta > 0) {
                moveFromCreator(creator.getId(), subUser.getId(), e.getKey(), delta);
            } else if (delta < 0) {
                takeBack(subUser.getId(), e.getKey(), -delta);
                addToExisting(creator.getId(), e.getKey(), -delta, "你已没有该配置，不能收回");
            }
        }
    }

    /**
     * 停用 / 启用子用户。停用时把子用户各配置未用的次数退回创建人（创建人已没有该配置的不退，留在子用户上）；
     * 重新启用不会自动分配，次数保持停用后的状态（通常为 0）。
     */
    @Transactional(rollbackFor = Exception.class)
    public RegCodeSubUser.StatusResult setStatus(SysUsers creator, SysUsers subUser, int status) {
        if (status != RegCodeAccessService.STATUS_ENABLED && status != RegCodeAccessService.STATUS_DISABLED) {
            throw new BizException("状态只能是 0（停用）或 1（启用）");
        }
        int max = lockCreator(creator.getId());
        RegCodeSubUser.StatusResult result = new RegCodeSubUser.StatusResult();
        result.setStatus(status);
        if (status == RegCodeAccessService.STATUS_ENABLED
                && currentStatus(subUser.getId()) == RegCodeAccessService.STATUS_DISABLED
                && regCodeAccessService.enabledSubUserCount(creator.getId()) >= max) {
            throw new BizException("启用中的子用户数量已达上限（" + max + " 个）");
        }
        int changed = jdbc.update("UPDATE reg_code_user SET status = ?, update_time = ? WHERE user_id = ? AND status <> ?",
                status, DateUtils.getNow(), subUser.getId(), status);
        if (changed == 0) {
            if (regCodeAccessService.getAssignment(subUser.getId()) == null) {
                throw new BizException("该子用户没有注册码账号");
            }
            return result;
        }
        if (status == RegCodeAccessService.STATUS_DISABLED) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, config_id, generate_limit - generate_used AS remaining FROM reg_code_user_config "
                            + "WHERE user_id = ? AND generate_limit > generate_used FOR UPDATE",
                    subUser.getId());
            Map<String, Integer> refunded = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String configId = String.valueOf(row.get("config_id"));
                int remaining = ((Number) row.get("remaining")).intValue();
                String creatorRowId = rowId(creator.getId(), configId);
                if (creatorRowId == null || remaining <= 0) {
                    continue;
                }
                int moved = jdbc.update("UPDATE reg_code_user_config SET generate_limit = generate_limit - ? "
                        + "WHERE id = ? AND generate_limit - generate_used >= ?", remaining, row.get("id"), remaining);
                if (moved == 0) {
                    throw new BizException("次数已变化，请刷新后重试");
                }
                jdbc.update("UPDATE reg_code_user_config SET generate_limit = generate_limit + ? WHERE id = ?",
                        remaining, creatorRowId);
                refunded.merge(configId, remaining, Integer::sum);
            }
            refunded.forEach((k, v) -> result.getRefunded().add(new RegCodeSubUser.RefundItem(k, v)));
            result.setRefundedTotal(refunded.values().stream().mapToInt(Integer::intValue).sum());
        }
        return result;
    }

    /** 重置子用户密码：服务端随机生成 10 位新密码，保存后只在本次返回 */
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(SysUsers subUser) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        String password = sb.toString();
        SysUsers patch = new SysUsers();
        patch.setId(subUser.getId());
        // 与登录校验一致的存储方式（现有系统未做密码哈希）
        patch.setPassword(password);
        if (!sysUsersService.updateById(patch)) {
            throw new BizException("重置密码失败");
        }
        return password;
    }

    // ------------------------------------------------------------------ 管理员

    /**
     * 管理员调整子用户次数（与客户接口同样的 delta 格式）：正数由管理员直接增加，不从创建人扣；
     * 负数收回子用户未用的次数，直接作废，不退回创建人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustByAdmin(SysUsers subUser, List<RegCodeSubUser.DeltaItem> items) {
        for (Map.Entry<String, Integer> e : mergeDeltas(items).entrySet()) {
            int delta = e.getValue();
            if (delta > 0) {
                requireConfig(e.getKey());
                addOrCreate(subUser.getId(), e.getKey(), delta);
            } else if (delta < 0) {
                takeBack(subUser.getId(), e.getKey(), -delta);
            }
        }
    }

    /**
     * 管理员保存客户的按配置次数（/admin/regCodeUser/save、update）：列表里的配置设为给定上限
     * （不能低于该配置已用），不在列表里的配置删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceCustomerQuotas(String userId, List<RegCodeSubUser.QuotaCount> quotas) {
        Map<String, Integer> wanted = merge(quotas);
        List<RegCodeUserConfig> existing = regCodeUserConfigService.list(
                new LambdaQueryWrapper<RegCodeUserConfig>().eq(RegCodeUserConfig::getUserId, userId));
        Map<String, RegCodeUserConfig> byConfig = new LinkedHashMap<>();
        List<String> toDelete = new ArrayList<>();
        for (RegCodeUserConfig row : existing) {
            if (row.getConfigId() == null || !wanted.containsKey(row.getConfigId()) || byConfig.containsKey(row.getConfigId())) {
                toDelete.add(row.getId());
            } else {
                byConfig.put(row.getConfigId(), row);
            }
        }
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            int limit = e.getValue();
            if (limit < 0) {
                throw new BizException("次数不能为负数");
            }
            RegCodeUserConfig row = byConfig.get(e.getKey());
            if (row == null) {
                requireConfig(e.getKey());
                insertRow(userId, e.getKey(), limit);
                continue;
            }
            int updated = jdbc.update("UPDATE reg_code_user_config SET generate_limit = ? WHERE id = ? AND generate_used <= ?",
                    limit, row.getId(), limit);
            if (updated == 0) {
                throw new BizException("「" + configName(e.getKey()) + "」的次数不能低于已用的 "
                        + RegCodeAccessService.usedOf(row) + " 次");
            }
        }
        if (!toDelete.isEmpty()) {
            regCodeUserConfigService.removeByIds(toDelete);
        }
    }

    /** 删除某账号全部次数行 */
    @Transactional(rollbackFor = Exception.class)
    public void removeAll(String userId) {
        regCodeUserConfigService.remove(new LambdaQueryWrapper<RegCodeUserConfig>().eq(RegCodeUserConfig::getUserId, userId));
    }

    // ------------------------------------------------------------------ 查询（客户接口和管理员接口共用同一返回结构）

    /** 某账号名下的子用户列表（parent_id = owner），带各自已用 / 已分配合计，以及启用数与上限 */
    public RegCodeSubUser.SubUserList subUserList(SysUsers owner) {
        RegCodeSubUser.SubUserList list = new RegCodeSubUser.SubUserList();
        List<SysUsers> children = sysUsersService.list(new LambdaQueryWrapper<SysUsers>()
                .eq(SysUsers::getParentId, owner.getId())
                .orderByDesc(SysUsers::getCreateTime));
        Map<String, RegCodeUser> accounts = new LinkedHashMap<>();
        Map<String, int[]> totals = new LinkedHashMap<>();
        List<String> ids = children == null ? List.of()
                : children.stream().map(SysUsers::getId).filter(Objects::nonNull).toList();
        if (!ids.isEmpty()) {
            regCodeUserService.list(new LambdaQueryWrapper<RegCodeUser>().in(RegCodeUser::getUserId, ids))
                    .forEach(a -> accounts.putIfAbsent(a.getUserId(), a));
            regCodeUserConfigService.list(new LambdaQueryWrapper<RegCodeUserConfig>().in(RegCodeUserConfig::getUserId, ids))
                    .forEach(r -> {
                        int[] t = totals.computeIfAbsent(r.getUserId(), k -> new int[2]);
                        t[0] += RegCodeAccessService.usedOf(r);
                        t[1] += RegCodeAccessService.limitOf(r);
                    });
        }
        int enabled = 0;
        for (SysUsers c : children == null ? List.<SysUsers>of() : children) {
            RegCodeSubUser.SubUserItem item = new RegCodeSubUser.SubUserItem();
            item.setId(c.getId());
            item.setUsername(c.getUsername());
            item.setNickname(c.getNickname());
            RegCodeUser a = accounts.get(c.getId());
            int status = a == null || a.getStatus() == null ? RegCodeAccessService.STATUS_ENABLED : a.getStatus();
            item.setStatus(status);
            item.setCreateTime(c.getCreateTime());
            int[] t = totals.getOrDefault(c.getId(), new int[2]);
            item.setUsedTotal(t[0]);
            item.setAllocatedTotal(t[1]);
            if (status != RegCodeAccessService.STATUS_DISABLED) {
                enabled++;
            }
            list.getItems().add(item);
        }
        list.setCreatedCount(enabled);
        list.setMaxSubUsers(regCodeAccessService.maxSubUsersOf(owner));
        list.setCanCreate(regCodeAccessService.canManageSubUsers(owner) && enabled < list.getMaxSubUsers());
        return list;
    }

    /** 子用户各配置次数 + 可退回合计 + 创建人各配置剩余（创建人为 null 时不返回） */
    public RegCodeSubUser.SubUserQuota subUserQuota(SysUsers subUser, SysUsers creator) {
        RegCodeSubUser.SubUserQuota q = new RegCodeSubUser.SubUserQuota();
        q.setSubUserId(subUser.getId());
        q.setItems(regCodeAccessService.quotaItems(subUser.getId()));
        q.setRefundableTotal(q.getItems().stream().mapToInt(RegCodeSubUser.QuotaItem::getRemaining).sum());
        if (creator != null) {
            q.setCreatorRemaining(regCodeAccessService.quotaItems(creator.getId()));
        }
        return q;
    }

    // ------------------------------------------------------------------ 内部

    /** 锁住创建人的 reg_code_user 行（没有记录视为不能分配），返回其 max_sub_users */
    private int lockCreator(String creatorId) {
        List<Integer> max = jdbc.queryForList(
                "SELECT max_sub_users FROM reg_code_user WHERE user_id = ? FOR UPDATE", Integer.class, creatorId);
        if (max.isEmpty()) {
            throw new BizException("当前账号没有注册码额度，请联系管理员");
        }
        Integer v = max.get(0);
        return v == null ? 0 : v;
    }

    private int currentStatus(String userId) {
        List<Integer> s = jdbc.queryForList("SELECT status FROM reg_code_user WHERE user_id = ?", Integer.class, userId);
        return s.isEmpty() || s.get(0) == null ? RegCodeAccessService.STATUS_ENABLED : s.get(0);
    }

    /** 从创建人剩余里划 n 次给子用户（创建人上限减 n，子用户上限加 n） */
    private void moveFromCreator(String creatorId, String subUserId, String configId, int n) {
        String creatorRowId = rowId(creatorId, configId);
        if (creatorRowId == null) {
            throw new BizException("你没有「" + configName(configId) + "」的次数，不能分配");
        }
        int updated = jdbc.update("UPDATE reg_code_user_config SET generate_limit = generate_limit - ? "
                + "WHERE id = ? AND generate_limit - generate_used >= ?", n, creatorRowId, n);
        if (updated == 0) {
            throw new BizException("「" + configName(configId) + "」剩余次数不足");
        }
        addOrCreate(subUserId, configId, n);
    }

    /** 收回某账号某配置未用的 n 次（已用的不能收回） */
    private void takeBack(String userId, String configId, int n) {
        String id = rowId(userId, configId);
        int updated = id == null ? 0 : jdbc.update("UPDATE reg_code_user_config SET generate_limit = generate_limit - ? "
                + "WHERE id = ? AND generate_limit - generate_used >= ?", n, id, n);
        if (updated == 0) {
            throw new BizException("「" + configName(configId) + "」可收回的次数不足（已用的不能收回）");
        }
    }

    private void addToExisting(String userId, String configId, int n, String missingMessage) {
        String id = rowId(userId, configId);
        if (id == null) {
            throw new BizException(missingMessage);
        }
        jdbc.update("UPDATE reg_code_user_config SET generate_limit = generate_limit + ? WHERE id = ?", n, id);
    }

    private void addOrCreate(String userId, String configId, int n) {
        String id = rowId(userId, configId);
        if (id == null) {
            insertRow(userId, configId, n);
        } else {
            jdbc.update("UPDATE reg_code_user_config SET generate_limit = generate_limit + ? WHERE id = ?", n, id);
        }
    }

    private void insertRow(String userId, String configId, int limit) {
        jdbc.update("INSERT INTO reg_code_user_config (id, user_id, config_id, generate_limit, generate_used) VALUES (?, ?, ?, ?, 0)",
                UUID.randomUUID().toString().replace("-", ""), userId, configId, limit);
    }

    /** 某账号某配置的次数行 id（同一配置有多行时取第一行，并加行锁） */
    private String rowId(String userId, String configId) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM reg_code_user_config WHERE user_id = ? AND config_id = ? ORDER BY id LIMIT 1 FOR UPDATE",
                String.class, userId, configId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void requireConfig(String configId) {
        if (regCodeConfigService.getById(configId) == null) {
            throw new BizException("注册码配置不存在：" + configId);
        }
    }

    private String configName(String configId) {
        RegCodeConfig c = configId == null ? null : regCodeConfigService.getById(configId);
        String label = RegCodeAccessService.configLabel(c);
        return label == null || label.isBlank() ? String.valueOf(configId) : label;
    }

    private static Map<String, Integer> merge(List<RegCodeSubUser.QuotaCount> quotas) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (quotas != null) {
            for (RegCodeSubUser.QuotaCount q : quotas) {
                if (q == null || q.getConfigId() == null || q.getConfigId().isBlank()) {
                    continue;
                }
                map.merge(q.getConfigId().trim(), q.getCount() == null ? 0 : q.getCount(), Integer::sum);
            }
        }
        return map;
    }

    private static Map<String, Integer> mergeDeltas(List<RegCodeSubUser.DeltaItem> items) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (items != null) {
            for (RegCodeSubUser.DeltaItem d : items) {
                if (d == null || d.getConfigId() == null || d.getConfigId().isBlank() || d.getDelta() == null) {
                    continue;
                }
                map.merge(d.getConfigId().trim(), d.getDelta(), Integer::sum);
            }
        }
        if (map.isEmpty()) {
            throw new BizException("没有要调整的次数");
        }
        return map;
    }
}
