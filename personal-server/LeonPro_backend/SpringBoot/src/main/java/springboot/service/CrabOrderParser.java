package springboot.service;

import springboot.domain.CrabShipment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把粘贴/OCR/微信文本拆成出货行：姓名、电话、地址、规格、数量。
 * 规格认「两」+公母，数量认「只」。
 */
public final class CrabOrderParser {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern PHONE_SPACED = Pattern.compile("(?<!\\d)(1[3-9]\\d)[\\s\\-]*(\\d{4})[\\s\\-]*(\\d{4})(?!\\d)");
    private static final Pattern SPEC_A = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*两?\\s*([公母])(?:蟹)?");
    private static final Pattern SPEC_B = Pattern.compile("([公母])(?:蟹)?\\s*(\\d+(?:\\.\\d+)?)\\s*两?");
    private static final Pattern QTY_ZHI = Pattern.compile("(\\d+)\\s*只");
    private static final Pattern QTY_MARK = Pattern.compile("(?:数量|只数|共|合计)[:：]?\\s*(\\d+)");
    private static final Pattern QTY_TAIL = Pattern.compile("(?:[xX×*]\\s*)(\\d+)\\s*$");
    private static final Pattern LEAD_SEQ = Pattern.compile("^(\\d{1,4})[\\.、\\s]+");
    private static final Pattern CN_BEFORE_ZHI = Pattern.compile("([一二三四五六七八九十两]+)只");
    private static final Pattern CN_BEFORE_LIANG = Pattern.compile("([一二三四五六七八九十]+)(?=两\\s*[公母])");
    private static final Pattern LABELS = Pattern.compile(
            "(?:收件人|收货人|客户|姓名|电话|手机号|手机|地址|住址|规格|数量|只数)\\s*[:：]\\s*");
    private static final Pattern ADDR_TOKEN = Pattern.compile("[省市区县旗盟州镇乡村街道路巷弄号楼栋单元室园苑屯寨庄组社场厦座层]");

    private CrabOrderParser() {
    }

    public static List<CrabShipment> parse(String raw) {
        List<CrabShipment> rows = new ArrayList<>();
        if (raw == null) {
            return rows;
        }
        String text = preprocess(raw);
        if (text.isEmpty()) {
            return rows;
        }
        if (looksLikeTable(text)) {
            return parseLines(text);
        }
        List<String> blocks = splitBlocks(text);
        if (blocks.size() == 1) {
            return parseLines(text);
        }
        for (String block : blocks) {
            CrabShipment row = parseLoose(block.replace('\n', ' '));
            if (row != null && hasContent(row)) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            return parseLines(text);
        }
        return rows;
    }

    static String preprocess(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ').replace('\u3000', ' ');
        text = PHONE_SPACED.matcher(text).replaceAll("$1$2$3");
        text = LABELS.matcher(text).replaceAll(" ");
        text = CN_BEFORE_ZHI.matcher(text).replaceAll(m -> chineseToInt(m.group(1)) + "只");
        text = CN_BEFORE_LIANG.matcher(text).replaceAll(m -> String.valueOf(chineseToInt(m.group(1))));
        text = text.replace("两只", "2只");
        return text.trim();
    }

