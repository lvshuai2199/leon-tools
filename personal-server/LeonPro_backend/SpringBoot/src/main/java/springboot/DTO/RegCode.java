package springboot.DTO;

import lombok.Data;

@Data
public class RegCode {
    private String regCode;

    private Integer regCodeType;

    /** 注册码配置 ID（PC 生成页优先走配置，手机端仍可只传类型） */
    private String configId;

    /** 申请人姓名（可选，写入操作历史） */
    private String applyName;

    /** 公司名称（可选，写入操作历史） */
    private String company;

    /** 当前操作人用户 ID（为空则记录为未知人员） */
    private String applyId;

    private String oneMonthValid;
    private String twoMonthValid;
    private String fourMonthValid;
    private String sixMonthValid;
    private String thirteenMonthValid;
    private String longTimeValid;

}
