package springboot.service;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 羽毛球球局名称规则（服务端保存时统一处理，预览接口用同一套）：
 * <ul>
 *   <li>空名 → 活动日期 YYYY-MM-DD；</li>
 *   <li>有名 → 去掉首尾空格，结尾已是「日期」或「日期 (n)」先去掉（不叠加），再追加「 活动日期」；</li>
 *   <li>同一天同一用户重名 → 「… 日期 (2)」「… 日期 (3)」，正在编辑的这条不算；</li>
 *   <li>总长不超过 {@link #MAX_LENGTH}，超了截原名，日期后缀保持完整。</li>
 * </ul>
 * 纯计算，不访问数据库。
 */
public final class BadmintonNames {

    /** 与 badminton_bill.title VARCHAR(100) 一致（按字符数） */
    public static final int MAX_LENGTH = 100;

    /** 结尾的「YYYY-MM-DD」或「YYYY-MM-DD (n)」，前面可有空白 */
    private static final Pattern TAIL = Pattern.compile("^(.*?)\\s*(\\d{4}-\\d{2}-\\d{2})(?:\\s*\\((\\d{1,4})\\))?$",
            Pattern.DOTALL);

    private BadmintonNames() {
    }

    /** 用户填的名称去掉首尾空格、结尾日期 / 日期 (n) 后剩下的部分（可能为空串） */
    public static String baseName(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        Matcher m = TAIL.matcher(t);
        if (m.matches()) {
            t = m.group(1).trim();
        }
        return t;
    }

    /** 拼名称：base + " " + date [+ " (n)"]，n ≤ 1 时不加序号；超长截 base */
    public static String compose(String base, String date, int n) {
        String suffix = n > 1 ? date + " (" + n + ")" : date;
        String b = base == null ? "" : base.trim();
        if (b.isEmpty()) {
            return suffix;
        }
        int room = MAX_LENGTH - suffix.length() - 1;
        if (room <= 0) {
            return suffix;
        }
        b = truncateCodePoints(b, room).trim();
        return b.isEmpty() ? suffix : b + " " + suffix;
    }

    /**
     * 最终名称。existing 为同一用户、同一活动日期的其他记录（已排除正在编辑的这条）的名称。
     */
    public static String normalize(String raw, String date, Collection<String> existing) {
        String base = baseName(raw);
        int n = 1;
        String name = compose(base, date, n);
        if (existing == null || existing.isEmpty()) {
            return name;
        }
        while (existing.contains(name) && n < 9999) {
            n++;
            name = compose(base, date, n);
        }
        return name;
    }

    /** 名称结尾是否已经是「日期」或「日期 (n)」（老记录判断用） */
    public static boolean endsWithDate(String name) {
        return name != null && TAIL.matcher(name.trim()).matches();
    }

    /** 按字符（code point）截断，避免切开 emoji 等代理对；长度按 UTF-16 单位不超过 max */
    static String truncateCodePoints(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int end = max;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