    private static List<CrabShipment> parseLines(String text) {
        List<CrabShipment> rows = new ArrayList<>();
        ColumnMap header = null;
        for (String original : text.split("\n")) {
            String line = normalizeLine(original);
            if (line.isEmpty()) {
                continue;
            }
            if (header == null && looksLikeHeader(line)) {
                header = ColumnMap.from(line);
                continue;
            }
            CrabShipment row = header != null ? parseByHeader(line, header) : parseLoose(line);
            if (row != null && hasContent(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> splitBlocks(String text) {
        String[] parts = text.split("\\n\\s*\\n+");
        List<String> blocks = new ArrayList<>();
        if (parts.length <= 1) {
            blocks.add(text);
            return blocks;
        }
        for (String part : parts) {
            String t = part.trim();
            if (!t.isEmpty()) {
                blocks.add(t);
            }
        }
        return blocks;
    }

    private static boolean looksLikeTable(String text) {
        int tabs = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\t') {
                tabs++;
            }
        }
        return tabs >= 3 || looksLikeHeader(text.split("\n", 2)[0]);
    }

    static String normalizeLine(String original) {
        if (original == null) {
            return "";
        }
        String line = original.replace('\u3000', ' ').replace('|', '\t').trim();
        line = line.replaceAll("[ ]{2,}", " ");
        return line;
    }

    static boolean looksLikeHeader(String line) {
        String compact = line.replace("\t", "").replace(" ", "");
        return compact.contains("姓名") && (compact.contains("电话") || compact.contains("手机")
                || compact.contains("地址") || compact.contains("规格"));
    }

    private static CrabShipment parseByHeader(String line, ColumnMap header) {
        String[] cols = splitColumns(line);
        if (cols.length == 1) {
            return parseLoose(line);
        }
        CrabShipment row = new CrabShipment();
        row.setSeqNo(parseInt(header.get(cols, "seq")));
        row.setCustomerName(cleanName(header.get(cols, "name")));
        row.setPhone(cleanPhone(header.get(cols, "phone")));
        row.setAddress(trimToNull(header.get(cols, "address")));
        row.setSpec(cleanSpec(header.get(cols, "spec")));
        row.setQuantity(parseQuantity(header.get(cols, "qty")));
        if (!hasContent(row)) {
            return parseLoose(line);
        }
        fillMissingFromLoose(row, line);
        return row;
    }

    static CrabShipment parseLoose(String line) {
        String text = normalizeLine(line);
        Integer seq = null;
        Matcher seqMatcher = LEAD_SEQ.matcher(text);
        if (seqMatcher.find()) {
            seq = parseInt(seqMatcher.group(1));
            text = text.substring(seqMatcher.end()).trim();
        } else {
            String[] parts = text.split("[\\s\\t]+", 2);
            if (parts.length > 0 && parts[0].matches("\\d{1,4}") && parts.length > 1) {
                seq = parseInt(parts[0]);
                text = parts[1].trim();
            }
        }
        SpecQty extracted = extractSpecQty(text);
        String remainder = extracted.address == null ? "" : extracted.address;
        Matcher phoneMatcher = PHONE.matcher(remainder);
        if (!phoneMatcher.find()) {
            CrabShipment fallback = parseWithoutPhone(normalizeLine(line));
            if (fallback == null) {
                return null;
            }
            if (seq != null && fallback.getSeqNo() == null) {
                fallback.setSeqNo(seq);
            }
            if (isBlank(fallback.getSpec())) {
                fallback.setSpec(extracted.spec);
            }
            if (fallback.getQuantity() == null) {
                fallback.setQuantity(extracted.quantity);
            }
            return fallback;
        }
        String phone = phoneMatcher.group(1);
        String left = remainder.substring(0, phoneMatcher.start()).trim();
        String right = remainder.substring(phoneMatcher.end()).trim();
        NameAddr pair = splitNameAddress(left, right);
        CrabShipment row = new CrabShipment();
        row.setSeqNo(seq);
        row.setPhone(phone);
        row.setCustomerName(cleanName(pair.name));
        row.setAddress(trimToNull(pair.address));
        row.setSpec(extracted.spec);
        row.setQuantity(extracted.quantity);
        return row;
    }

    static NameAddr splitNameAddress(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        if (!a.isEmpty() && !b.isEmpty()) {
            int la = visualLen(a);
            int lb = visualLen(b);
            if (la < lb) {
                return new NameAddr(a, b);
            }
            if (lb < la) {
                return new NameAddr(b, a);
            }
            if (looksLikeName(a) && !looksLikeName(b)) {
                return new NameAddr(a, b);
            }
            if (looksLikeName(b) && !looksLikeName(a)) {
                return new NameAddr(b, a);
            }
            if (looksLikeAddress(b) && !looksLikeAddress(a)) {
                return new NameAddr(a, b);
            }
            if (looksLikeAddress(a) && !looksLikeAddress(b)) {
                return new NameAddr(b, a);
            }
            return new NameAddr(a, b);
        }
        if (a.isEmpty() && !b.isEmpty()) {
            return splitNameFromBlob(b);
        }
        if (!a.isEmpty()) {
            return splitNameFromBlob(a);
        }
        return new NameAddr(null, null);
    }

    static NameAddr splitNameFromBlob(String blob) {
        String text = blob == null ? "" : blob.trim();
        if (text.isEmpty()) {
            return new NameAddr(null, null);
        }
        String[] parts = text.split("\\s+");
        if (parts.length >= 2) {
            int best = -1;
            int bestLen = Integer.MAX_VALUE;
            for (int i = 0; i < parts.length; i++) {
                int n = visualLen(parts[i]);
                if (n > 0 && n <= 5 && looksLikeName(parts[i]) && n < bestLen) {
                    best = i;
                    bestLen = n;
                }
            }
            if (best < 0) {
                for (int i = 0; i < parts.length; i++) {
                    int n = visualLen(parts[i]);
                    if (n > 0 && n <= 5 && n < bestLen) {
                        best = i;
                        bestLen = n;
                    }
                }
            }
            if (best >= 0) {
                StringBuilder addr = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i == best) {
                        continue;
                    }
                    if (addr.length() > 0) {
                        addr.append(' ');
                    }
                    addr.append(parts[i]);
                }
                return new NameAddr(parts[best], addr.toString());
            }
        }
        if (text.length() <= 5 && looksLikeName(text)) {
            return new NameAddr(text, null);
        }
        int bestStart = Integer.MIN_VALUE / 4;
        int bestEnd = Integer.MIN_VALUE / 4;
        int startLen = 2;
        int endLen = 2;
        int max = Math.min(4, Math.max(0, text.length() - 2));
        for (int len = 2; len <= max; len++) {
            String prefix = text.substring(0, len);
            String restAfter = text.substring(len);
            int startScore = scoreName(prefix) + scoreAddress(restAfter);
            if (restAfter.matches("^[\\u4e00-\\u9fff]{2}(省|市|区|县|镇|乡).*")) {
                startScore += 8;
            } else if (restAfter.startsWith("省") || restAfter.startsWith("市") || restAfter.startsWith("区")
                    || restAfter.matches("^[\\u4e00-\\u9fff]{3}(省|市).*")) {
                startScore += 3;
            }
            if (startScore > bestStart) {
                bestStart = startScore;
                startLen = len;
            }
            String suffix = text.substring(text.length() - len);
            String restBefore = text.substring(0, text.length() - len);
            int endScore = scoreName(suffix) + scoreAddress(restBefore);
            if (addressLikelyAtHead(text)) {
                endScore += 6;
            }
            if (endScore > bestEnd) {
                bestEnd = endScore;
                endLen = len;
            }
        }
        if (addressLikelyAtHead(text) || bestEnd >= bestStart) {
            return new NameAddr(text.substring(text.length() - endLen), text.substring(0, text.length() - endLen));
        }
        return new NameAddr(text.substring(0, startLen), text.substring(startLen));
    }

