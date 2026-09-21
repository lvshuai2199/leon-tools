package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 螃蟹每日出货单。
 */
@TableName(value = "crab_shipment")
@Data
public class CrabShipment implements Serializable {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 当天序号 */
    private Integer seqNo;

    private String customerName;

    private String phone;

    private String address;

    /** 规格，如 3.5母 */
    private String spec;

    /** 数量（只） */
    private Integer quantity;

    /** 0 未付款 1 已付款 */
    private Integer paid;

    /** 0 未发货 1 已发货 */
    private Integer shipped;

    private String trackingNo;

    /** yyyy-MM-dd */
    private String shipDate;

    private String remark;

    /** 对外分享标识 */
    private String publicId;

    private String operatorId;

    private String operatorName;

    private Date createTime;

    private Date updateTime;

    /** 可复制的相对路径，不入库 */
    @TableField(exist = false)
    private String sharePath;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
