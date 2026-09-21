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
 * 把粘贴/OCR 文本拆成出货行：姓名、电话、地址、规格、数量。
 */
public final class CrabOrderParser {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern SPEC_QTY = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*([公母])\\s*(?:蟹)?\\s*(\\d+)\\s*只?\\s*$");
    private static final Pattern QTY_ONLY = Pattern.compile("(\\d+)\\s*只\\s*$");
    private static final Pattern SPEC_ONLY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([公母])\\s*(?:蟹)?\\s*$");
    private static final Pattern LEAD_SEQ = Pattern.compile("^(\\d{1,4})[\\.、\\s]+");

    private CrabOrderParser() {
    }

    public static List<CrabShipment> parse(String raw) {
        List<CrabShipment> rows = new ArrayList<>();
        if (raw == null) {
            return rows;
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ').trim();
        if (text.isEmpty()) {
            return rows;
        }

        String[] lines = text.split("\n");
        ColumnMap header = null;
        for (String original : lines) {
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
        Matcher phoneMatcher = PHONE.matcher(line);
        if (!phoneMatcher.find()) {
            return parseWithoutPhone(line);
        }
        String phone = phoneMatcher.group(1);
        String before = line.substring(0, phoneMatcher.start()).trim();
        String after = line.substring(phoneMatcher.end()).trim();

        CrabShipment row = new CrabShipment();
        row.setPhone(phone);
        applySeqAndName(row, before);
        applyAddressSpecQty(row, after);
        return row;
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
        applyAddressSpecQty(row, "");
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
        Matcher specQty = SPEC_QTY.matcher(text);
        if (specQty.find()) {
            row.setSpec(specQty.group(1) + specQty.group(2));
            row.setQuantity(parseInt(specQty.group(3)));
            row.setAddress(trimToNull(text.substring(0, specQty.start())));
            return;
        }
        Matcher qty = QTY_ONLY.matcher(text);
        if (qty.find()) {
            row.setQuantity(parseInt(qty.group(1)));
            text = text.substring(0, qty.start()).trim();
        }
        Matcher spec = SPEC_ONLY.matcher(text);
        if (spec.find()) {
            row.setSpec(spec.group(1) + spec.group(2));
            text = text.substring(0, spec.start()).trim();
        }
        row.setAddress(trimToNull(text));
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
        Matcher matcher = PHONE.matcher(raw.replace(" ", ""));
        return matcher.find() ? matcher.group(1) : trimToNull(raw);
    }

    private static String cleanSpec(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([公母])").matcher(value);
        if (matcher.find()) {
            return matcher.group(1) + matcher.group(2);
        }
        return value.replace(" ", "");
    }

    private static Integer parseQuantity(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(raw);
        return matcher.find() ? parseInt(matcher.group(1)) : null;
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
            if (h.contains("规格") || h.contains("尺码") || h.contains("公母")) {
                return "spec";
            }
            if (h.contains("数量") || h.contains("只数") || h.contains("qty")) {
                return "qty";
            }
            return null;
        }
    }
}
