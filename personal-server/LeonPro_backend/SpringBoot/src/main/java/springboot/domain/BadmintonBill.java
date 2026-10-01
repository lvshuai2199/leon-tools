package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 羽毛球球局计费主表。
 */
@TableName(value = "badminton_bill")
@Data
public class BadmintonBill implements Serializable {

    @TableId(type = IdType.INPUT)
    private String id;

    /** yyyy-MM-dd */
    private String playDate;

    /** 可选标题，如「周五夜场」 */
    private String title;

    /** 球局参与人数 */
    private Integer participantCount;

    private BigDecimal courtTotal;

    private BigDecimal ballTotal;

    private BigDecimal grandTotal;

    private BigDecimal perPerson;

    private String remark;

    private String operatorId;

    private String operatorName;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private List<BadmintonCourtFee> courtItems;

    @TableField(exist = false)
    private List<BadmintonBallFee> ballItems;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
