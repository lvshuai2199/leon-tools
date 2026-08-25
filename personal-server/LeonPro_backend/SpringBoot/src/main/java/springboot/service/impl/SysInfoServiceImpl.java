package springboot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import springboot.domain.SysInfo;
import springboot.service.SysInfoService;
import springboot.mapper.SysInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author 13326
* @description 针对表【sys_info】的数据库操作Service实现
* @createDate 2025-05-08 22:09:58
*/
@Service
public class SysInfoServiceImpl extends ServiceImpl<SysInfoMapper, SysInfo>
    implements SysInfoService{

}




