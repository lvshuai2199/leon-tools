package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.domain.BadmintonBallFee;
import springboot.domain.BadmintonBill;
import springboot.domain.BadmintonCourtFee;
import springboot.domain.SysUsers;
import springboot.utils.ApiResponse;
import springboot.utils.BizException;
import springboot.utils.DateUtils;
import springboot.utils.ForbiddenException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 羽毛球计费业务：管理端 / 用户端共用。保存时重算场地费、用球费、人均应付。
 * <p>
 * 数据范围 scope：null 表示不限（管理端、ROOT）；否则只能看 / 改 operatorId 在集合里的记录。
 */
@Service
public class BadmintonBillBizService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_ITEMS = 50;
    private static final int MAX_PEOPLE = 999;
    private static final int MAX_COURTS = 100;
    private static final int MAX_QTY = 10000;
    private static final BigDecimal MAX_BUCKET_PRICE = new BigDecimal("100000");
    static final String BUCKET_PRICE_INVALID = "请输入大于 0 的价格";

    private final BadmintonBillService billService;
    private final BadmintonCourtFeeService courtFeeService;
    private final BadmintonBallFeeService ballFeeService;
    private final SysUsersService sysUsersService;
    private final RegCodeAccessService regCodeAccessService;

    public BadmintonBillBizService(BadmintonBillService billService,
                                   BadmintonCourtFeeService courtFeeService,
                                   BadmintonBallFeeService ballFeeService,
                                   SysUsersService sysUsersService,
                                   RegCodeAccessService regCodeAccessService) {
        this.billService = billService;
        this.courtFeeService = courtFeeService;
        this.ballFeeService = ballFeeService;
        this.sysUsersService = sysUsersService;
        this.regCodeAccessService = regCodeAccessService;
    }

    /**
     * 用户端数据范围（按 operator_id）：ROOT 不限（返回 null）；子账号只有自己；
     * 主账号 = 自己 + parent_id 是自己的子账号。
     */
    public Set<String> scopeOf(SysUsers user) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new ForbiddenException();
        }
        if (this.regCodeAccessService.isRootUser(user)) {
            return null;
        }
        Set<String> ids = new LinkedHashSet<>();
        ids.add(user.getId());
        if (!this.regCodeAccessService.isSubAccount(user)) {
            List<SysUsers> children = this.sysUsersService.list(
                    new LambdaQueryWrapper<SysUsers>().eq(SysUsers::getParentId, user.getId()));
            if (children != null) {
                children.stream()
                        .map(SysUsers::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .forEach(ids::add);
            }
        }
        return ids;
    }

    public Page<BadmintonBill> page(Page<BadmintonBill> page, String playDate,
                                    String playDateStart, String playDateEnd, String title,
                                    Collection<String> scope) {
        LambdaQueryWrapper<BadmintonBill> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(BadmintonBill::getOperatorId, scope);
        }
        applyDateFilter(wrapper, playDate, playDateStart, playDateEnd);
        if (notBlank(title)) {
            wrapper.like(BadmintonBill::getTitle, title.trim());
        }
        wrapper.orderByDesc(BadmintonBill::getPlayDate).orderByDesc(BadmintonBill::getUpdateTime);
        return billService.page(page, wrapper);
    }

    public ApiResponse detail(String id, Collection<String> scope) {
        BadmintonBill entity = getWithItems(id);
        if (entity == null) {
            return ApiResponse.failure("记录不存在");
        }
        assertInScope(entity, scope);
        return ApiResponse.success(entity);
    }

    @Transactional
    public ApiResponse save(BadmintonBill body, SysUsers operator, Collection<String> scope) {
        return save(body, operator, scope, false);
    }

    /**
     * @param inheritBucketPrice 管理端保存传 true：管理端页面不认识整桶价，按
     *                           {@link BadmintonBilling#inheritBucketPrices} 沿用原明细的整桶价，避免编辑后整桶价丢失、金额变化。
     */
    @Transactional
    public ApiResponse save(BadmintonBill body, SysUsers operator, Collection<String> scope, boolean inheritBucketPrice) {
        if (body == null) {
            return ApiResponse.failure("请填写计费信息");
        }
        Date now = DateUtils.getNow();
        boolean creating = body.getId() == null || body.getId().isBlank();
        BadmintonBill entity;
        if (creating) {
            entity = new BadmintonBill();
            entity.setId(newId());
            entity.setCreateTime(now);
            applyOperator(entity, operator);
        } else {
            entity = billService.getById(body.getId().trim());
            if (entity == null) {
                return ApiResponse.failure("记录不存在");
            }
            assertInScope(entity, scope);
        }
        entity.setPlayDate(normalizeDate(body.getPlayDate()));
        entity.setTitle(resolveName(body.getTitle(), entity.getPlayDate(), entity.getOperatorId(), entity.getId()));
        entity.setRemark(trimTo(body.getRemark(), 500));
        entity.setParticipantCount(clampPeople(body.getParticipantCount()));
        entity.setCourtItems(sanitizeCourts(body.getCourtItems()));
        entity.setBallItems(sanitizeBalls(body.getBallItems()));
        if (inheritBucketPrice && !creating) {
            List<BadmintonBallFee> stored = ballFeeService.list(
                    new LambdaQueryWrapper<BadmintonBallFee>()
                            .eq(BadmintonBallFee::getBillId, entity.getId())
                            .orderByAsc(BadmintonBallFee::getSortOrder)
                            .orderByAsc(BadmintonBallFee::getId));
            BadmintonBilling.inheritBucketPrices(entity.getBallItems(), stored);
        }
        entity.setUpdateTime(now);
        BadmintonBilling.apply(entity);

        boolean ok = creating ? billService.save(entity) : billService.updateById(entity);
        if (!ok) {
            return ApiResponse.failure("保存失败");
        }
        replaceItems(entity);
        fillItems(entity);
        return ApiResponse.success(entity);
    }

    @Transactional
    public ApiResponse delete(List<String> idList, Collection<String> scope) {
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        List<String> clean = new ArrayList<>();
        for (String id : idList) {
            if (notBlank(id)) {
                clean.add(id.trim());
            }
        }
        if (clean.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        if (scope != null) {
            List<BadmintonBill> targets = billService.listByIds(clean);
            if (targets != null) {
                for (BadmintonBill t : targets) {
                    assertInScope(t, scope);
                }
            }
        }
        courtFeeService.remove(new LambdaQueryWrapper<BadmintonCourtFee>().in(BadmintonCourtFee::getBillId, clean));
        ballFeeService.remove(new LambdaQueryWrapper<BadmintonBallFee>().in(BadmintonBallFee::getBillId, clean));
        return ApiResponse.success(billService.removeByIds(clean));
    }

    public ApiResponse preview(BadmintonBill body) {
        BadmintonBill bill = new BadmintonBill();
        bill.setPlayDate(normalizeDate(body == null ? null : body.getPlayDate()));
        bill.setTitle(body == null ? null : trimTo(body.getTitle(), 100));
        bill.setRemark(body == null ? null : trimTo(body.getRemark(), 500));
        bill.setParticipantCount(clampPeople(body == null ? null : body.getParticipantCount()));
        bill.setCourtItems(sanitizeCourts(body == null ? null : body.getCourtItems()));
        bill.setBallItems(sanitizeBalls(body == null ? null : body.getBallItems()));
        BadmintonBilling.apply(bill);
        return ApiResponse.success(bill);
    }

    /**
     * 名称预览：与 save 用同一套规则（含同一天重名加序号，正在编辑的这条不算）。
     * body 只看 id / title / playDate。编辑别人的记录（不在数据范围内）按无权处理。
     */
    public ApiResponse namePreview(BadmintonBill body, SysUsers operator, Collection<String> scope) {
        String playDate = normalizeDate(body == null ? null : body.getPlayDate());
        String title = body == null ? null : body.getTitle();
        String id = body == null || body.getId() == null ? null : body.getId().trim();
        String owner = operator == null ? null : operator.getId();
        if (id != null && !id.isEmpty()) {
            BadmintonBill existing = billService.getById(id);
            if (existing != null) {
                assertInScope(existing, scope);
                owner = existing.getOperatorId();
            } else {
                id = null;
            }
        }
        String name = resolveName(title, playDate, owner, id);
        return ApiResponse.success(Map.of("name", name, "playDate", playDate));
    }

    /** 最终名称：规则见 {@link BadmintonNames}；重名范围 = 同一 operator_id + 同一 play_date，排除 selfId */
    private String resolveName(String rawTitle, String playDate, String ownerId, String selfId) {
        LambdaQueryWrapper<BadmintonBill> wrapper = new LambdaQueryWrapper<BadmintonBill>()
                .select(BadmintonBill::getId, BadmintonBill::getTitle)
                .eq(BadmintonBill::getPlayDate, playDate);
        if (ownerId == null || ownerId.isBlank()) {
            wrapper.isNull(BadmintonBill::getOperatorId);
        } else {
            wrapper.eq(BadmintonBill::getOperatorId, ownerId);
        }
        if (selfId != null && !selfId.isBlank()) {
            wrapper.ne(BadmintonBill::getId, selfId);
        }
        List<BadmintonBill> sameDay = billService.list(wrapper);
        Set<String> names = new LinkedHashSet<>();
        if (sameDay != null) {
            sameDay.stream().map(BadmintonBill::getTitle).filter(Objects::nonNull).map(String::trim).forEach(names::add);
        }
        return BadmintonNames.normalize(rawTitle, playDate, names);
    }

    /**
     * 整桶价校验：空或 0 = 没填（返回 null）；负数不能保存；最多两位小数；上限 10 万。
     */
    static BigDecimal validateBucketPrice(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return null;
        }
        if (value.signum() < 0) {
            throw new BizException(BUCKET_PRICE_INVALID);
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BizException("整桶价格最多两位小数");
        }
        if (value.compareTo(MAX_BUCKET_PRICE) > 0) {
            throw new BizException("整桶价格不能超过 100000");
        }
        return value;
    }

    private BadmintonBill getWithItems(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        BadmintonBill bill = billService.getById(id.trim());
        if (bill == null) {
            return null;
        }
        fillItems(bill);
        return bill;
    }

    static void assertInScope(BadmintonBill entity, Collection<String> scope) {
        if (scope == null) {
            return;
        }
        String owner = entity.getOperatorId();
        if (owner == null || owner.isBlank() || !scope.contains(owner)) {
            throw new ForbiddenException("无权操作这条计费记录");
        }
    }

    private void applyOperator(BadmintonBill entity, SysUsers operator) {
        if (operator == null) {
            return;
        }
        entity.setOperatorId(operator.getId());
        entity.setOperatorName(notBlank(operator.getNickname()) ? operator.getNickname() : operator.getUsername());
    }

    private void fillItems(BadmintonBill bill) {
        List<BadmintonCourtFee> courts = courtFeeService.list(
                new LambdaQueryWrapper<BadmintonCourtFee>()
                        .eq(BadmintonCourtFee::getBillId, bill.getId())
                        .orderByAsc(BadmintonCourtFee::getSortOrder)
                        .orderByAsc(BadmintonCourtFee::getId));
        List<BadmintonBallFee> balls = ballFeeService.list(
                new LambdaQueryWrapper<BadmintonBallFee>()
                        .eq(BadmintonBallFee::getBillId, bill.getId())
                        .orderByAsc(BadmintonBallFee::getSortOrder)
                        .orderByAsc(BadmintonBallFee::getId));
        bill.setCourtItems(courts);
        bill.setBallItems(balls);
    }

    private void replaceItems(BadmintonBill bill) {
        courtFeeService.remove(new LambdaQueryWrapper<BadmintonCourtFee>()
                .eq(BadmintonCourtFee::getBillId, bill.getId()));
        ballFeeService.remove(new LambdaQueryWrapper<BadmintonBallFee>()
                .eq(BadmintonBallFee::getBillId, bill.getId()));
        List<BadmintonCourtFee> courts = bill.getCourtItems() == null ? List.of() : bill.getCourtItems();
        for (BadmintonCourtFee item : courts) {
            item.setId(newId());
            item.setBillId(bill.getId());
            courtFeeService.save(item);
        }
        List<BadmintonBallFee> balls = bill.getBallItems() == null ? List.of() : bill.getBallItems();
        for (BadmintonBallFee item : balls) {
            item.setId(newId());
            item.setBillId(bill.getId());
            ballFeeService.save(item);
        }
    }

    private List<BadmintonCourtFee> sanitizeCourts(List<BadmintonCourtFee> raw) {
        List<BadmintonCourtFee> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        int n = Math.min(raw.size(), MAX_ITEMS);
        for (int i = 0; i < n; i++) {
            BadmintonCourtFee src = raw.get(i);
            if (src == null) {
                continue;
            }
            BadmintonCourtFee item = new BadmintonCourtFee();
            item.setCourtCount(clampInt(src.getCourtCount(), MAX_COURTS));
            item.setHours(src.getHours());
            item.setUnitPrice(src.getUnitPrice());
            item.setRemark(trimTo(src.getRemark(), 100));
            out.add(item);
        }
        return out;
    }

    private List<BadmintonBallFee> sanitizeBalls(List<BadmintonBallFee> raw) {
        List<BadmintonBallFee> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        int n = Math.min(raw.size(), MAX_ITEMS);
        for (int i = 0; i < n; i++) {
            BadmintonBallFee src = raw.get(i);
            if (src == null) {
                continue;
            }
            BadmintonBallFee item = new BadmintonBallFee();
            item.setBrand(trimTo(src.getBrand(), 50));
            item.setQuantity(clampInt(src.getQuantity(), MAX_QTY));
            item.setUnitPrice(src.getUnitPrice());
            item.setBucketPrice(validateBucketPrice(src.getBucketPrice()));
            out.add(item);
        }
        return out;
    }

    private static void applyDateFilter(LambdaQueryWrapper<BadmintonBill> wrapper,
                                        String playDate, String playDateStart, String playDateEnd) {
        String from = optionalDate(playDateStart);
        String to = optionalDate(playDateEnd);
        if (from != null || to != null) {
            if (from != null && to != null && from.compareTo(to) > 0) {
                String swap = from;
                from = to;
                to = swap;
            }
            if (from != null) {
                wrapper.ge(BadmintonBill::getPlayDate, from);
            }
            if (to != null) {
                wrapper.le(BadmintonBill::getPlayDate, to);
            }
            return;
        }
        String day = optionalDate(playDate);
        if (day != null) {
            wrapper.eq(BadmintonBill::getPlayDate, day);
        }
    }

    private static String optionalDate(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.length() >= 10) {
            t = t.substring(0, 10);
        }
        return t.matches("\\d{4}-\\d{2}-\\d{2}") ? t : null;
    }

    private static String normalizeDate(String raw) {
        String day = optionalDate(raw);
        return day != null ? day : LocalDate.now(ZONE).format(DAY);
    }

    private static int clampPeople(Integer value) {
        int n = BadmintonBilling.itemPeople(value);
        return Math.min(n, MAX_PEOPLE);
    }

    private static int clampInt(Integer value, int max) {
        int n = BadmintonBilling.nonNegativeInt(value);
        return Math.min(n, max);
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
