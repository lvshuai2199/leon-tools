package springboot.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 壁纸文件存储：原图 + 缩略图保存在 {storage-dir}/{groupId}/ 下，通过 {url-prefix}/** 静态访问。
 * 数据库只存相对路径（{groupId}/{文件名}）。
 */
@Slf4j
@Component
public class WallpaperStorage {

    /** ImageIO 格式名 -> 保存扩展名 */
    private static final Map<String, String> FORMAT_EXT = Map.of(
            "jpeg", "jpg",
            "jpg", "jpg",
            "png", "png",
            "gif", "gif",
            "bmp", "bmp",
            "webp", "webp");

    /** 允许上传的文件扩展名 */
    private static final java.util.Set<String> ALLOWED_EXT = java.util.Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp");

    /** 防止解压炸弹：最大像素数 */
    private static final long MAX_PIXELS = 150_000_000L;

    @Value("${app.wallpaper.storage-dir:./uploads/wallpaper}")
    private String storageDir;

    @Value("${app.wallpaper.url-prefix:/uploads/wallpaper}")
    private String urlPrefix;

    /** random 接口 302 / json 使用的公网 API 前缀，生产 /prod-api，开发直连为空 */
    @Value("${app.wallpaper.redirect-prefix:}")
    private String redirectPrefix;

    @Value("${app.wallpaper.thumb-width:640}")
    private int thumbWidth;

    /** 缩略图 JPEG 质量 */
    private static final float THUMB_QUALITY = 0.8f;

    @Value("${app.wallpaper.max-file-size:20MB}")
    private DataSize maxFileSize;

    @Data
    public static class StoredImage {
        private String filePath;
        private String thumbPath;
        private int width;
        private int height;
        private long fileSize;
    }

    public Path baseDir() {
        return Paths.get(storageDir).toAbsolutePath().normalize();
    }

    /** 静态资源映射路径，如 /uploads/wallpaper */
    public String urlPrefix() {
        String p = urlPrefix == null || urlPrefix.isBlank() ? "/uploads/wallpaper" : urlPrefix.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public String redirectPrefix() {
        String b = redirectPrefix == null ? "" : redirectPrefix.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (!b.isEmpty() && !b.startsWith("/") && !b.startsWith("http://") && !b.startsWith("https://")) {
            b = "/" + b;
        }
        return b;
    }

    /** 相对站点根的访问路径，如 /uploads/wallpaper/{groupId}/{file}.jpg */
    public String relativeUrl(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            return null;
        }
        return urlPrefix() + "/" + relPath.replace('\\', '/');
    }

    /** 列表类接口返回的地址：站点根相对路径（/uploads/wallpaper/...），前端自行拼 API 前缀 */
    public String url(String relPath) {
        return relativeUrl(relPath);
    }

    /** random 接口使用的地址：redirect-prefix + /uploads/wallpaper/...，如 /prod-api/uploads/wallpaper/... */
    public String redirectUrl(String relPath) {
        String rel = relativeUrl(relPath);
        return rel == null ? null : redirectPrefix() + rel;
    }

    /** 校验并保存上传图片，生成缩略图 */
    public StoredImage store(String groupId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        if (maxFileSize != null && file.getSize() > maxFileSize.toBytes()) {
            throw new IllegalArgumentException("图片过大，最大 " + maxFileSize.toMegabytes() + "MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String clientExt = dot >= 0 ? original.substring(dot + 1).trim().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXT.contains(clientExt)) {
            throw new IllegalArgumentException("仅支持 jpg/jpeg/png/webp/gif/bmp 图片");
        }
        byte[] bytes = file.getBytes();
        // 内容必须能被 ImageIO 解码且格式在白名单内；保存扩展名取实际格式，文件名由服务端生成（UUID）
        Decoded decoded = decode(bytes);

        Path dir = groupDir(groupId);
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "");
        String fileName = name + "." + decoded.ext;
        String thumbName = name + "_thumb.jpg";

        writeAtomic(dir.resolve(fileName), bytes);
        try {
            writeAtomic(dir.resolve(thumbName), toJpeg(decoded.thumb, THUMB_QUALITY));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(dir.resolve(fileName));
            throw e;
        }

        StoredImage stored = new StoredImage();
        stored.setFilePath(groupId + "/" + fileName);
        stored.setThumbPath(groupId + "/" + thumbName);
        stored.setWidth(decoded.width);
        stored.setHeight(decoded.height);
        stored.setFileSize(bytes.length);
        return stored;
    }

