package springboot.controller.Extern;



import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import springboot.DTO.TaskDto;
import springboot.domain.ExternAccounts;
import springboot.domain.SysMenus;
import springboot.domain.SysTasks;
import springboot.service.ExternAccountsService;
import springboot.utils.ApiResponse;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.List;

/**
 * (ExternAccounts)表控制层
 *
 * @author makejava
 * @since 2025-09-08 21:02:51
 */
@RestController
@RequestMapping("externAccounts")
public class ExternAccountsController {
    /**
     * 服务对象
     */
    @Autowired
    private ExternAccountsService externAccountsService;

    /**
     * 分页查询所有数据
     *
     * @param page           分页对象
     * @param externAccounts 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<ExternAccounts> page, ExternAccounts externAccounts) {
        return ApiResponse.success(this.externAccountsService.page(page, new QueryWrapper<>(externAccounts)));
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.externAccountsService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param externAccounts 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody ExternAccounts externAccounts) {
        return ApiResponse.success(this.externAccountsService.save(externAccounts));
    }

    /**
     * 修改数据
     *
     * @param externAccounts 实体对象
     * @return 修改结果
     */
    @PostMapping("update")
    public ApiResponse update(@RequestBody ExternAccounts externAccounts) {
        return ApiResponse.success(this.externAccountsService.updateById(externAccounts));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @PostMapping("del")
    public ApiResponse delete(@RequestParam("idList") List<Long> idList) {
        return ApiResponse.success(this.externAccountsService.removeByIds(idList));
    }


}

