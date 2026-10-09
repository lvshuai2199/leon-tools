package springboot.notes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 随手记上传到仓库时的相对路径：`随手记/YYYY-MM-DD_标题.md`。 */
public final class NoteDraftNames {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private NoteDraftNames() {
    }

    public static String sanitizeTitle(String title) {
        String t = title == null ? "" : title.trim();
        t = t.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");
        t = t.replaceAll("\\s+", " ").trim();
        while (t.endsWith(".") || t.endsWith(" ") || t.endsWith("。")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        if (t.length() > 80) {
            t = t.substring(0, 80).trim();
        }
        return t.isEmpty() ? "未命名" : t;
    }

    public static String relativePath(String folder, String title, LocalDate day, String suffix) {
        String dir = folder == null || folder.isBlank() ? "随手记" : folder.trim();
        String name = DAY.format(day) + "_" + sanitizeTitle(title);
        if (suffix != null && !suffix.isBlank()) {
            name = name + "-" + suffix;
        }
        return NotePaths.normalizeRel(dir + "/" + name + ".md");
    }
}
