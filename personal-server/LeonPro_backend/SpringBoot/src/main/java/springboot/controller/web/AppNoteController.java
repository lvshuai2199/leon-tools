package springboot.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.notes.NoteSyncException;
import springboot.service.NoteAccess;
import springboot.service.NoteSourceService;
import springboot.utils.ApiResponse;

import java.util.Map;

/**
 * 用户端笔记（/app/notes/**）：阅读仓库里的 Markdown，随手记先缓存，写完再上传到当前分支。
 */
@RestController
@RequestMapping("/app/notes")
public class AppNoteController {

    private final NoteSourceService notes;
    private final NoteAccess noteAccess;

    public AppNoteController(NoteSourceService notes, NoteAccess noteAccess) {
        this.notes = notes;
        this.noteAccess = noteAccess;
    }

    @GetMapping("/source")
    public ApiResponse source(HttpServletRequest request) {
        noteAccess.requireApp(request);
        return ApiResponse.success(notes.sourceView());
    }

    @GetMapping("/docs")
    public ApiResponse docs(HttpServletRequest request) {
        noteAccess.requireApp(request);
        return ApiResponse.success(notes.listDocs());
    }

    @GetMapping("/doc")
    public ApiResponse doc(@RequestParam("path") String path, HttpServletRequest request) {
        noteAccess.requireApp(request);
        try {
            return ApiResponse.success(notes.readDoc(path));
        } catch (IllegalArgumentException e) {
            return ApiResponse.withStatus(404, e.getMessage(), null);
        }
    }

    @GetMapping("/asset")
    public ResponseEntity<?> asset(@RequestParam("path") String path, HttpServletRequest request) {
        noteAccess.requireApp(request);
        try {
            return AdminNoteController.assetResponse(notes.readAsset(path));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.withStatus(404, e.getMessage(), null));
        }
    }

    @GetMapping("/drafts")
    public ApiResponse drafts(HttpServletRequest request) {
        noteAccess.requireApp(request);
        return ApiResponse.success(notes.listDrafts());
    }

    @GetMapping("/draft")
    public ApiResponse draft(@RequestParam("id") String id, HttpServletRequest request) {
        noteAccess.requireApp(request);
        try {
            return ApiResponse.success(notes.readDraft(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.withStatus(404, e.getMessage(), null);
        }
    }

    @PostMapping("/draft")
    public ApiResponse saveDraft(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        noteAccess.requireApp(request);
        try {
            return ApiResponse.success(notes.saveDraft(AdminNoteController.str(body, "id"),
                    AdminNoteController.str(body, "title"), AdminNoteController.str(body, "content")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("/draft/delete")
    public ApiResponse deleteDraft(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        noteAccess.requireApp(request);
        try {
            notes.deleteDraft(AdminNoteController.str(body, "id"));
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }

    @PostMapping("/draft/upload")
    public ApiResponse uploadDraft(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        noteAccess.requireApp(request);
        try {
            return ApiResponse.success(notes.uploadDraft(AdminNoteController.str(body, "id")));
        } catch (IllegalArgumentException | NoteSyncException e) {
            return ApiResponse.failure(e.getMessage());
        }
    }
}
