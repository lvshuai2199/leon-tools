package springboot.notes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 仓库内相对路径。拒绝绝对路径、`..` 和 `.git`。 */
public final class NotePaths {

    private NotePaths() {
    }

    public static String normalizeRel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("缺少文件路径");
        }
        String s = raw.trim().replace('\\', '/');
        if (s.startsWith("/") || s.contains(":")) {
            throw new IllegalArgumentException("文件路径不合法");
        }
        String[] parts = s.split("/");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) {
                continue;
            }
            if ("..".equals(p) || ".git".equals(p) || p.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("文件路径不合法");
            }
            out.add(p);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("文件路径不合法");
        }
        String rel = String.join("/", out);
        if (rel.length() > 700) {
            throw new IllegalArgumentException("文件路径过长");
        }
        return rel;
    }

    /**
     * 解析到仓库根目录之内。文件已存在时再按真实路径确认一次，避免符号链接指向仓库外面。
     * 文件还不存在时返回规范化后的路径（上传新文件会用到）。
     */
    public static Path resolve(Path root, String rel) throws IOException {
        String norm = normalizeRel(rel);
        Path rootReal = root.toRealPath();
        Path target = rootReal.resolve(norm).normalize();
        if (!target.startsWith(rootReal)) {
            throw new IllegalArgumentException("文件路径不合法");
        }
        if (Files.exists(target)) {
            Path real = target.toRealPath();
            if (!real.startsWith(rootReal)) {
                throw new IllegalArgumentException("文件路径不合法");
            }
            return real;
        }
        return target;
    }
}
