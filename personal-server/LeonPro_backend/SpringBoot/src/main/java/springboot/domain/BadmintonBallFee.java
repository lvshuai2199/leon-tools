package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用球费用明细：品牌 × 数量 × 单价。
 * <p>
 * 填了整桶价（bucketPrice &gt; 0）时：单价 = round2(整桶价 / 12)，小计 = round2(整桶价 / 12 × 数量)，
 * 见 {@link springboot.service.BadmintonBilling}。整桶价为空或 0 表示没填，按单价算。
 */
@TableName(value = "badminton_ball_fee")
@Data
public class BadmintonBallFee implements Serializable {

    @TableId(type = IdType.INPUT)
    private String id;

    private String billId;

    /** 用球品牌 */
    private String brand;

    private Integer quantity;

    private BigDecimal unitPrice;

    /** 整桶价格（一桶 12 个），可为空；空或 0 = 没填，单价手填 */
    private BigDecimal bucketPrice;

    private BigDecimal amount;

    private Integer sortOrder;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
