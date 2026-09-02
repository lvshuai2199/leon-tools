package springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import springboot.DTO.SystemDataStatus;
import springboot.domain.RegCodeConfig;
import springboot.domain.ToolMindmap;
import springboot.service.RegCodeConfigService;
import springboot.service.SystemDataPackService;
import springboot.service.ToolMindmapService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class SystemDataPackServiceImpl implements SystemDataPackService {

    public static final String CLASSPATH_PACK = "seed/system-data.zip";
    public static final String DISK_PACK_RELATIVE = "seed/system-data.zip";

    private final ToolMindmapService toolMindmapService;
    private final RegCodeConfigService regCodeConfigService;
    private final JsonMapper mapper;

    @Value("${app.mindmap.storage-dir:./data/mindmap}")
    private String storageDir;

    public SystemDataPackServiceImpl(ToolMindmapService toolMindmapService,
                                     RegCodeConfigService regCodeConfigService,
                                     JsonMapper mapper) {
        this.toolMindmapService = toolMindmapService;
        this.regCodeConfigService = regCodeConfigService;
        this.mapper = mapper;
    }

    @Override
    public SystemDataStatus status() {
        SystemDataStatus s = new SystemDataStatus();
        s.setClasspathPath("src/main/resources/" + CLASSPATH_PACK);
        s.setDiskPath(Paths.get(DISK_PACK_RELATIVE).toAbsolutePath().normalize().toString());
        s.setPackOnClasspath(new ClassPathResource(CLASSPATH_PACK).exists());
        s.setPackOnDisk(Files.isRegularFile(Paths.get(DISK_PACK_RELATIVE)));
        s.setMindmapCount((int) toolMindmapService.count());
        s.setRegCodeConfigCount((int) regCodeConfigService.count());
        s.setStorageDir(Paths.get(storageDir).toAbsolutePath().normalize().toString());
        return s;
    }

    @Override
    public void writeZip(OutputStream out) throws IOException {
        List<RegCodeConfig> configs = regCodeConfigService.list();
        List<ToolMindmap> maps = toolMindmapService.list();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", 1);
        manifest.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        manifest.put("mindmapCount", maps.size());
        manifest.put("regCodeConfigCount", configs.size());

        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            putJson(zos, "manifest.json", manifest);
            putJson(zos, "reg_code_config.json", configs);
            putJson(zos, "tool_mindmap.json", maps);
            Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
            for (ToolMindmap m : maps) {
                if (m.getPublicId() == null || m.getPublicId().isBlank()) {
                    continue;
                }
                Path png = dir.resolve(m.getPublicId() + ".png");
                if (!Files.isRegularFile(png)) {
                    log.warn("导出思维导图缺图：{}", png);
                    continue;
                }
                zos.putNextEntry(new ZipEntry("mindmap/" + m.getPublicId() + ".png"));
                Files.copy(png, zos);
                zos.closeEntry();
            }
        }
    }

    @Override
    public String importPack(InputStream zipIn) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(zipIn, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) {
                    continue;
                }
                files.put(name, readAll(zis));
                zis.closeEntry();
            }
        }

        int configUpsert = 0;
        int mapUpsert = 0;
        int pngWrote = 0;

        byte[] configJson = files.get("reg_code_config.json");
        if (configJson != null) {
            List<RegCodeConfig> rows = mapper.readValue(configJson, new TypeReference<List<RegCodeConfig>>() {});
            if (rows != null) {
                for (RegCodeConfig row : rows) {
                    if (row.getId() == null || row.getId().isBlank()) {
                        continue;
                    }
                    if (regCodeConfigService.getById(row.getId()) != null) {
                        continue;
                    }
                    regCodeConfigService.save(row);
                    configUpsert++;
                }
            }
        }

        byte[] mapJson = files.get("tool_mindmap.json");
        if (mapJson != null) {
            List<ToolMindmap> rows = mapper.readValue(mapJson, new TypeReference<List<ToolMindmap>>() {});
            if (rows != null) {
                for (ToolMindmap row : rows) {
                    if (upsertMindmap(row)) {
                        mapUpsert++;
                    }
                }
            }
        }

        Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String name = e.getKey();
            if (!name.startsWith("mindmap/") || !name.endsWith(".png")) {
                continue;
            }
            String fileName = name.substring("mindmap/".length());
            if (fileName.contains("/") || !fileName.matches("[a-zA-Z0-9-]+\\.png")) {
                continue;
            }
            Path target = dir.resolve(fileName);
            if (Files.exists(target)) {
                continue;
            }
            Files.write(target, e.getValue());
            pngWrote++;
        }

        String msg = String.format("导入完成：注册码配置 %d 条，思维导图 %d 条，图片 %d 张",
                configUpsert, mapUpsert, pngWrote);
        log.info(msg);
        return msg;
    }

    /** 已有记录不覆盖，避免每次启动把「保存修改」冲掉。 */
    private boolean upsertMindmap(ToolMindmap row) {
        if (row.getId() == null || row.getId().isBlank()) {
            return false;
        }
        row.setUrl(null);
        if (toolMindmapService.getById(row.getId()) != null) {
            return false;
        }
        if (row.getPublicId() != null && !row.getPublicId().isBlank()) {
            long n = toolMindmapService.count(
                    new LambdaQueryWrapper<ToolMindmap>().eq(ToolMindmap::getPublicId, row.getPublicId()));
            if (n > 0) {
                return false;
            }
        }
        return toolMindmapService.save(row);
    }

    private void putJson(ZipOutputStream zos, String name, Object value) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        zos.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        return buf.toByteArray();
    }
}
