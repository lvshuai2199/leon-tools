package springboot.controller.Extern;




import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import springboot.domain.ExternWallet;
import springboot.service.ExternWalletService;
import springboot.utils.ApiResponse;

import java.io.Serializable;
import java.util.List;

/**
 * (ExternWallet)表控制层
 *
 * @author makejava
 * @since 2025-09-08 22:36:12
 */
@RestController
@RequestMapping("externWallet")
@Slf4j
public class ExternWalletController {
    /**
     * 服务对象
     */
    @Autowired
    private ExternWalletService externWalletService;

    /**
     * 分页查询所有数据
     *
     * @param page         分页对象
     * @param externWallet 查询实体
     * @return 所有数据
     */
    @GetMapping("getAll")
    public ApiResponse selectAll(Page<ExternWallet> page, ExternWallet externWallet) {
        return ApiResponse.success(this.externWalletService.page(page, new QueryWrapper<>(externWallet)));
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ApiResponse selectOne(@PathVariable Serializable id) {
        return ApiResponse.success(this.externWalletService.getById(id));
    }

    /**
     * 新增数据
     *
     * @param externWallet 实体对象
     * @return 新增结果
     */
    @PostMapping("add")
    public ApiResponse insert(@RequestBody ExternWallet externWallet) {
        return ApiResponse.success(this.externWalletService.save(externWallet));
    }

    /**
     * 修改数据
     *
     * @param externWallet 实体对象
     * @return 修改结果
     */
    @PostMapping("update")
    public ApiResponse update(@RequestBody ExternWallet externWallet) {
        return ApiResponse.success(this.externWalletService.updateById(externWallet));
    }

    /**
     * 删除数据
     *
     * @param idList 主键结合
     * @return 删除结果
     */
    @PostMapping("del")
    public ApiResponse delete(@RequestParam("idList") List<Long> idList) {
        return ApiResponse.success(this.externWalletService.removeByIds(idList));
    }
}

