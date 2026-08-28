package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;

import lombok.Data;

/**
 * @TableName sys_info
 */
@TableName(value ="sys_info")
@Data
public class SysInfo implements Serializable {

    /**
     *
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String infoDes;

    private Integer infoStatus;

    private String publicId;

    private String userId;

    private Integer infoType;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}