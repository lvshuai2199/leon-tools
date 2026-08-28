package springboot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value ="sys_tasks")
@Data
public class SysTasks implements Serializable {
    /**
     *
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 任务类别
     */
    private String taskType;

    /**
     * 任务名称
     */
    private String taskName;


    /**
     * 描述
     */
    private String description;


    /**
     * 任务等级
     */
    private String taskLevel;

    /**
     * 任务优先级
     */
    private String taskStatus;

    /**
     * 任务发布用户id
     */
    private String publisherId;

    /**
     * 处理人
     */
    private String handlerId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建时间
     */
    private Date updateTime;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 客户地址
     */
    private String customerPlace;

    /**
     * 所属行业
     */
    private String industry;

    /**
     * 场景
     */
    private String scenario;

    /**
     * 机械臂型号
     */
    private String robotType;

    /**
     * 机械臂数量
     */
    private String robotNum;

    /**
     * 备注，异常情况信息填写
     */
    private String remarks;

    /**
     * 任务是否删除
     */
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;


}