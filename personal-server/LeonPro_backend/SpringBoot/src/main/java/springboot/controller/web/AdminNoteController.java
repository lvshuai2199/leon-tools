package springboot.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.notes.NoteSyncException;
import springboot.service.NoteSourceService;
import springboot.utils.ApiResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 管理端笔记：设置仓库和分支、看同步状态、阅读 Markdown，以及随手记缓存 / 上传。
 * 权限由 AdminAuthInterceptor 按 component tool/notes/index 校验。
 */
@RestController
public class AdminNoteController {

    private final NoteSourceService notes;

    public AdminNoteController(NoteSourceService notes) {
        this.notes = notes;
    }

    @GetMapping("/admin/notes/source")
    public ApiResponse source(HttpServletRequest request) {
        return ApiResponse.success(notes.sourceView());
    }

    @PostMapping("/admin/notes/source")
    public ApiResponse saveSource(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            notes.save(str(body, "repoUrl"), str(body, "branch"), str(body, "accessToken"), bool(body, "clearToken"));
            return ApiResponse.success(notes.sourceView());
        } catch (IllegalArgumentException | NoteSyncException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("/admin/notes/branches")
    public ApiResponse branches(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ApiResponse.success(notes.listBranches(str(body, "repoUrl"), str(body, "accessToken")));
        } catch (IllegalArgumentException | NoteSyncException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("/admin/notes/sync")
    public ApiResponse sync(HttpServletRequest request) {
        notes.syncAsync();
        return ApiResponse.success(notes.sourceView());
    }

    @GetMapping("/admin/notes/docs")
    public ApiResponse docs(HttpServletRequest request) {
        return ApiResponse.success(notes.listDocs());
    }

    @GetMapping("/admin/notes/doc")
    public ApiResponse doc(@RequestParam("path") String path, HttpServletRequest request) {
        try {
            return ApiResponse.success(notes.readDoc(path));
        } catch (IllegalArgumentException e) {
            return ApiResponse.withStatus(404, e.getMessage(), null);
        }
    }

    @GetMapping("/admin/notes/asset")
    public ResponseEntity<?> asset(@RequestParam("path") String path, HttpServletRequest request) {
        try {
            return assetResponse(notes.readAsset(path));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.withStatus(404, e.getMessage(), null));
        }
    }

    @GetMapping("/admin/notes/drafts")
    public ApiResponse drafts(HttpServletRequest request) {
        return ApiResponse.success(notes.listDrafts());
    }

    @GetMapping("/admin/notes/draft")
    public ApiResponse draft(@RequestParam("id") String id, HttpServletRequest request) {
        try {
            return ApiResponse.success(notes.readDraft(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.withStatus(404, e.getMessage(), null);
        }
    }

    @PostMapping("/admin/notes/draft")
    public ApiResponse saveDraft(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ApiResponse.success(notes.saveDraft(str(body, "id"), str(body, "title"), str(body, "content")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("/admin/notes/draft/delete")
    public ApiResponse deleteDraft(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            notes.deleteDraft(str(body, "id"));
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("/admin/notes/draft/upload")
    public ApiResponse uploadDraft(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ApiResponse.success(notes.uploadDraft(str(body, "id")));
        } catch (IllegalArgumentException | NoteSyncException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    static ResponseEntity<byte[]> assetResponse(NoteSourceService.NoteAsset asset) {
        ContentDisposition disposition = ContentDisposition.builder(asset.inline() ? "inline" : "attachment")
                .filename(asset.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.bytes());
    }

    static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return "";
        }
        return String.valueOf(body.get(key));
    }

    static boolean bool(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return false;
        }
        Object v = body.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }
}
