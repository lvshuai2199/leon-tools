package springboot.notes;

import java.net.URI;
import java.util.Locale;

/** 仓库地址和分支名校验。令牌单独存，不允许写在地址里。 */
public final class NoteRepoUrls {

    private NoteRepoUrls() {
    }

    public static String normalizeRepoUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("请填写仓库地址");
        }
        String s = raw.trim();
        if (s.length() > 500) {
            throw new IllegalArgumentException("仓库地址过长");
        }
        if (s.contains(" ") || s.contains("\\") || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("仓库地址不能包含空格");
        }
        URI uri;
        try {
            uri = new URI(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("仓库地址不是合法的链接");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("仓库地址只支持 http 或 https");
        }
        if (uri.getRawUserInfo() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("请不要把令牌写在地址里，填到访问令牌");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("仓库地址缺少主机名");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if ("169.254.169.254".equals(hostLower) || "metadata.google.internal".equals(hostLower)) {
            throw new IllegalArgumentException("仓库地址不可用");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.isBlank() || "/".equals(path)) {
            throw new IllegalArgumentException("仓库地址缺少仓库路径");
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String normalizeBranch(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("请选择分支");
        }
        String b = raw.trim();
        if (b.length() > 200) {
            throw new IllegalArgumentException("分支名过长");
        }
        if (b.startsWith("-") || b.startsWith("/") || b.startsWith(".") || b.endsWith("/") || b.endsWith(".")
                || b.endsWith(".lock") || b.contains("..") || b.contains("//") || b.contains("@")
                || b.contains("\\") || b.contains(" ") || b.contains("~") || b.contains("^")
                || b.contains(":") || b.contains("?") || b.contains("*") || b.contains("[")) {
            throw new IllegalArgumentException("分支名不合法");
        }
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (c <= 0x20 || c == 0x7f) {
                throw new IllegalArgumentException("分支名不合法");
            }
        }
        return b;
    }

    /** 空串表示没填。调用方决定留空是「不修改」还是「清除」。 */
    public static String normalizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.length() > 500) {
            throw new IllegalArgumentException("访问令牌过长");
        }
        if (t.indexOf(' ') >= 0 || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("访问令牌格式不正确");
        }
        return t;
    }
}
