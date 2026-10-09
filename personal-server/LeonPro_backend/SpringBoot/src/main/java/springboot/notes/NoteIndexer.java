package springboot.notes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 扫描仓库里的 Markdown，跳过依赖目录、隐藏目录和指向仓库外的符号链接。 */
public final class NoteIndexer {

    public record Doc(String path, String title, int sizeBytes) {
    }

    public record Result(List<Doc> docs, int skippedLarge) {
    }

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "dist", "target", "build", "vendor",
            ".idea", ".vscode", "coverage", ".next", "out");

    private NoteIndexer() {
    }

    public static Result index(Path root, int maxFiles, int maxBytes) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return new Result(List.of(), 0);
        }
        Path rootReal = root.toRealPath();
        List<Doc> docs = new ArrayList<>();
        int[] skipped = {0};
        Files.walkFileTree(rootReal, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(rootReal)) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (name.startsWith(".") || SKIP_DIRS.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                if (Files.isSymbolicLink(dir)) {
                    try {
                        if (!dir.toRealPath().startsWith(rootReal)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    } catch (IOException e) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName() == null ? "" : file.getFileName().toString();
                if (name.isEmpty() || name.startsWith(".")) {
                    return FileVisitResult.CONTINUE;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".md") && !lower.endsWith(".markdown")) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    if (Files.isSymbolicLink(file)) {
                        Path real = file.toRealPath();
                        if (!real.startsWith(rootReal)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    Path real = file.toRealPath();
                    if (!real.startsWith(rootReal) || !Files.isRegularFile(real)) {
                        return FileVisitResult.CONTINUE;
                    }
                    long size = Files.size(real);
                    if (size > maxBytes) {
                        skipped[0]++;
                        return FileVisitResult.CONTINUE;
                    }
                    if (docs.size() >= maxFiles) {
                        skipped[0]++;
                        return FileVisitResult.CONTINUE;
                    }
                    String rel = rootReal.relativize(real).toString().replace('\\', '/');
                    docs.add(new Doc(rel, titleOf(name, readHead(real)), (int) size));
                } catch (IOException ignored) {
                    // 单个文件读失败不影响其余笔记
                }
                return FileVisitResult.CONTINUE;
            }
        });
        docs.sort(Comparator.comparing(Doc::path, String.CASE_INSENSITIVE_ORDER));
        return new Result(docs, skipped[0]);
    }

    static String titleOf(String filename, String head) {
        String body = stripFrontMatter(head == null ? "" : head);
        int seen = 0;
        for (String line : body.split("\n", -1)) {
            if (seen++ > 40) {
                break;
            }
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("#")) {
                String title = t.replaceFirst("^#+\\s*", "").trim();
                if (!title.isEmpty()) {
                    return cut(title, 300);
                }
            }
            break;
        }
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base.isEmpty() ? filename : base;
    }

    static String stripFrontMatter(String text) {
        if (text == null || !text.startsWith("---")) {
            return text == null ? "" : text;
        }
        int nl = text.indexOf('\n');
        if (nl < 0) {
            return text;
        }
        int end = text.indexOf("\n---", nl);
        if (end < 0) {
            return text;
        }
        int rest = end + 4;
        if (rest < text.length() && text.charAt(rest) == '\r') {
            rest++;
        }
        if (rest < text.length() && text.charAt(rest) == '\n') {
            rest++;
        }
        return text.substring(Math.min(rest, text.length()));
    }

    private static String readHead(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }

    private static String cut(String s, int max) {
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
