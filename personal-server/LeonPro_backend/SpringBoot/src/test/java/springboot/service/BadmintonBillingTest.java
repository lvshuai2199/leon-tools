package springboot.service;

import org.junit.jupiter.api.Test;
import springboot.domain.BadmintonBallFee;
import springboot.domain.BadmintonBill;
import springboot.domain.BadmintonCourtFee;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BadmintonBillingTest {

    @Test
    void courtAndBallSplitEvenly() {
        BadmintonBill bill = new BadmintonBill();
        bill.setParticipantCount(4);

        BadmintonCourtFee court = new BadmintonCourtFee();
        court.setCourtCount(2);
        court.setHours(new BigDecimal("2"));
        court.setUnitPrice(new BigDecimal("40"));
        bill.setCourtItems(List.of(court));

        BadmintonBallFee ball = new BadmintonBallFee();
        ball.setBrand("亚狮龙7号");
        ball.setQuantity(3);
        ball.setUnitPrice(new BigDecimal("70"));
        bill.setBallItems(List.of(ball));

        BadmintonBilling.apply(bill);

        assertEquals(new BigDecimal("160.00"), bill.getCourtTotal());
        assertEquals(new BigDecimal("210.00"), bill.getBallTotal());
        assertEquals(new BigDecimal("370.00"), bill.getGrandTotal());
        assertEquals(new BigDecimal("92.50"), bill.getPerPerson());
        assertEquals(new BigDecimal("160.00"), court.getAmount());
        assertEquals(new BigDecimal("210.00"), ball.getAmount());
    }

    @Test
    void multipleRowsAndHalfHour() {
        BadmintonBill bill = new BadmintonBill();
        bill.setParticipantCount(3);

        BadmintonCourtFee a = new BadmintonCourtFee();
        a.setCourtCount(1);
        a.setHours(new BigDecimal("2.5"));
        a.setUnitPrice(new BigDecimal("50"));
        BadmintonCourtFee b = new BadmintonCourtFee();
        b.setCourtCount(1);
        b.setHours(new BigDecimal("1"));
        b.setUnitPrice(new BigDecimal("30"));
        bill.setCourtItems(List.of(a, b));

        BadmintonBallFee ballA = new BadmintonBallFee();
        ballA.setQuantity(1);
        ballA.setUnitPrice(new BigDecimal("80"));
        BadmintonBallFee ballB = new BadmintonBallFee();
        ballB.setQuantity(2);
        ballB.setUnitPrice(new BigDecimal("12.5"));
        bill.setBallItems(List.of(ballA, ballB));

        BadmintonBilling.apply(bill);

        assertEquals(new BigDecimal("125.00"), a.getAmount());
        assertEquals(new BigDecimal("30.00"), b.getAmount());
        assertEquals(new BigDecimal("155.00"), bill.getCourtTotal());
        assertEquals(new BigDecimal("105.00"), bill.getBallTotal());
        assertEquals(new BigDecimal("260.00"), bill.getGrandTotal());
        assertEquals(new BigDecimal("86.67"), bill.getPerPerson());
    }

    @Test
    void missingPeopleDefaultsToOneAndNullsAreZero() {
        BadmintonBill bill = new BadmintonBill();
        bill.setParticipantCount(0);
        BadmintonCourtFee court = new BadmintonCourtFee();
        bill.setCourtItems(List.of(court));
        bill.setBallItems(null);

        BadmintonBilling.apply(bill);

        assertEquals(1, bill.getParticipantCount());
        assertEquals(new BigDecimal("0.00"), bill.getCourtTotal());
        assertEquals(new BigDecimal("0.00"), bill.getBallTotal());
        assertEquals(new BigDecimal("0.00"), bill.getGrandTotal());
        assertEquals(new BigDecimal("0.00"), bill.getPerPerson());
    }
}
