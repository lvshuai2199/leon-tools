package springboot.controller.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import springboot.service.CrabShipmentBizService;
import springboot.utils.ApiResponse;

/**
 * 螃蟹出货单条公开分享（免登录）。用户端分享页地址 /s/crab/{publicId} 调这个接口，
 * 返回的手机号、地址里的号码已脱敏。
 */
@RestController
public class PublicCrabShipmentController {

    private final CrabShipmentBizService bizService;

    public PublicCrabShipmentController(CrabShipmentBizService bizService) {
        this.bizService = bizService;
    }

    @GetMapping("/public/crabShipment/{publicId}")
    public ApiResponse publicView(@PathVariable String publicId) {
        return bizService.publicView(publicId);
    }
}
