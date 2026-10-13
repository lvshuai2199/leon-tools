package springboot.service.repo;

import java.util.Locale;
import java.util.regex.Pattern;

/** 仓库路径和分支名校验，避免把用户输入拼进外部接口地址。 */
public final class RepoRefs {

    private static final Pattern SLUG = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");
    private static final Pattern SHA = Pattern.compile("^[0-9a-fA-F]{7,40}$");

    private RepoRefs() {
    }

    public static String provider(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!"github".equals(s) && !"gitee".equals(s)) {
            throw new IllegalArgumentException("只支持 GitHub 和 Gitee");
        }
        return s;
    }

    public static String slug(String raw, String label) {
        String s = raw == null ? "" : raw.trim();
        if (!SLUG.matcher(s).matches() || s.contains("..") || ".".equals(s)) {
            throw new IllegalArgumentException(label + "不可用");
        }
        return s;
    }

    public static String ref(String raw) {
        String ref = raw == null ? "" : raw.trim();
        if (ref.isEmpty() || ref.length() > 200
                || ref.contains("..") || ref.contains("\\") || ref.contains("?") || ref.contains("#")
                || ref.contains(" ") || ref.contains("@") || ref.startsWith("/") || ref.endsWith("/")
                || ref.contains("//") || ref.indexOf(':') >= 0) {
            throw new IllegalArgumentException("分支名不可用");
        }
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch < 32 || ch == 127) {
                throw new IllegalArgumentException("分支名不可用");
            }
        }
        return ref;
    }

    public static String sha(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (!SHA.matcher(s).matches()) {
            throw new IllegalArgumentException("提交号不可用");
        }
        return s.toLowerCase(Locale.ROOT);
    }

    public static int page(int page) {
        if (page < 1) {
            return 1;
        }
        return Math.min(page, 10);
    }
}
