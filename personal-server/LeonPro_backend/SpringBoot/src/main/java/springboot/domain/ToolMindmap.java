package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 思维导图存储：Markdown 源码 + 可外访的本地 PNG 链接。
 */
@TableName(value = "tool_mindmap")
@Data
public class ToolMindmap implements Serializable {

    @TableId(type = IdType.INPUT)
    private String id;

    private String title;

    private String markdown;

    /** 对外访问标识，对应 /public/mindmap/{publicId}.png */
    private String publicId;

    private Date createTime;

    private Date updateTime;

    /** 可复制的相对路径，不入库 */
    @TableField(exist = false)
    private String url;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