    /** 把文件移动到另一个分组目录，返回新的相对路径；源文件不存在时仅改路径 */
    public String moveToGroup(String relPath, String targetGroupId) throws IOException {
        if (relPath == null || relPath.isBlank()) {
            return relPath;
        }
        Path src = resolve(relPath);
        String fileName = src.getFileName().toString();
        Path targetDir = groupDir(targetGroupId);
        Files.createDirectories(targetDir);
        Path dst = targetDir.resolve(fileName);
        if (Files.exists(src) && !src.equals(dst)) {
            try {
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return targetGroupId + "/" + fileName;
    }

    public void deleteQuietly(String... relPaths) {
        if (relPaths == null) {
            return;
        }
        for (String rel : relPaths) {
            if (rel == null || rel.isBlank()) {
                continue;
            }
            try {
                Files.deleteIfExists(resolve(rel));
            } catch (Exception e) {
                log.warn("删除壁纸文件失败 {}: {}", rel, e.getMessage());
            }
        }
    }

    /** 删除整个分组目录 */
    public void deleteGroupDirQuietly(String groupId) {
        try {
            Path dir = groupDir(groupId);
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("删除壁纸文件失败 {}: {}", p, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("删除壁纸分组目录失败 {}: {}", groupId, e.getMessage());
        }
    }

    private Path groupDir(String groupId) {
        if (groupId == null || !groupId.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw new IllegalArgumentException("分组ID非法");
        }
        return resolve(groupId);
    }

    private Path resolve(String relPath) {
        Path base = baseDir();
        Path p = base.resolve(relPath.replace('\\', '/')).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return p;
    }

    // ------------------------------------------------------------------ 图片处理

    static class Decoded {
        String ext;
        int width;
        int height;
        BufferedImage thumb;
    }

    Decoded decode(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                throw new IllegalArgumentException("无法读取图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("不支持的图片格式，仅支持 jpg/jpeg/png/webp/gif/bmp");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName() == null ? "" : reader.getFormatName().toLowerCase(Locale.ROOT);
                String ext = FORMAT_EXT.get(format);
                if (ext == null) {
                    throw new IllegalArgumentException("不支持的图片格式：" + format + "，仅支持 jpg/jpeg/png/webp/gif/bmp");
                }
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0) {
                    throw new IllegalArgumentException("图片尺寸无效");
                }
                if ((long) w * h > MAX_PIXELS) {
                    throw new IllegalArgumentException("图片分辨率过大");
                }
                int target = Math.max(16, thumbWidth);
                ImageReadParam param = reader.getDefaultReadParam();
                int sub = Math.max(1, w / (target * 2));
                if (sub > 1) {
                    param.setSourceSubsampling(sub, sub, 0, 0);
                }
                BufferedImage img = reader.read(0, param);
                Decoded d = new Decoded();
                d.ext = ext;
                d.width = w;
                d.height = h;
                d.thumb = scaleToWidth(img, target);
                return d;
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("图片解析失败：" + e.getMessage());
        }
    }

    /** 按最大宽度等比缩放（不放大），输出 RGB（透明区域填白） */
    static BufferedImage scaleToWidth(BufferedImage src, int maxWidth) {
        BufferedImage cur = src;
        int w = src.getWidth();
        int h = src.getHeight();
        int targetW = Math.min(maxWidth, w);
        int targetH = Math.max(1, (int) Math.round((double) h * targetW / w));
        // 逐级减半，缩小比例大时画质更好
        while (w / 2 >= targetW) {
            w = w / 2;
            h = Math.max(1, h / 2);
            cur = draw(cur, w, h);
        }
        return draw(cur, targetW, targetH);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    static byte[] toJpeg(BufferedImage img, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("缺少 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    private static void writeAtomic(Path target, byte[] data) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
