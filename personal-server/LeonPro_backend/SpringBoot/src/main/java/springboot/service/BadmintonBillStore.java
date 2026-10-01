package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.domain.BadmintonBallFee;
import springboot.domain.BadmintonBill;
import springboot.domain.BadmintonCourtFee;
import springboot.utils.DateUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 羽毛球计费：主表 + 场地费/用球费明细，保存时重算金额。
 */
@Service
public class BadmintonBillStore {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_ITEMS = 50;
    private static final int MAX_PEOPLE = 999;
    private static final int MAX_COURTS = 100;
    private static final int MAX_QTY = 10000;

    private final BadmintonBillService billService;
    private final BadmintonCourtFeeService courtFeeService;
    private final BadmintonBallFeeService ballFeeService;

    public BadmintonBillStore(BadmintonBillService billService,
                              BadmintonCourtFeeService courtFeeService,
                              BadmintonBallFeeService ballFeeService) {
        this.billService = billService;
        this.courtFeeService = courtFeeService;
        this.ballFeeService = ballFeeService;
    }

    public Page<BadmintonBill> page(Page<BadmintonBill> page, String playDate,
                                    String playDateStart, String playDateEnd, String title) {
        LambdaQueryWrapper<BadmintonBill> wrapper = new LambdaQueryWrapper<>();
        applyDateFilter(wrapper, playDate, playDateStart, playDateEnd);
        if (notBlank(title)) {
            wrapper.like(BadmintonBill::getTitle, title.trim());
        }
        wrapper.orderByDesc(BadmintonBill::getPlayDate).orderByDesc(BadmintonBill::getUpdateTime);
        return billService.page(page, wrapper);
    }

    public BadmintonBill getWithItems(String id) {
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

    @Transactional
    public BadmintonBill save(BadmintonBill body, String operatorId, String operatorName) {
        if (body == null) {
            throw new IllegalArgumentException("请填写计费信息");
        }
        Date now = DateUtils.getNow();
        boolean creating = body.getId() == null || body.getId().isBlank();
        BadmintonBill entity;
        if (creating) {
            entity = new BadmintonBill();
            entity.setId(newId());
            entity.setCreateTime(now);
            entity.setOperatorId(operatorId);
            entity.setOperatorName(operatorName);
        } else {
            entity = billService.getById(body.getId().trim());
            if (entity == null) {
                throw new IllegalArgumentException("记录不存在");
            }
        }
        entity.setPlayDate(normalizeDate(body.getPlayDate()));
        entity.setTitle(trimTo(body.getTitle(), 100));
        entity.setRemark(trimTo(body.getRemark(), 500));
        entity.setParticipantCount(clampPeople(body.getParticipantCount()));
        entity.setCourtItems(sanitizeCourts(body.getCourtItems()));
        entity.setBallItems(sanitizeBalls(body.getBallItems()));
        entity.setUpdateTime(now);
        BadmintonBilling.apply(entity);

        boolean ok = creating ? billService.save(entity) : billService.updateById(entity);
        if (!ok) {
            throw new IllegalStateException("保存失败");
        }
        replaceItems(entity);
        fillItems(entity);
        return entity;
    }

    @Transactional
    public boolean deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        List<String> clean = new ArrayList<>();
        for (String id : ids) {
            if (notBlank(id)) {
                clean.add(id.trim());
            }
        }
        if (clean.isEmpty()) {
            return false;
        }
        courtFeeService.remove(new LambdaQueryWrapper<BadmintonCourtFee>().in(BadmintonCourtFee::getBillId, clean));
        ballFeeService.remove(new LambdaQueryWrapper<BadmintonBallFee>().in(BadmintonBallFee::getBillId, clean));
        return billService.removeByIds(clean);
    }

    public BadmintonBill preview(BadmintonBill body) {
        BadmintonBill bill = new BadmintonBill();
        bill.setPlayDate(normalizeDate(body == null ? null : body.getPlayDate()));
        bill.setTitle(body == null ? null : trimTo(body.getTitle(), 100));
        bill.setRemark(body == null ? null : trimTo(body.getRemark(), 500));
        bill.setParticipantCount(clampPeople(body == null ? null : body.getParticipantCount()));
        bill.setCourtItems(sanitizeCourts(body == null ? null : body.getCourtItems()));
        bill.setBallItems(sanitizeBalls(body == null ? null : body.getBallItems()));
        BadmintonBilling.apply(bill);
        return bill;
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
