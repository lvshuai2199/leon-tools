package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * (ComRegistration)实体类
 *
 * @author makejava
 * @since 2025-04-27 13:42:33
 */
@TableName(value ="com_registration")
@Data
public class ComRegistration implements Serializable {

    /**
     *
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String applyName;
    
    private String company;
    
    private String salesName;
    
    private String applyPhone;
    
    private String regCode;

    private Integer regCodeType;

    private String remarks;

    private String oneMonthValid;

    private String longTimeValid;

    private String applyId;

    /** 操作人员（用户 ID；无用户 ID 时记为「未知人员」） */
    private String operator;

    private Date createTime;

    private Integer applyStatus;

    private static final long serialVersionUID = 577057650298768209L;


}

