package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.DTO.TaskDto;
import springboot.domain.SysTasks;
import springboot.domain.SysUsers;
import springboot.service.SysTasksService;
import springboot.utils.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import springboot.utils.DateUtils;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * (SysTasks)表控制层
 *
 * @author makejava
 * @since 2025-03-05 13:28:30
 */
@RestController
@RequestMapping("sysTasks")
public class SysTasksController {
    /**
     * 服务对象
     */
    @Autowired
    private SysTasksService sysTasksService;

    /**
     * 分页查询所有数据
     *
     * @param page 分页对象
     * @param taskDto 查询实体
     * @return 所有数据
     */
//    @GetMapping("getAll")
//    public ApiResponse selectAll(Page<SysTasks> page, SysTasks taskDto) {
//        return ApiResponse.success(this.sysTasksService.page(page, new QueryWrapper<>(taskDto)));
//    }


    @GetMapping("getAll")
    public ApiResponse selectAll(Page<SysTasks> page, TaskDto taskDto) {
        LambdaQueryWrapper<SysTasks> queryWrapper = new LambdaQueryWrapper<>();

        if (taskDto.getId() != null) {
            queryWrapper.eq(SysTasks::getId, taskDto.getId());
        }else {

            if (StringUtils.isNotBlank(taskDto.getPublisherId())) {
                queryWrapper.eq(SysTasks::getPublisherId,taskDto.getPublisherId());
            }else {
                return ApiResponse.failure("请先登录！！！");
            }

            // 处理 isDelete 字段
            if (taskDto.getIsDelete() != null && taskDto.getIsDelete() != 0) {
                queryWrapper.eq(SysTasks::getIsDelete, 1);
            }else {
                queryWrapper.ne(SysTasks::getIsDelete, 1);
            }

            // 处理 taskName 字段
            if (StringUtils.isNotBlank(taskDto.getTaskName())) {
                queryWrapper.like(SysTasks::getTaskName, taskDto.getTaskName());
            }

            // 处理 taskType 字段
            if (StringUtils.isNotBlank(taskDto.getTaskType())) {
                queryWrapper.eq(SysTasks::getTaskType, taskDto.getTaskType());
            }

            // 处理 taskLevel 字段
            if (StringUtils.isNotBlank(taskDto.getTaskLevel())) {
                queryWrapper.eq(SysTasks::getTaskLevel, taskDto.getTaskLevel());
            }

            // 处理 taskStatus 字段
            if (StringUtils.isNotBlank(taskDto.getTaskStatus())) {
                queryWrapper.eq(SysTasks::getTaskStatus, taskDto.getTaskStatus());
            }

            // 处理 publisherId 字段
            if (StringUtils.isNotBlank(taskDto.getPublisherId())) {
                queryWrapper.eq(SysTasks::getPublisherId, taskDto.getPublisherId());
            }

            // 处理 handlerId 字段
            if (StringUtils.isNotBlank(taskDto.getHandlerId())) {
                queryWrapper.eq(SysTasks::getHandlerId, taskDto.getHandlerId());
            }

            // 处理 customerName 字段
            if (StringUtils.isNotBlank(taskDto.getCustomerName())) {
                queryWrapper.eq(SysTasks::getCustomerName, taskDto.getCustomerName());
            }

            // 处理 customerPlace 字段
            if (StringUtils.isNotBlank(taskDto.getCustomerPlace())) {
                queryWrapper.eq(SysTasks::getCustomerPlace, taskDto.getCustomerPlace());
            }

            // 处理 industry 字段
            if (StringUtils.isNotBlank(taskDto.getIndustry())) {
                queryWrapper.eq(SysTasks::getIndustry, taskDto.getIndustry());
            }

            // 处理 scenario 字段
            if (StringUtils.isNotBlank(taskDto.getScenario())) {
                queryWrapper.eq(SysTasks::getScenario, taskDto.getScenario());
            }

            // 处理日期字段
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (StringUtils.isNotBlank(taskDto.getStartTime())) {
                LocalDate startDate = LocalDate.parse(taskDto.getStartTime(), formatter);
                queryWrapper.ge(SysTasks::getCreateTime, startDate); // 任务开始时间大于或等于 startDate
            }

            if (StringUtils.isNotBlank(taskDto.getEndTime())) {
                LocalDate endDate = LocalDate.parse(taskDto.getEndTime(), formatter);
                queryWrapper.le(SysTasks::getCreateTime, endDate); // 任务结束时间小于或等于 endDate
            }
        }

        // 执行查询并获取结果
        Page<SysTasks> resultPage = this.sysTasksService.page(page, queryWrapper);

        return ApiResponse.success(resultPage);
    }


    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.sysTasksService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param sysTasks 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody SysTasks sysTasks) {
        // 设置当前时间
        sysTasks.setCreateTime(DateUtils.getNow()); // 获取当前时间并设置

        sysTasks.setIsDelete(0);

        // 调用服务层保存任务并返回结果
        boolean isSaved = this.sysTasksService.save(sysTasks);
        return ApiResponse.success(isSaved);
    }
    /**
     * 修改数据
     *
     * @param sysTasks 实体对象
     * @return 修改结果
     */
    @PostMapping ("update")
    public ApiResponse update(@RequestBody SysTasks sysTasks) {
        sysTasks.setUpdateTime(DateUtils.getNow());
        return ApiResponse.success(this.sysTasksService.updateById(sysTasks));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @DeleteMapping("del")
    public ApiResponse delete(@RequestParam("idList") List<Long> idList) {
        return ApiResponse.success(this.sysTasksService.removeByIds(idList));
    }
}

