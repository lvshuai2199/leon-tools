package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springboot.domain.CrabShipment;
import springboot.domain.SysUsers;
import springboot.service.CrabOrderParser;
import springboot.service.CrabShipmentService;
import springboot.service.SysUsersService;
import springboot.utils.DateUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信公众号服务器回调：把收到的文本按螃蟹出货单解析并入库。
 * 公众号后台 URL 填 https://域名/prod-api/wechat/oa ，Token 与配置一致。
 */
@RestController
@RequestMapping("wechat/oa")
public class WechatOaController {

    private final CrabShipmentService crabShipmentService;
    private final SysUsersService sysUsersService;
    private final boolean enabled;
    private final String token;
    private final String operatorUsername;

    public WechatOaController(CrabShipmentService crabShipmentService,
                              SysUsersService sysUsersService,
                              @Value("${wechat.enabled:false}") boolean enabled,
                              @Value("${wechat.token:}") String token,
                              @Value("${wechat.crab-operator-username:admin}") String operatorUsername) {
        this.crabShipmentService = crabShipmentService;
        this.sysUsersService = sysUsersService;
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.operatorUsername = operatorUsername;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@RequestParam(value = "signature", required = false) String signature,
                         @RequestParam(value = "timestamp", required = false) String timestamp,
                         @RequestParam(value = "nonce", required = false) String nonce,
                         @RequestParam(value = "echostr", required = false) String echostr) {
        if (!enabled || token.isEmpty() || !signOk(signature, timestamp, nonce)) {
            return "disabled";
        }
        return echostr == null ? "" : echostr;
    }

    @PostMapping(consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public String receive(@RequestBody String body,
                          @RequestParam(value = "signature", required = false) String signature,
                          @RequestParam(value = "timestamp", required = false) String timestamp,
                          @RequestParam(value = "nonce", required = false) String nonce) {
        if (!enabled || token.isEmpty() || !signOk(signature, timestamp, nonce)) {
            return reply("", "", "公众号出货未启用");
        }
        String toUser = xml(body, "ToUserName");
        String fromUser = xml(body, "FromUserName");
        String msgType = xml(body, "MsgType");
        if (!"text".equalsIgnoreCase(msgType)) {
            return reply(fromUser, toUser, "请直接发送出货文本，例如：张三 13800001111 阳江 3两母 20只");
        }
        String content = xml(body, "Content");
        List<CrabShipment> parsed = CrabOrderParser.parse(content);
        if (parsed.isEmpty()) {
            return reply(fromUser, toUser, "没有识别到出货单。请带上姓名或手机号，规格用「3两母」，数量用「20只」。");
        }
        int saved = saveRows(parsed);
        StringBuilder msg = new StringBuilder("已录入 ").append(saved).append(" 条\n");
        for (CrabShipment row : parsed) {
            msg.append(nullToDash(row.getCustomerName())).append(" ")
                    .append(nullToDash(row.getSpec())).append(" ")
                    .append(row.getQuantity() == null ? "-" : row.getQuantity() + "只")
                    .append("\n");
        }
        return reply(fromUser, toUser, msg.toString().trim());
    }

    private int saveRows(List<CrabShipment> parsed) {
        SysUsers operator = findOperator();
        String shipDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        int seq = nextSeq(shipDate);
        Date now = DateUtils.getNow();
        int count = 0;
        for (CrabShipment item : parsed) {
            if (item.getCustomerName() == null && item.getPhone() == null) {
                continue;
            }
            CrabShipment entity = new CrabShipment();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setPublicId(UUID.randomUUID().toString().replace("-", ""));
            entity.setSeqNo(item.getSeqNo() != null ? item.getSeqNo() : seq++);
            entity.setCustomerName(item.getCustomerName());
            entity.setPhone(item.getPhone());
            entity.setAddress(item.getAddress());
            entity.setSpec(item.getSpec());
            entity.setQuantity(item.getQuantity());
            entity.setPaid(0);
            entity.setShipped(0);
            entity.setShipDate(shipDate);
            entity.setRemark("微信公众号");
            if (operator != null) {
                entity.setOperatorId(operator.getId());
                entity.setOperatorName(operator.getNickname() != null ? operator.getNickname() : operator.getUsername());
            } else {
                entity.setOperatorName("微信公众号");
            }
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            this.crabShipmentService.save(entity);
            count++;
        }
        return count;
    }

    private SysUsers findOperator() {
        if (operatorUsername == null || operatorUsername.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<SysUsers> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUsers::getUsername, operatorUsername.trim());
        return this.sysUsersService.getOne(wrapper, false);
    }

    private int nextSeq(String shipDate) {
        CrabShipment last = this.crabShipmentService.getOne(
                new LambdaQueryWrapper<CrabShipment>()
                        .eq(CrabShipment::getShipDate, shipDate)
                        .orderByDesc(CrabShipment::getSeqNo)
                        .last("LIMIT 1"),
                false);
        return last == null || last.getSeqNo() == null ? 1 : last.getSeqNo() + 1;
    }

    private boolean signOk(String signature, String timestamp, String nonce) {
        if (signature == null || timestamp == null || nonce == null) {
            return false;
        }
        String[] arr = {token, timestamp, nonce};
        Arrays.sort(arr);
        return signature.equalsIgnoreCase(sha1(arr[0] + arr[1] + arr[2]));
    }

    private static String sha1(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String xml(String body, String tag) {
        if (body == null) {
            return "";
        }
        Matcher cdata = Pattern.compile("<" + tag + "><!\\[CDATA\\[(.*?)]]></" + tag + ">", Pattern.DOTALL).matcher(body);
        if (cdata.find()) {
            return cdata.group(1).trim();
        }
        Matcher plain = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">").matcher(body);
        return plain.find() ? plain.group(1).trim() : "";
    }

    private static String reply(String to, String from, String content) {
        long now = System.currentTimeMillis() / 1000;
        return "<xml>"
                + "<ToUserName><![CDATA[" + nullToEmpty(to) + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + nullToEmpty(from) + "]]></FromUserName>"
                + "<CreateTime>" + now + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + nullToEmpty(content) + "]]></Content>"
                + "</xml>";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
