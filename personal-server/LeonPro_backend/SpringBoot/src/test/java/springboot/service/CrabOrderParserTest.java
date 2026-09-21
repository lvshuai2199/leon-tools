package springboot.service;

import org.junit.jupiter.api.Test;
import springboot.domain.CrabShipment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CrabOrderParserTest {

    @Test
    void parseTabTableWithHeader() {
        String text = ""
                + "序号\t姓名\t电话\t地址\t规格\t数量\n"
                + "1\t王大姐\t13800138000\t广东省阳江市江城区岗列街道幸福路12号\t3.5母\t20只\n"
                + "2\t陈师傅\t13912345678\t广西玉林市福绵区沙田镇东风村\t4公\t15只\n";
        List<CrabShipment> rows = CrabOrderParser.parse(text);
        assertEquals(2, rows.size());
        assertEquals("王大姐", rows.get(0).getCustomerName());
        assertEquals("13800138000", rows.get(0).getPhone());
        assertEquals("广东省阳江市江城区岗列街道幸福路12号", rows.get(0).getAddress());
        assertEquals("3.5母", rows.get(0).getSpec());
        assertEquals(20, rows.get(0).getQuantity());
        assertEquals(1, rows.get(0).getSeqNo());
        assertEquals("4公", rows.get(1).getSpec());
        assertEquals(15, rows.get(1).getQuantity());
    }

    @Test
    void parseSpacedLinesAnchoredByPhone() {
        String text = "张三 18600001111 海南省海口市美兰区蓝天路3号 2.8母 8只\n"
                + "4 李梅 13700002222 广东省湛江市霞山区人民大道88号 3母 12";
        List<CrabShipment> rows = CrabOrderParser.parse(text);
        assertEquals(2, rows.size());
        assertEquals("张三", rows.get(0).getCustomerName());
        assertEquals("2.8母", rows.get(0).getSpec());
        assertEquals(8, rows.get(0).getQuantity());
        assertEquals(4, rows.get(1).getSeqNo());
        assertEquals("李梅", rows.get(1).getCustomerName());
        assertEquals("3母", rows.get(1).getSpec());
        assertEquals(12, rows.get(1).getQuantity());
    }

    @Test
    void skipHeaderOnly() {
        List<CrabShipment> rows = CrabOrderParser.parse("序号 姓名 电话 地址 规格 数量");
        assertNotNull(rows);
        assertEquals(0, rows.size());
    }

    @Test
    void ignoreEmptyAndRequireNameOrPhone() {
        List<CrabShipment> rows = CrabOrderParser.parse("\n\n   \n");
        assertEquals(0, rows.size());
        List<CrabShipment> one = CrabOrderParser.parse("刘姐 13500003333 阳江 3.5母 20只");
        assertFalse(one.isEmpty());
        assertEquals("刘姐", one.get(0).getCustomerName());
    }
}
