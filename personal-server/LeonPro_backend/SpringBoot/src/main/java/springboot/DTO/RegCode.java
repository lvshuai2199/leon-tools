package springboot.DTO;

import lombok.Data;

@Data
public class RegCode {
    private String regCode;

    private Integer regCodeType;

    private String oneMonthValid;
    private String twoMonthValid;
    private String fourMonthValid;
    private String sixMonthValid;
    private String thirteenMonthValid;
    private String longTimeValid;

}
