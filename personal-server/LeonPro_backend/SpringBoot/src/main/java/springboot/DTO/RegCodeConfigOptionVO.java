package springboot.DTO;

import lombok.Data;
import springboot.domain.RegCodeConfig;

import java.util.Date;

/**
 * 生成页用的注册码配置（/common/regCodeConfig/list）：刻意不含 encryptSuffix、encryptType，
 * 生成时由后端按 configId 取后缀计算。
 */
@Data
public class RegCodeConfigOptionVO {
    private String id;
    private String company;
    private String name;
    private String componentName;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;

    public static RegCodeConfigOptionVO of(RegCodeConfig c) {
        RegCodeConfigOptionVO vo = new RegCodeConfigOptionVO();
        vo.setId(c.getId());
        vo.setCompany(c.getCompany());
        vo.setName(c.getName());
        vo.setComponentName(c.getComponentName());
        vo.setSortOrder(c.getSortOrder());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}
