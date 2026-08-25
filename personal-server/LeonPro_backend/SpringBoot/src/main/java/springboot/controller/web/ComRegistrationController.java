package springboot.controller.web;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import springboot.domain.ComRegistration;
import springboot.domain.SysInfo;
import springboot.enums.RegCodeType;
import springboot.enums.SysInfoType;
import springboot.service.ComRegistrationService;
import org.springframework.web.bind.annotation.*;
import springboot.service.SysInfoService;
import springboot.utils.ApiResponse;
import springboot.utils.DateUtils;

import java.io.Serializable;
import java.util.List;

/**
 * (ComRegistration)表控制层
 *
 * @author makejava
 * @since 2025-04-27 13:47:00
 */
@RestController
@RequestMapping("comRegistration")
public class ComRegistrationController {
    /**
     * 服务对象
     */
    @Autowired
    private ComRegistrationService comRegistrationService;

    @Autowired
    private SysInfoService sysInfoService;

    /**
     * 分页查询所有数据
     *
     * @param page            分页对象
     * @param comRegistration 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<ComRegistration> page, ComRegistration comRegistration) {
        return ApiResponse.success(this.comRegistrationService.page(page, new QueryWrapper<>(comRegistration)));
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.comRegistrationService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param comRegistration 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody ComRegistration comRegistration) {
        comRegistration.setCreateTime(DateUtils.getNow());


        boolean save = this.comRegistrationService.save(comRegistration);
        if (save){
            SysInfo sysInfo = new SysInfo();
            sysInfo.setInfoDes("您的申请已提交，申请编码为：" + comRegistration.getId() + "\\n" +
                    "类型：" + RegCodeType.getDescriptionByCode(comRegistration.getRegCodeType()));
            sysInfo.setInfoStatus(0);
            sysInfo.setUserId(comRegistration.getApplyId());
            sysInfo.setInfoType(2);
            sysInfo.setCreateTime(DateUtils.getNow());
            this.sysInfoService.save(sysInfo);
            return ApiResponse.success("申请提交成功");
        }else {
            return ApiResponse.failure("申请提交失败！！！");

        }
    }

    /**
     * 修改数据
     *
     * @param comRegistration 实体对象
     * @return 修改结果
     */
    @PostMapping ("update")
    public ApiResponse update(@RequestBody ComRegistration comRegistration) {
        return ApiResponse.success(this.comRegistrationService.updateById(comRegistration));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @PostMapping("del")
    public ApiResponse delete(@RequestParam("idList") List<Long> idList) {
        return ApiResponse.success(this.comRegistrationService.removeByIds(idList));
    }
}

