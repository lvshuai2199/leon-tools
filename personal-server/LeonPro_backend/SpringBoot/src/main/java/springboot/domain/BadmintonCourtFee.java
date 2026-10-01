package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 场地费明细：场地数量 × 时长(小时) × 单价。
 */
@TableName(value = "badminton_court_fee")
@Data
public class BadmintonCourtFee implements Serializable {

    @TableId(type = IdType.INPUT)
    private String id;

    private String billId;

    /** 场地数量（片） */
    private Integer courtCount;

    /** 时长（小时，支持小数） */
    private BigDecimal hours;

    /** 单价（元/片/小时） */
    private BigDecimal unitPrice;

    /** 小计 */
    private BigDecimal amount;

    /** 可选备注，如场地名或 19:00-21:00 */
    private String remark;

    private Integer sortOrder;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
