package springboot.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公开分享页用的手机号脱敏（登录后的接口仍返回完整号码）。
 * <p>
 * 规则：号码保留前 3 位和后 4 位，中间换成 ****；不足 7 位的号码整段换成 *。
 * 识别号码时忽略中间的空格、短横线，11 位手机号前的 86 / +86 会被识别并保留。
 * 一个字段里有多个号码时分别脱敏。
 * <ul>
 *   <li>{@link #maskPhone}：电话字段，字段里所有数字串都按号码处理</li>
 *   <li>{@link #maskAddress}：地址字段，只处理像号码的数字串（去掉分隔后至少 7 位），门牌号等短数字不动</li>
 * </ul>
 * 注意：按“前 3 后 4”规则，7 位号码会原样显示（3 + 4 = 7）。
 */
public final class PhoneMaskUtils {

    /** 数字串：数字之间允许单个空格或短横线 */
    private static final Pattern RUN = Pattern.compile("\\d+(?:[ \\-]\\d+)*");
    private static final Pattern SEP = Pattern.compile("[ \\-]");
    private static final int MIN_PHONE_DIGITS = 7;
    private static final int MOBILE_DIGITS = 11;

    private PhoneMaskUtils() {
    }

    /** 电话字段脱敏；null / 空串原样返回 */
    public static String maskPhone(String value) {
        return mask(value, false);
    }

    /** 地址字段里的号码脱敏；null / 空串原样返回 */
    public static String maskAddress(String value) {
        return mask(value, true);
    }

    private static String mask(String value, boolean addressMode) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher m = RUN.matcher(value);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(value, last, m.start());
            out.append(maskRun(m.group(), addressMode));
            last = m.end();
        }
        out.append(value.substring(last));
        return out.toString();
    }

    private static String maskRun(String run, boolean addressMode) {
        String[] groups = SEP.split(run);
        boolean anyLong = false;
        for (String g : groups) {
            if (g.length() >= MIN_PHONE_DIGITS) {
                anyLong = true;
                break;
            }
        }
        if (groups.length > 1 && anyLong) {
            // 多段且有一段本身就像号码：看成几个独立号码，按原分隔符拼回
            StringBuilder sb = new StringBuilder();
            Matcher sep = SEP.matcher(run);
            int pos = 0;
            for (String g : groups) {
                sb.append(addressMode && g.length() < MIN_PHONE_DIGITS ? g : maskNumber(g));
                pos += g.length();
                if (sep.find(pos)) {
                    sb.append(sep.group());
                    pos = sep.end();
                }
            }
            return sb.toString();
        }
        String digits = String.join("", groups);
        if (addressMode && digits.length() < MIN_PHONE_DIGITS) {
            return run;
        }
        return maskDigits(digits);
    }

    /** 一串连续数字（已去掉分隔符）：识别 86 前缀、多个 11 位手机号连写 */
    private static String maskDigits(String digits) {
        if (digits.length() == MOBILE_DIGITS + 2 && digits.startsWith("86") && digits.charAt(2) == '1') {
            return "86" + maskNumber(digits.substring(2));
        }
        List<String> mobiles = splitMobiles(digits);
        if (mobiles != null) {
            List<String> masked = new ArrayList<>();
            for (String p : mobiles) {
                masked.add(maskNumber(p));
            }
            return String.join(" ", masked);
        }
        return maskNumber(digits);
    }

    /** 长度是 11 的整数倍（至少 2 个）且每段都以 1 开头时，看成多个手机号连写 */
    private static List<String> splitMobiles(String digits) {
        if (digits.length() < MOBILE_DIGITS * 2 || digits.length() % MOBILE_DIGITS != 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < digits.length(); i += MOBILE_DIGITS) {
            String p = digits.substring(i, i + MOBILE_DIGITS);
            if (p.charAt(0) != '1') {
                return null;
            }
            parts.add(p);
        }
        return parts;
    }

    /** 单个号码：前 3 + **** + 后 4；不足 7 位全部换成 * */
    static String maskNumber(String digits) {
        if (digits.length() == MOBILE_DIGITS + 2 && digits.startsWith("86") && digits.charAt(2) == '1') {
            return "86" + maskNumber(digits.substring(2));
        }
        if (digits.length() < MIN_PHONE_DIGITS) {
            return "*".repeat(digits.length());
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }
}
