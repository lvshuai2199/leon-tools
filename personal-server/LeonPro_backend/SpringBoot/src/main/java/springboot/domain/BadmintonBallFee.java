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

    private BigDecimal amount;

    private Integer sortOrder;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
