package springboot.service;

import springboot.domain.BadmintonBallFee;
import springboot.domain.BadmintonBill;
import springboot.domain.BadmintonCourtFee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 羽毛球计费：场地费、用球费、人均应付。纯计算，不访问数据库。
 */
public final class BadmintonBilling {

    public static final int MONEY_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
    /** 一桶羽毛球 12 个 */
    public static final BigDecimal BALLS_PER_BUCKET = BigDecimal.valueOf(12);

    private BadmintonBilling() {
    }

    public static void apply(BadmintonBill bill) {
        if (bill == null) {
            return;
        }
        List<BadmintonCourtFee> courts = bill.getCourtItems() == null
                ? new ArrayList<>() : bill.getCourtItems();
        List<BadmintonBallFee> balls = bill.getBallItems() == null
                ? new ArrayList<>() : bill.getBallItems();
        bill.setCourtItems(courts);
        bill.setBallItems(balls);

        BigDecimal courtTotal = ZERO;
        for (int i = 0; i < courts.size(); i++) {
            BadmintonCourtFee item = courts.get(i);
            if (item == null) {
                continue;
            }
            item.setSortOrder(i);
            item.setCourtCount(nonNegativeInt(item.getCourtCount()));
            item.setHours(nonNegativeMoney(item.getHours()));
            item.setUnitPrice(nonNegativeMoney(item.getUnitPrice()));
            item.setAmount(money(BigDecimal.valueOf(item.getCourtCount())
                    .multiply(item.getHours())
                    .multiply(item.getUnitPrice())));
            courtTotal = courtTotal.add(item.getAmount());
        }

        BigDecimal ballTotal = ZERO;
        for (int i = 0; i < balls.size(); i++) {
            BadmintonBallFee item = balls.get(i);
            if (item == null) {
                continue;
            }
            item.setSortOrder(i);
            item.setQuantity(nonNegativeInt(item.getQuantity()));
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            BigDecimal bucket = bucketOrNull(item.getBucketPrice());
            item.setBucketPrice(bucket);
            if (bucket != null) {
                // 单价只用于展示：round2(整桶/12)；小计用整桶价直接算，最后才取到分（100 元 × 3 个 = 25.00）
                item.setUnitPrice(bucket.divide(BALLS_PER_BUCKET, MONEY_SCALE, ROUNDING));
                item.setAmount(bucket.multiply(qty).divide(BALLS_PER_BUCKET, MONEY_SCALE, ROUNDING));
            } else {
                item.setUnitPrice(nonNegativeMoney(item.getUnitPrice()));
                item.setAmount(money(qty.multiply(item.getUnitPrice())));
            }
            ballTotal = ballTotal.add(item.getAmount());
        }

        int people = itemPeople(bill.getParticipantCount());
        bill.setParticipantCount(people);
        bill.setCourtTotal(money(courtTotal));
        bill.setBallTotal(money(ballTotal));
        BigDecimal grand = money(courtTotal.add(ballTotal));
        bill.setGrandTotal(grand);
        if (people <= 0) {
            bill.setPerPerson(ZERO);
        } else {
            bill.setPerPerson(grand.divide(BigDecimal.valueOf(people), MONEY_SCALE, ROUNDING));
        }
    }

    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal nonNegativeMoney(BigDecimal value) {
        BigDecimal n = money(value);
        return n.signum() < 0 ? ZERO : n;
    }

    /** 整桶价：空、0 或负数都当没填（返回 null）；校验在 BadmintonBillBizService 里先做 */
    public static BigDecimal bucketOrNull(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return null;
        }
        return money(value);
    }

    /** 单条用球小计（与 apply 里的口径一致），给测试和预览用 */
    public static BigDecimal ballAmount(BigDecimal bucketPrice, BigDecimal unitPrice, Integer quantity) {
        BigDecimal qty = BigDecimal.valueOf(nonNegativeInt(quantity));
        BigDecimal bucket = bucketOrNull(bucketPrice);
        if (bucket != null) {
            return bucket.multiply(qty).divide(BALLS_PER_BUCKET, MONEY_SCALE, ROUNDING);
        }
        return money(qty.multiply(nonNegativeMoney(unitPrice)));
    }

    public static int nonNegativeInt(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    public static int itemPeople(Integer value) {
        if (value == null || value < 1) {
            return 1;
        }
        return value;
    }
}
