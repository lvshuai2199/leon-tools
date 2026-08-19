package springboot.DTO;

import lombok.Data;


@Data
public class TaskDto {

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
     * 任务状态
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
     * 任务是否删除
     */
    private Integer isDelete;


    /**
     * 创建时间
     */
    private String startTime;

    private String endTime;

}
