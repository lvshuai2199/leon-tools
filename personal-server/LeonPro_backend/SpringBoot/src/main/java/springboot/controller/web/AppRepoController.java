package springboot.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.domain.SysUsers;
import springboot.service.repo.RepoWatchService;
import springboot.utils.ApiResponse;

/**
 * 用户端仓库（/app/repos/**）。每人保存自己的 GitHub / Gitee 令牌。
 * 请求体里的字段名必须是 token，操作日志会按这个名字打码。
 */
@RestController
@RequestMapping("/app/repos")
public class AppRepoController {

    private final RepoWatchService repos;

    public AppRepoController(RepoWatchService repos) {
        this.repos = repos;
    }

    @GetMapping("/board")
    public ApiResponse board(@RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
                             HttpServletRequest request) {
        SysUsers user = repos.requireUser(request);
        return ApiResponse.success(repos.board(user.getId(), refresh));
    }

    @PostMapping("/token")
    public ApiResponse saveToken(@RequestBody TokenBody body, HttpServletRequest request) {
        SysUsers user = repos.requireUser(request);
        TokenBody req = body == null ? new TokenBody() : body;
        return ApiResponse.success(repos.saveToken(user.getId(), req.getProvider(), req.getToken()));
    }

    @PostMapping("/token/clear")
    public ApiResponse clearToken(@RequestBody TokenBody body, HttpServletRequest request) {
        SysUsers user = repos.requireUser(request);
        TokenBody req = body == null ? new TokenBody() : body;
        return ApiResponse.success(repos.clearToken(user.getId(), req.getProvider()));
    }

    @GetMapping("/{provider}/{owner}/{name}/branches")
    public ApiResponse branches(@PathVariable String provider, @PathVariable String owner, @PathVariable String name,
                                HttpServletRequest request) {
        SysUsers user = repos.requireUser(request);
        return ApiResponse.success(repos.branches(user.getId(), provider, owner, name));
    }

    @GetMapping("/{provider}/{owner}/{name}/commits")
    public ApiResponse commits(@PathVariable String provider, @PathVariable String owner, @PathVariable String name,
                               @RequestParam(name = "ref") String ref,
                               @RequestParam(name = "page", defaultValue = "1") int page,
                               HttpServletRequest request) {
        SysUsers user = repos.requireUser(request);
        return ApiResponse.success(repos.commits(user.getId(), provider, owner, name, ref, page));
    }

    @GetMapping("/{provider}/{owner}/{name}/commits/{sha}")
    public ApiResponse commit(@PathVariable String provider, @PathVariable String owner, @PathVariable String name,
                              @PathVariable String sha, HttpServletRequest request) {
        SysUsers user = repos.requireUser(request);
        return ApiResponse.success(repos.commit(user.getId(), provider, owner, name, sha));
    }

    @Data
    public static class TokenBody {
        private String provider;
        private String token;
    }
}
