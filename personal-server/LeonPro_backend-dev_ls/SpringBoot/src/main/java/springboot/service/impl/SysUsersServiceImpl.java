package springboot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import springboot.mapper.SysUsersMapper;
import springboot.service.SysUsersService;
import springboot.domain.SysUsers;
import org.springframework.stereotype.Service;

/**
* @author 13326
* @description 针对表【sys_users】的数据库操作Service实现
* @createDate 2024-12-05 17:21:19
*/
@Service
public class SysUsersServiceImpl extends ServiceImpl<SysUsersMapper, SysUsers>
    implements SysUsersService {

}