    private static boolean addressLikelyAtHead(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.matches("^[\\u4e00-\\u9fff]{0,3}(省|市|自治区).*")
                || text.startsWith("北京") || text.startsWith("上海") || text.startsWith("天津")
                || text.startsWith("重庆") || text.startsWith("广西") || text.startsWith("西藏")
                || text.startsWith("宁夏") || text.startsWith("新疆") || text.startsWith("内蒙");
    }

    private static boolean looksLikeName(String raw) {
        String value = raw == null ? "" : raw.replaceAll("\\s+", "");
        if (value.length() < 1 || value.length() > 5) {
            return false;
        }
        if (ADDR_TOKEN.matcher(value).find()) {
            return false;
        }
        if (value.matches(".*\\d.*")) {
            return false;
        }
        return value.matches("[\\u4e00-\\u9fff·•]{1,5}");
    }

    private static boolean looksLikeAddress(String raw) {
        return raw != null && ADDR_TOKEN.matcher(raw).find();
    }

    private static int scoreName(String raw) {
        if (looksLikeName(raw)) {
            int n = visualLen(raw);
            return 10 + Math.max(0, 4 - n);
        }
        if (ADDR_TOKEN.matcher(raw == null ? "" : raw).find()) {
            return -12;
        }
        if (raw != null && raw.matches(".*\\d.*")) {
            return -8;
        }
        return -3;
    }

