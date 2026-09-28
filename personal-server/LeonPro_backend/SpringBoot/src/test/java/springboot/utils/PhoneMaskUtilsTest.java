package springboot.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneMaskUtilsTest {

    @Test
    void nullAndEmptyUnchanged() {
        assertNull(PhoneMaskUtils.maskPhone(null));
        assertEquals("", PhoneMaskUtils.maskPhone(""));
        assertNull(PhoneMaskUtils.maskAddress(null));
        assertEquals("", PhoneMaskUtils.maskAddress(""));
    }

    @Test
    void mobile() {
        assertEquals("138****5678", PhoneMaskUtils.maskPhone("13812345678"));
        assertEquals("138****5678", PhoneMaskUtils.maskPhone("138 1234 5678"));
        assertEquals("138****5678", PhoneMaskUtils.maskPhone("138-1234-5678"));
        assertEquals("+86138****5678", PhoneMaskUtils.maskPhone("+8613812345678"));
        assertEquals("86138****5678", PhoneMaskUtils.maskPhone("8613812345678"));
        assertEquals("+86138****5678", PhoneMaskUtils.maskPhone("+86 138 1234 5678"));
    }

    @Test
    void landlineAndShort() {
        assertEquals("057****1234", PhoneMaskUtils.maskPhone("0571-8888 1234"));
        assertEquals("******", PhoneMaskUtils.maskPhone("123456"));
        assertEquals("*****", PhoneMaskUtils.maskPhone("95588"));
        // 规则原样：7 位号码前 3 + 后 4 等于全部显示
        assertEquals("888****8888", PhoneMaskUtils.maskPhone("8888888"));
    }

    @Test
    void twoNumbersInOneField() {
        assertEquals("138****5678/139****4321", PhoneMaskUtils.maskPhone("13812345678/13987654321"));
        assertEquals("138****5678 139****4321", PhoneMaskUtils.maskPhone("13812345678 13987654321"));
        assertEquals("138****5678 139****4321", PhoneMaskUtils.maskPhone("1381234567813987654321"));
        assertEquals("张三138****5678，李四139****4321", PhoneMaskUtils.maskPhone("张三13812345678，李四13987654321"));
        assertEquals("138****5678 ***", PhoneMaskUtils.maskPhone("13812345678 123"));
    }

    @Test
    void address() {
        assertEquals("浙江省杭州市西湖区文三路 88 号 3 单元 1202 室",
                PhoneMaskUtils.maskAddress("浙江省杭州市西湖区文三路 88 号 3 单元 1202 室"));
        assertEquals("文三路88号2-1202室", PhoneMaskUtils.maskAddress("文三路88号2-1202室"));
        assertEquals("文三路88号 收件人电话138****5678", PhoneMaskUtils.maskAddress("文三路88号 收件人电话13812345678"));
        assertEquals("文三路88号 138****5678", PhoneMaskUtils.maskAddress("文三路88号 138-1234-5678"));
        assertEquals("88 138****5678", PhoneMaskUtils.maskAddress("88 13812345678"));
        assertEquals("小区 0571-888****1234 转 88", PhoneMaskUtils.maskAddress("小区 0571-88881234 转 88"));
    }
}
