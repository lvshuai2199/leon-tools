package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统操作日志：用户操作、关键参数、成败与异常，用于回溯与崩溃排查。
 */
@TableName(value = "sys_operation_log")
@Data
public class SysOperationLog implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String operatorId;

    private String operatorName;

    /** 业务模块，如用户管理、认证 */
    private String module;

    /** 动作摘要，如 POST /sysUsers/userSaveOrUpdate */
    private String action;

    private String requestMethod;

    private String requestUri;

    /** 脱敏后的查询串与请求体（截断） */
    private String requestParams;

    private String ip;

    private String userAgent;

    /** SUCCESS / FAIL / ERROR */
    private String status;

    private String resultMsg;

    private String errorMsg;

    private Long costMs;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
