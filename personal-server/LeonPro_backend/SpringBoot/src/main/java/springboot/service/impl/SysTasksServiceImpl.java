package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;

import springboot.domain.SysTasks;
import springboot.mapper.SysTasksMapper;
import springboot.service.SysTasksService;
import org.springframework.stereotype.Service;

/**
* @author 13326
* @description 针对表【sys_tasks】的数据库操作Service实现
* @createDate 2025-03-05 13:11:13
*/
@Service
public class SysTasksServiceImpl extends ServiceImpl<SysTasksMapper, SysTasks>
    implements SysTasksService {

}




