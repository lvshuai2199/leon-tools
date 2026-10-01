package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import springboot.DTO.CrabShipmentBatchRequest;
import springboot.domain.CrabShipment;
import springboot.domain.SysUsers;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.ForbiddenException;
import springboot.utils.PhoneMaskUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 螃蟹出货业务逻辑，管理端 / 用户端 / 公开分享三个入口共用。
 * <p>
 * 数据范围 scope：null 表示不限（管理端、ROOT）；否则只能看 / 改 operatorId 在集合里的记录，
 * 范围外的记录一律 403（批量删除只要有一条越界整批 403）。新建记录都记在当前用户名下。
 */
@Service
public class CrabShipmentBizService {

    /** 分享页地址（用户端 history 路由），前端拼上域名即可 */
    public static final String SHARE_PATH_PREFIX = "/s/crab/";

    private static final Pattern PUBLIC_ID = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CrabShipmentService crabShipmentService;
    private final SysUsersService sysUsersService;
    private final RegCodeAccessService regCodeAccessService;

    public CrabShipmentBizService(CrabShipmentService crabShipmentService,
                                  SysUsersService sysUsersService,
                                  RegCodeAccessService regCodeAccessService) {
        this.crabShipmentService = crabShipmentService;
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

    public Page<CrabShipment> page(Page<CrabShipment> page, CrabShipment query, String shipDateStart,
                                   String shipDateEnd, Collection<String> scope) {
        LambdaQueryWrapper<CrabShipment> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(CrabShipment::getOperatorId, scope);
        }
        applyShipDateFilter(wrapper, query == null ? null : query.getShipDate(), shipDateStart, shipDateEnd);
        if (query != null) {
            if (notBlank(query.getCustomerName())) {
                wrapper.like(CrabShipment::getCustomerName, query.getCustomerName().trim());
            }
            if (notBlank(query.getPhone())) {
                wrapper.like(CrabShipment::getPhone, query.getPhone().trim());
            }
            if (query.getPaid() != null) {
                wrapper.eq(CrabShipment::getPaid, query.getPaid());
            }
            if (query.getShipped() != null) {
                wrapper.eq(CrabShipment::getShipped, query.getShipped());
            }
            if (notBlank(query.getTrackingNo())) {
                wrapper.like(CrabShipment::getTrackingNo, query.getTrackingNo().trim());
            }
        }
        wrapper.orderByDesc(CrabShipment::getShipDate).orderByAsc(CrabShipment::getSeqNo)
                .orderByDesc(CrabShipment::getCreateTime);
        Page<CrabShipment> result = this.crabShipmentService.page(page, wrapper);
        if (result.getRecords() != null) {
            result.getRecords().forEach(this::fillShare);
        }
        return result;
    }

    public ApiResponse detail(String id, Collection<String> scope) {
        CrabShipment entity = id == null ? null : this.crabShipmentService.getById(id.trim());
        if (entity == null) {
            return ApiResponse.failure("记录不存在");
        }
        assertInScope(entity, scope);
        fillShare(entity);
        return ApiResponse.success(entity);
    }

    public ApiResponse save(CrabShipment body, SysUsers operator, Collection<String> scope) {
        if (body == null) {
            return ApiResponse.failure("请填写出货信息");
        }
        String name = trimToEmpty(body.getCustomerName());
        if (name.isEmpty()) {
            return ApiResponse.failure("请填写姓名");
        }
        Date now = DateUtils.getNow();
        boolean creating = body.getId() == null || body.getId().isBlank();
        CrabShipment entity;
        if (creating) {
            entity = new CrabShipment();
            entity.setId(newId());
            entity.setPublicId(newId());
            entity.setCreateTime(now);
            applyOperator(entity, operator);
        } else {
            entity = this.crabShipmentService.getById(body.getId().trim());
            if (entity == null) {
                return ApiResponse.failure("记录不存在");
            }
            assertInScope(entity, scope);
        }
        String shipDate = normalizeDate(body.getShipDate());
        entity.setCustomerName(name);
        entity.setPhone(trimToNull(body.getPhone()));
        entity.setAddress(trimToNull(body.getAddress()));
        entity.setSpec(trimToNull(body.getSpec()));
        entity.setQuantity(body.getQuantity());
        entity.setPaid(body.getPaid() != null && body.getPaid() != 0 ? 1 : 0);
        entity.setShipped(body.getShipped() != null && body.getShipped() != 0 ? 1 : 0);
        entity.setTrackingNo(trimToNull(body.getTrackingNo()));
        entity.setRemark(trimToNull(body.getRemark()));
        entity.setShipDate(shipDate);
        entity.setUpdateTime(now);
        if (body.getSeqNo() != null) {
            entity.setSeqNo(body.getSeqNo());
        } else if (creating || entity.getSeqNo() == null) {
            entity.setSeqNo(nextSeq(shipDate));
        }
        boolean ok = creating ? this.crabShipmentService.save(entity) : this.crabShipmentService.updateById(entity);
        if (!ok) {
            return ApiResponse.failure("保存失败");
        }
        fillShare(entity);
        return ApiResponse.success(entity);
    }

