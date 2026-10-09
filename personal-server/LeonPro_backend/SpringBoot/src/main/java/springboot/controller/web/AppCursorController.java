package springboot.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springboot.domain.SysUsers;
import springboot.service.cursor.CursorTaskService;
import springboot.utils.ApiResponse;

/**
 * 用户端 Cursor 任务（/app/cursor/**）。每人使用自己的 API Key，数据按登录用户隔离。
 */
@RestController
@RequestMapping("/app/cursor")
public class AppCursorController {

    private final CursorTaskService cursorTaskService;

    public AppCursorController(CursorTaskService cursorTaskService) {
        this.cursorTaskService = cursorTaskService;
    }

    @GetMapping("/board")
    public ApiResponse board(HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        return ApiResponse.success(cursorTaskService.board(user.getId()));
    }

    @PostMapping("/key")
    public ApiResponse saveKey(@RequestBody KeyBody body, HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        cursorTaskService.saveKey(user.getId(), body == null ? null : body.getApiKey());
        return ApiResponse.success(cursorTaskService.board(user.getId()));
    }

    @PostMapping("/key/clear")
    public ApiResponse clearKey(HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        cursorTaskService.clearKey(user.getId());
        return ApiResponse.success(cursorTaskService.board(user.getId()));
    }

    @GetMapping("/usage")
    public ApiResponse usage(HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        return ApiResponse.success(cursorTaskService.quota(user.getId()));
    }

    @GetMapping("/repos")
    public ApiResponse repos(HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        return ApiResponse.success(cursorTaskService.repositories(user.getId()));
    }

    @PostMapping("/tasks")
    public ApiResponse create(@RequestBody CreateBody body, HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        CreateBody req = body == null ? new CreateBody() : body;
        return ApiResponse.success(cursorTaskService.create(
                user.getId(), req.getPrompt(), req.getRepoUrl(), req.getStartingRef(), req.getAutoCreatePr()));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse detail(@PathVariable String id, HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        return ApiResponse.success(cursorTaskService.detail(user.getId(), id, true));
    }

    @PostMapping("/tasks/{id}/follow")
    public ApiResponse follow(@PathVariable String id, @RequestBody PromptBody body, HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        return ApiResponse.success(cursorTaskService.follow(user.getId(), id, body == null ? null : body.getPrompt()));
    }

    @PostMapping("/tasks/{id}/cancel")
    public ApiResponse cancel(@PathVariable String id, HttpServletRequest request) {
        SysUsers user = cursorTaskService.requireUser(request);
        return ApiResponse.success(cursorTaskService.cancel(user.getId(), id));
    }

    @Data
    public static class KeyBody {
        private String apiKey;
    }

    @Data
    public static class CreateBody {
        private String prompt;
        private String repoUrl;
        private String startingRef;
        private Boolean autoCreatePr;
    }

    @Data
    public static class PromptBody {
        private String prompt;
    }
}
