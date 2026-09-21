package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.DTO.CrabShipmentBatchRequest;
import springboot.DTO.CrabShipmentParseRequest;
import springboot.domain.CrabShipment;
import springboot.domain.SysUsers;
import springboot.service.CrabOrderParser;
import springboot.service.CrabShipmentService;
import springboot.service.RegCodeAccessService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;
import springboot.utils.RequestUserUtils;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 螃蟹每日出货：录入、改状态、单条公开分享。
 */
@RestController
public class CrabShipmentController {

    private static final Pattern PUBLIC_ID = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CrabShipmentService crabShipmentService;
    private final SysUsersService sysUsersService;
    private final RegCodeAccessService regCodeAccessService;

    public CrabShipmentController(CrabShipmentService crabShipmentService,
                                  SysUsersService sysUsersService,
                                  RegCodeAccessService regCodeAccessService) {
        this.crabShipmentService = crabShipmentService;
        this.sysUsersService = sysUsersService;
        this.regCodeAccessService = regCodeAccessService;
    }

    @GetMapping("crabShipment/getAll")
    public ApiResponse selectAll(Page<CrabShipment> page,
                                 CrabShipment query,
                                 @RequestParam(value = "shipDateStart", required = false) String shipDateStart,
                                 @RequestParam(value = "shipDateEnd", required = false) String shipDateEnd,
                                 HttpServletRequest request) {
        ApiResponse deny = denyUnlessCrab(request);
        if (deny != null) {
            return deny;
        }
        LambdaQueryWrapper<CrabShipment> wrapper = new LambdaQueryWrapper<>();
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
        return ApiResponse.success(result);
    }

    @GetMapping("crabShipment/{id}")
    public ApiResponse selectOne(@PathVariable Serializable id, HttpServletRequest request) {
        ApiResponse deny = denyUnlessCrab(request);
        if (deny != null) {
            return deny;
        }
        CrabShipment entity = this.crabShipmentService.getById(id);
        if (entity == null) {
            return ApiResponse.failure("记录不存在");
        }
        fillShare(entity);
        return ApiResponse.success(entity);
    }

    @PostMapping("crabShipment/save")
    public ApiResponse save(@RequestBody CrabShipment body, HttpServletRequest request) {
        ApiResponse deny = denyUnlessCrab(request);
        if (deny != null) {
            return deny;
        }
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
            applyOperator(entity, request);
        } else {
            entity = this.crabShipmentService.getById(body.getId().trim());
            if (entity == null) {
                return ApiResponse.failure("记录不存在");
            }
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

    @PostMapping("crabShipment/status")
    public ApiResponse updateStatus(@RequestBody CrabShipment body, HttpServletRequest request) {
        ApiResponse deny = denyUnlessCrab(request);
        if (deny != null) {
            return deny;
        }
        if (body == null || body.getId() == null || body.getId().isBlank()) {
            return ApiResponse.failure("缺少记录");
        }
        CrabShipment entity = this.crabShipmentService.getById(body.getId().trim());
        if (entity == null) {
            return ApiResponse.failure("记录不存在");
        }
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

    @PostMapping("crabShipment/batchSave")
    public ApiResponse batchSave(@RequestBody CrabShipmentBatchRequest req, HttpServletRequest request) {
        ApiResponse deny = denyUnlessCrab(request);
        if (deny != null) {
            return deny;
        }
        if (req == null || req.getRecords() == null || req.getRecords().isEmpty()) {
            return ApiResponse.failure("没有可保存的记录");
        }
        String shipDate = normalizeDate(req.getShipDate());
        int seq = nextSeq(shipDate);
        Date now = DateUtils.getNow();
        Operator operator = resolveOperator(request);
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
            entity.setOperatorId(operator.id);
            entity.setOperatorName(operator.name);
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

    @PostMapping("crabShipment/parse")
    public ApiResponse parse(@RequestBody CrabShipmentParseRequest req, HttpServletRequest request) {
        ApiResponse deny = denyUnlessCrab(request);
        if (deny != null) {
            return deny;
        }
        String text = req == null ? null : req.getText();
        List<CrabShipment> rows = CrabOrderParser.parse(text);
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("count", rows.size());
        return ApiResponse.success(data);
    }

    @PostMapping("crabShipment/del")
    public ApiResponse delete(@RequestBody List<String> idList, HttpServletRequest request) {
        ApiResponse deny = denyUnlessCrab(request);
        if (deny != null) {
            return deny;
        }
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        return ApiResponse.success(this.crabShipmentService.removeByIds(idList));
    }

    /** 单条公开状态：无需登录。 */
    @GetMapping("/public/crabShipment/{publicId}")
    public ApiResponse publicView(@PathVariable String publicId) {
        if (publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return ApiResponse.failure("链接无效");
        }
        CrabShipment entity = this.crabShipmentService.getOne(
                new LambdaQueryWrapper<CrabShipment>().eq(CrabShipment::getPublicId, publicId));
        if (entity == null) {
            return ApiResponse.failure("记录不存在或链接已失效");
        }
        Map<String, Object> view = new HashMap<>();
        view.put("publicId", entity.getPublicId());
        view.put("seqNo", entity.getSeqNo());
        view.put("customerName", entity.getCustomerName());
        view.put("phone", maskPhone(entity.getPhone()));
        view.put("address", entity.getAddress());
        view.put("spec", entity.getSpec());
        view.put("quantity", entity.getQuantity());
        view.put("paid", entity.getPaid() != null && entity.getPaid() != 0 ? 1 : 0);
        view.put("shipped", entity.getShipped() != null && entity.getShipped() != 0 ? 1 : 0);
        view.put("trackingNo", entity.getTrackingNo());
        view.put("shipDate", entity.getShipDate());
        view.put("updateTime", entity.getUpdateTime());
        return ApiResponse.success(view);
    }

    private ApiResponse denyUnlessCrab(HttpServletRequest request) {
        String err = this.regCodeAccessService.requireCrab(RequestUserUtils.currentUserId(request));
        return err == null ? null : ApiResponse.failure(err);
    }

    private void fillShare(CrabShipment entity) {
        if (entity != null && entity.getPublicId() != null) {
            entity.setSharePath("/h5/#/pages/crab/share?id=" + entity.getPublicId());
        }
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

    private void applyOperator(CrabShipment entity, HttpServletRequest request) {
        Operator operator = resolveOperator(request);
        entity.setOperatorId(operator.id);
        entity.setOperatorName(operator.name);
    }

    private Operator resolveOperator(HttpServletRequest request) {
        Operator operator = new Operator();
        String userId = RequestUserUtils.currentUserId(request);
        if (userId != null && !userId.isBlank()) {
            SysUsers user = this.sysUsersService.getById(userId);
            if (user != null) {
                operator.id = user.getId();
                operator.name = notBlank(user.getNickname()) ? user.getNickname() : user.getUsername();
                return operator;
            }
            operator.id = userId;
        }
        return operator;
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

    private static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String p = phone.trim();
        if (p.length() == 11) {
            return p.substring(0, 3) + "****" + p.substring(7);
        }
        return p;
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

    private static final class Operator {
        private String id;
        private String name;
    }
}
