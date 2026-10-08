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

    /**
     * 管理端保存兼容：管理端页面不认识整桶价，提交的用球明细不带 bucketPrice（也不带明细 id，明细每次保存都重建）。
     * 对每条没带整桶价的明细，按位置找原来的同一条：品牌相同、原来有整桶价、且提交的单价 = round2(原整桶价/12)
     * （即管理端没改过单价）→ 沿用原整桶价，小计照整桶算，金额不变。单价被改过或品牌对不上 → 按手填单价算。
     * 只用于管理端路径；用户端「改为手填」会显式清空整桶价，不能走这里。
     */
    public static void inheritBucketPrices(List<BadmintonBallFee> incoming, List<BadmintonBallFee> stored) {
        if (incoming == null || stored == null || stored.isEmpty()) {
            return;
        }
        for (int i = 0; i < incoming.size() && i < stored.size(); i++) {
            BadmintonBallFee in = incoming.get(i);
            BadmintonBallFee old = stored.get(i);
            if (in == null || old == null || bucketOrNull(in.getBucketPrice()) != null) {
                continue;
            }
            BigDecimal oldBucket = bucketOrNull(old.getBucketPrice());
            if (oldBucket == null || in.getUnitPrice() == null) {
                continue;
            }
            if (!sameBrand(in.getBrand(), old.getBrand())) {
                continue;
            }
            BigDecimal expectedUnit = oldBucket.divide(BALLS_PER_BUCKET, MONEY_SCALE, ROUNDING);
            if (money(in.getUnitPrice()).compareTo(expectedUnit) == 0) {
                in.setBucketPrice(oldBucket);
            }
        }
    }

    private static boolean sameBrand(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return x.equals(y);
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