    public ApiResponse updateStatus(CrabShipment body, Collection<String> scope) {
        if (body == null || body.getId() == null || body.getId().isBlank()) {
            return ApiResponse.failure("缺少记录");
        }
        CrabShipment entity = this.crabShipmentService.getById(body.getId().trim());
        if (entity == null) {
            return ApiResponse.failure("记录不存在");
        }
        assertInScope(entity, scope);
        LambdaUpdateWrapper<CrabShipment> uw = new LambdaUpdateWrapper<>();
        uw.eq(CrabShipment::getId, entity.getId());
        if (body.getPaid() != null) {
            uw.set(CrabShipment::getPaid, body.getPaid() != 0 ? 1 : 0);
        }
        if (body.getShipped() != null) {
            uw.set(CrabShipment::getShipped, body.getShipped() != 0 ? 1 : 0);
        }
        if (body.getTrackingNo() != null) {
            uw.set(CrabShipment::getTrackingNo, trimToNull(body.getTrackingNo()));
        }
        uw.set(CrabShipment::getUpdateTime, DateUtils.getNow());
        this.crabShipmentService.update(uw);
        CrabShipment latest = this.crabShipmentService.getById(entity.getId());
        fillShare(latest);
        return ApiResponse.success(latest);
    }

    public ApiResponse batchSave(CrabShipmentBatchRequest req, SysUsers operator) {
        if (req == null || req.getRecords() == null || req.getRecords().isEmpty()) {
            return ApiResponse.failure("没有可保存的记录");
        }
        String shipDate = normalizeDate(req.getShipDate());
        int seq = nextSeq(shipDate);
        Date now = DateUtils.getNow();
        List<CrabShipment> saved = new ArrayList<>();
        for (CrabShipment item : req.getRecords()) {
            if (item == null || isBlank(item.getCustomerName()) && isBlank(item.getPhone())) {
                continue;
            }
            CrabShipment entity = new CrabShipment();
            entity.setId(newId());
            entity.setPublicId(newId());
            entity.setSeqNo(item.getSeqNo() != null ? item.getSeqNo() : seq++);
            entity.setCustomerName(trimToNull(item.getCustomerName()));
            entity.setPhone(trimToNull(item.getPhone()));
            entity.setAddress(trimToNull(item.getAddress()));
            entity.setSpec(trimToNull(item.getSpec()));
            entity.setQuantity(item.getQuantity());
            entity.setPaid(item.getPaid() != null && item.getPaid() != 0 ? 1 : 0);
            entity.setShipped(item.getShipped() != null && item.getShipped() != 0 ? 1 : 0);
            entity.setTrackingNo(trimToNull(item.getTrackingNo()));
            entity.setRemark(trimToNull(item.getRemark()));
            entity.setShipDate(notBlank(item.getShipDate()) ? normalizeDate(item.getShipDate()) : shipDate);
            applyOperator(entity, operator);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            this.crabShipmentService.save(entity);
            fillShare(entity);
            saved.add(entity);
            if (item.getSeqNo() != null && item.getSeqNo() >= seq) {
                seq = item.getSeqNo() + 1;
            }
        }
        if (saved.isEmpty()) {
            return ApiResponse.failure("没有有效记录");
        }
        return ApiResponse.success(saved);
    }