    private static int scoreAddress(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -4;
        }
        int score = 0;
        if (raw.contains("省")) {
            score += 3;
        }
        if (raw.contains("市")) {
            score += 3;
        }
        if (raw.contains("区") || raw.contains("县")) {
            score += 2;
        }
        if (raw.contains("路") || raw.contains("街") || raw.contains("道")) {
            score += 2;
        }
        if (raw.contains("号") || raw.contains("村") || raw.contains("镇")) {
            score += 2;
        }
        if (raw.length() >= 8) {
            score += 2;
        }
        if (raw.length() >= 16) {
            score += 2;
        }
        return score;
    }

    private static int visualLen(String raw) {
        return raw == null ? 0 : raw.replaceAll("\\s+", "").length();
    }

    private static CrabShipment parseWithoutPhone(String line) {
        String[] cols = splitColumns(line);
        if (cols.length >= 4) {
            CrabShipment row = new CrabShipment();
            int i = 0;
            Integer seq = parseInt(cols[0]);
            if (seq != null) {
                row.setSeqNo(seq);
                i = 1;
            }
            if (i < cols.length) {
                row.setCustomerName(cleanName(cols[i++]));
            }
            if (i < cols.length && PHONE.matcher(cols[i]).find()) {
                row.setPhone(cleanPhone(cols[i++]));
            }
            if (i < cols.length) {
                row.setAddress(trimToNull(cols[i++]));
            }
            if (i < cols.length) {
                row.setSpec(cleanSpec(cols[i++]));
            }
            if (i < cols.length) {
                row.setQuantity(parseQuantity(cols[i]));
            }
            return hasContent(row) ? row : null;
        }
        CrabShipment row = new CrabShipment();
        applySeqAndName(row, line);
        applyAddressSpecQty(row, line);
        if (isBlank(row.getAddress()) || (row.getAddress() != null && row.getAddress().equals(row.getCustomerName()))) {
            row.setAddress(null);
        }
        return hasContent(row) ? row : null;
    }

    private static void applySeqAndName(CrabShipment row, String before) {
        String text = before == null ? "" : before.trim();
        Matcher seq = LEAD_SEQ.matcher(text);
        if (seq.find()) {
            row.setSeqNo(parseInt(seq.group(1)));
            text = text.substring(seq.end()).trim();
        } else {
            String[] parts = text.split("[\\s\\t]+", 2);
            if (parts.length > 0 && parts[0].matches("\\d{1,4}")) {
                row.setSeqNo(parseInt(parts[0]));
                text = parts.length > 1 ? parts[1].trim() : "";
            }
        }
        row.setCustomerName(cleanName(text));
    }

    private static void applyAddressSpecQty(CrabShipment row, String after) {
        String text = after == null ? "" : after.trim();
        SpecQty extracted = extractSpecQty(text);
        row.setSpec(extracted.spec);
        row.setQuantity(extracted.quantity);
        row.setAddress(trimToNull(extracted.address));
    }

    static SpecQty extractSpecQty(String raw) {
        SpecQty result = new SpecQty();
        String text = raw == null ? "" : raw.trim();
        int specStart = -1;
        int specEnd = -1;
        Matcher specA = SPEC_A.matcher(text);
        Matcher specB = SPEC_B.matcher(text);
        if (specA.find()) {
            result.spec = specA.group(1) + specA.group(2);
            specStart = specA.start();
            specEnd = specA.end();
        } else if (specB.find()) {
            result.spec = specB.group(2) + specB.group(1);
            specStart = specB.start();
            specEnd = specB.end();
        }
        int qtyStart = -1;
        int qtyEnd = -1;
        Matcher qtyZhi = QTY_ZHI.matcher(text);
        Matcher qtyMark = QTY_MARK.matcher(text);
        Matcher qtyTail = QTY_TAIL.matcher(text);
        if (qtyZhi.find()) {
            result.quantity = parseInt(qtyZhi.group(1));
            qtyStart = qtyZhi.start();
            qtyEnd = qtyZhi.end();
        } else if (qtyMark.find()) {
            result.quantity = parseInt(qtyMark.group(1));
            qtyStart = qtyMark.start();
            qtyEnd = qtyMark.end();
        } else if (qtyTail.find()) {
            result.quantity = parseInt(qtyTail.group(1));
            qtyStart = qtyTail.start();
            qtyEnd = qtyTail.end();
        } else {
            Matcher bare = Pattern.compile("(\\d+)\\s*$").matcher(text);
            if (bare.find() && specEnd >= 0 && bare.start() >= specEnd) {
                result.quantity = parseInt(bare.group(1));
                qtyStart = bare.start();
                qtyEnd = bare.end();
            }
        }
        result.address = trimToNull(removeSpans(text, specStart, specEnd, qtyStart, qtyEnd));
        return result;
    }

    private static String removeSpans(String text, int s1, int e1, int s2, int e2) {
        boolean a = s1 >= 0;
        boolean b = s2 >= 0;
        if (!a && !b) {
            return text;
        }
        if (a && b) {
            int from = Math.min(s1, s2);
            int to = Math.max(e1, e2);
            return (text.substring(0, from) + " " + text.substring(to)).replaceAll("[ ]{2,}", " ").trim();
        }
        int from = a ? s1 : s2;
        int to = a ? e1 : e2;
        return (text.substring(0, from) + " " + text.substring(to)).replaceAll("[ ]{2,}", " ").trim();
    }

    private static void fillMissingFromLoose(CrabShipment row, String line) {
        CrabShipment loose = parseLoose(line);
        if (loose == null) {
            return;
        }
        if (isBlank(row.getCustomerName())) {
            row.setCustomerName(loose.getCustomerName());
        }
        if (isBlank(row.getPhone())) {
            row.setPhone(loose.getPhone());
        }
        if (isBlank(row.getAddress())) {
            row.setAddress(loose.getAddress());
        }
        if (isBlank(row.getSpec())) {
            row.setSpec(loose.getSpec());
        }
        if (row.getQuantity() == null) {
            row.setQuantity(loose.getQuantity());
        }
        if (row.getSeqNo() == null) {
            row.setSeqNo(loose.getSeqNo());
        }
    }

    private static String[] splitColumns(String line) {
        if (line.contains("\t")) {
            return line.split("\t", -1);
        }
        if (line.contains(",") && line.split(",").length >= 4) {
            return line.split(",", -1);
        }
        return new String[]{line};
    }

    private static String cleanName(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        return value.replaceAll("^[\\d\\.、\\s]+", "").trim();
    }

    private static String cleanPhone(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = PHONE.matcher(raw.replace(" ", "").replace("-", ""));
        return matcher.find() ? matcher.group(1) : trimToNull(raw);
    }

    private static String cleanSpec(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        SpecQty extracted = extractSpecQty(value);
        if (extracted.spec != null) {
            return extracted.spec;
        }
        return value.replace(" ", "").replace("两", "");
    }

    private static Integer parseQuantity(String raw) {
        if (raw == null) {
            return null;
        }
        SpecQty extracted = extractSpecQty(raw);
        if (extracted.quantity != null) {
            return extracted.quantity;
        }
        Matcher zhi = QTY_ZHI.matcher(raw);
        if (zhi.find()) {
            return parseInt(zhi.group(1));
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(raw);
        return matcher.find() ? parseInt(matcher.group(1)) : null;
    }

    static int chineseToInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        if (raw.equals("两")) {
            return 2;
        }
        Map<Character, Integer> map = new HashMap<>();
        map.put('一', 1);
        map.put('二', 2);
        map.put('三', 3);
        map.put('四', 4);
        map.put('五', 5);
        map.put('六', 6);
        map.put('七', 7);
        map.put('八', 8);
        map.put('九', 9);
        map.put('十', 10);
        map.put('两', 2);
        int total = 0;
        int num = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            Integer v = map.get(c);
            if (v == null) {
                continue;
            }
            if (c == '十') {
                total += (num == 0 ? 1 : num) * 10;
                num = 0;
            } else {
                num = v;
            }
        }
        return total + num;
    }

    private static Integer parseInt(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasContent(CrabShipment row) {
        return row != null && (!isBlank(row.getCustomerName()) || !isBlank(row.getPhone()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    static final class NameAddr {
        final String name;
        final String address;

        NameAddr(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    static final class SpecQty {
        String spec;
        Integer quantity;
        String address;
    }

    static final class ColumnMap {
        private final Map<String, Integer> index = new HashMap<>();

        static ColumnMap from(String headerLine) {
            ColumnMap map = new ColumnMap();
            String[] cols = headerLine.contains("\t") ? headerLine.split("\t", -1) : headerLine.split("[\\s,]+");
            for (int i = 0; i < cols.length; i++) {
                String key = classify(cols[i]);
                if (key != null && !map.index.containsKey(key)) {
                    map.index.put(key, i);
                }
            }
            return map;
        }

        String get(String[] cols, String key) {
            Integer i = index.get(key);
            if (i == null || i < 0 || i >= cols.length) {
                return null;
            }
            return cols[i];
        }

        private static String classify(String header) {
            String h = header == null ? "" : header.replace(" ", "").toLowerCase(Locale.ROOT);
            if (h.contains("序号") || h.equals("no") || h.equals("#")) {
                return "seq";
            }
            if (h.contains("姓名") || h.contains("客户") || h.contains("收件")) {
                return "name";
            }
            if (h.contains("电话") || h.contains("手机") || h.contains("phone")) {
                return "phone";
            }
            if (h.contains("地址") || h.contains("住址")) {
                return "address";
            }
            if (h.contains("规格") || h.contains("尺码") || h.contains("公母") || h.contains("两")) {
                return "spec";
            }
            if (h.contains("数量") || h.contains("只数") || h.contains("只") || h.contains("qty")) {
                return "qty";
            }
            return null;
        }
    }
}
