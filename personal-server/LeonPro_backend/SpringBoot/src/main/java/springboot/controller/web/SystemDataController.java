package springboot.controller.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import springboot.service.SystemDataPackService;
import springboot.utils.ApiResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 系统数据包：导出当前可随代码部署的配置与思维导图。
 */
@RestController
public class SystemDataController {

    @Autowired
    private SystemDataPackService systemDataPackService;

    @GetMapping("systemData/status")
    public ApiResponse status() {
        return ApiResponse.success(systemDataPackService.status());
    }

    @GetMapping("systemData/export")
    public void export(HttpServletResponse response) throws IOException {
        String filename = URLEncoder.encode("system-data.zip", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename);
        systemDataPackService.writeZip(response.getOutputStream());
        response.flushBuffer();
    }
}