    public ApiResponse parse(String text) {
        List<CrabShipment> rows = CrabOrderParser.parse(text);
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("count", rows.size());
        return ApiResponse.success(data);
    }

    public ApiResponse delete(List<String> idList, Collection<String> scope) {
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        if (scope != null) {
            List<CrabShipment> targets = this.crabShipmentService.listByIds(idList);
            if (targets != null) {
                for (CrabShipment t : targets) {
                    assertInScope(t, scope);
                }
            }
        }
        return ApiResponse.success(this.crabShipmentService.removeByIds(idList));
    }

    /** 公开分享：无需登录，手机号和地址里的号码脱敏 */
    public ApiResponse publicView(String publicId) {
        if (publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return ApiResponse.failure("链接无效");
        }
        CrabShipment entity = this.crabShipmentService.getOne(
                new LambdaQueryWrapper<CrabShipment>().eq(CrabShipment::getPublicId, publicId), false);
        if (entity == null) {
            return ApiResponse.failure("记录不存在或链接已失效");
        }
        Map<String, Object> view = new HashMap<>();
        view.put("publicId", entity.getPublicId());
        view.put("seqNo", entity.getSeqNo());
        view.put("customerName", entity.getCustomerName());
        view.put("phone", PhoneMaskUtils.maskPhone(entity.getPhone()));
        view.put("address", PhoneMaskUtils.maskAddress(entity.getAddress()));
        view.put("spec", entity.getSpec());
        view.put("quantity", entity.getQuantity());
        view.put("paid", entity.getPaid() != null && entity.getPaid() != 0 ? 1 : 0);
        view.put("shipped", entity.getShipped() != null && entity.getShipped() != 0 ? 1 : 0);
        view.put("trackingNo", entity.getTrackingNo());
        view.put("shipDate", entity.getShipDate());
        view.put("updateTime", entity.getUpdateTime());
        return ApiResponse.success(view);
    }

    static void assertInScope(CrabShipment entity, Collection<String> scope) {
        if (scope == null) {
            return;
        }
        String owner = entity.getOperatorId();
        if (owner == null || owner.isBlank() || !scope.contains(owner)) {
            throw new ForbiddenException("无权操作这条出货记录");
        }
    }

    private void fillShare(CrabShipment entity) {
        if (entity != null && entity.getPublicId() != null) {
            entity.setSharePath(SHARE_PATH_PREFIX + entity.getPublicId());
        }
    }

    private void applyOperator(CrabShipment entity, SysUsers operator) {
        if (operator == null) {
            return;
        }
        entity.setOperatorId(operator.getId());
        entity.setOperatorName(notBlank(operator.getNickname()) ? operator.getNickname() : operator.getUsername());
    }

    private int nextSeq(String shipDate) {
        CrabShipment last = this.crabShipmentService.getOne(
                new LambdaQueryWrapper<CrabShipment>()
                        .eq(CrabShipment::getShipDate, shipDate)
                        .orderByDesc(CrabShipment::getSeqNo)
                        .last("LIMIT 1"),
                false);
        if (last == null || last.getSeqNo() == null) {
            return 1;
        }
        return last.getSeqNo() + 1;
    }

    private static void applyShipDateFilter(LambdaQueryWrapper<CrabShipment> wrapper,
                                           String shipDate,
                                           String shipDateStart,
                                           String shipDateEnd) {
        String from = optionalDate(shipDateStart);
        String to = optionalDate(shipDateEnd);
        if (from != null || to != null) {
            if (from != null && to != null && from.compareTo(to) > 0) {
                String swap = from;
                from = to;
                to = swap;
            }
            if (from != null) {
                wrapper.ge(CrabShipment::getShipDate, from);
            }
            if (to != null) {
                wrapper.le(CrabShipment::getShipDate, to);
            }
            return;
        }
        String day = optionalDate(shipDate);
        if (day != null) {
            wrapper.eq(CrabShipment::getShipDate, day);
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
        if (raw != null) {
            String t = raw.trim();
            if (t.length() >= 10) {
                t = t.substring(0, 10);
            }
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return t;
            }
        }
        return LocalDate.now(ZONE).format(DAY);
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
