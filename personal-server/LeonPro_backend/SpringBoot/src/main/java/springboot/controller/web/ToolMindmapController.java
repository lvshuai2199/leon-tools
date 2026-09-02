package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springboot.DTO.MindmapSaveRequest;
import springboot.domain.ToolMindmap;
import springboot.service.ToolMindmapService;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 思维导图存储：保存 PNG 到本地，提供可复制的外访链接；支持列表与修改。
 */
@RestController
public class ToolMindmapController {

    private static final Pattern PUBLIC_ID = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");

    @Autowired
    private ToolMindmapService toolMindmapService;

    @Value("${app.mindmap.storage-dir:./data/mindmap}")
    private String storageDir;

    @GetMapping("mindmap/getAll")
    public ApiResponse selectAll(Page<ToolMindmap> page, ToolMindmap query) {
        LambdaQueryWrapper<ToolMindmap> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ToolMindmap::getId, ToolMindmap::getTitle, ToolMindmap::getPublicId,
                ToolMindmap::getCreateTime, ToolMindmap::getUpdateTime);
        if (query != null && query.getTitle() != null && !query.getTitle().isBlank()) {
            wrapper.like(ToolMindmap::getTitle, query.getTitle().trim());
        }
        wrapper.orderByDesc(ToolMindmap::getUpdateTime);
        Page<ToolMindmap> result = this.toolMindmapService.page(page, wrapper);
        if (result.getRecords() != null) {
            result.getRecords().forEach(this::fillUrl);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("mindmap/{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        ToolMindmap entity = this.toolMindmapService.getById(id);
        if (entity == null) {
            return ApiResponse.failure("记录不存在");
        }
        fillUrl(entity);
        return ApiResponse.success(entity);
    }

    @PostMapping("mindmap/save")
    public ApiResponse save(@RequestBody MindmapSaveRequest req) {
        if (req == null || req.getMarkdown() == null || req.getMarkdown().isBlank()) {
            return ApiResponse.failure("请填写 Markdown 内容");
        }
        if (req.getImageBase64() == null || req.getImageBase64().isBlank()) {
            return ApiResponse.failure("缺少导图图片");
        }
        byte[] png;
        try {
            png = decodePng(req.getImageBase64());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure("图片数据无效");
        }
        if (png.length < 8 || png[0] != (byte) 0x89 || png[1] != 0x50) {
            return ApiResponse.failure("图片不是 PNG 格式");
        }

        Date now = DateUtils.getNow();
        ToolMindmap entity;
        boolean creating = req.getId() == null || req.getId().isBlank();
        if (creating) {
            entity = new ToolMindmap();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setPublicId(UUID.randomUUID().toString().replace("-", ""));
            entity.setCreateTime(now);
        } else {
            entity = this.toolMindmapService.getById(req.getId().trim());
            if (entity == null) {
                return ApiResponse.failure("记录不存在");
            }
        }
        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        if (title.isEmpty()) {
            title = defaultTitle(req.getMarkdown());
        }
        entity.setTitle(title);
        entity.setMarkdown(req.getMarkdown());
        entity.setUpdateTime(now);

        try {
            writePng(entity.getPublicId(), png);
        } catch (IOException e) {
            return ApiResponse.failure("保存图片失败：" + e.getMessage());
        }

        boolean ok;
        if (creating) {
            ok = this.toolMindmapService.save(entity);
            if (!ok) {
                return ApiResponse.failure("保存失败");
            }
        } else {
            LambdaUpdateWrapper<ToolMindmap> uw = new LambdaUpdateWrapper<>();
            uw.eq(ToolMindmap::getId, entity.getId())
                    .set(ToolMindmap::getTitle, title)
                    .set(ToolMindmap::getMarkdown, req.getMarkdown())
                    .set(ToolMindmap::getUpdateTime, now);
            this.toolMindmapService.update(uw);
        }
        fillUrl(entity);
        return ApiResponse.success(entity);
    }

    @PostMapping("mindmap/del")
    public ApiResponse delete(@RequestBody List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return ApiResponse.failure("请选择要删除的记录");
        }
        List<ToolMindmap> rows = this.toolMindmapService.listByIds(idList);
        for (ToolMindmap row : rows) {
            try {
                Files.deleteIfExists(pngPath(row.getPublicId()));
            } catch (IOException ignored) {
            }
        }
        return ApiResponse.success(this.toolMindmapService.removeByIds(idList));
    }

    /** 外访图片：复制链接后可直接打开对应 PNG。 */
    @GetMapping({"/public/mindmap/{publicId}.png", "/public/mindmap/{publicId}"})
    public ResponseEntity<byte[]> publicImage(@PathVariable String publicId) {
        if (publicId != null && publicId.endsWith(".png")) {
            publicId = publicId.substring(0, publicId.length() - 4);
        }
        if (publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return ResponseEntity.notFound().build();
        }
        ToolMindmap entity = this.toolMindmapService.getOne(
                new LambdaQueryWrapper<ToolMindmap>().eq(ToolMindmap::getPublicId, publicId));
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = pngPath(publicId);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            String etag = contentEtag(bytes);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .eTag(etag)
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .header("Surrogate-Control", "no-store")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + publicId + ".png\"")
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void fillUrl(ToolMindmap entity) {
        if (entity != null && entity.getPublicId() != null) {
            entity.setUrl("/public/mindmap/" + entity.getPublicId() + ".png");
        }
    }

    private Path pngPath(String publicId) {
        return Paths.get(storageDir).toAbsolutePath().normalize().resolve(publicId + ".png");
    }

    private void writePng(String publicId, byte[] png) throws IOException {
        Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = pngPath(publicId);
        Path tmp = dir.resolve(publicId + ".png.tmp");
        Files.write(tmp, png,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);
        try {
            Files.move(tmp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String contentEtag(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("MD5").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }

    private static byte[] decodePng(String imageBase64) {
        String raw = imageBase64.trim();
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma > 0) {
            raw = raw.substring(comma + 1);
        }
        return Base64.getDecoder().decode(raw);
    }

    private static String defaultTitle(String markdown) {
        for (String line : markdown.split("\n")) {
            String t = line.trim().replaceFirst("^#+\\s*", "");
            if (!t.isEmpty()) {
                return t.length() > 80 ? t.substring(0, 80) : t;
            }
        }
        return "未命名思维导图";
    }
}
