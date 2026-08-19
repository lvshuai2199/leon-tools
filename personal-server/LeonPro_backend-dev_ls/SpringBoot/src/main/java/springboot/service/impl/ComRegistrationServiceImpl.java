package springboot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import springboot.domain.ComRegistration;
import springboot.service.ComRegistrationService;
import springboot.mapper.ComRegistrationMapper;
import org.springframework.stereotype.Service;

/**
* @author 13326
* @description 针对表【com_registration】的数据库操作Service实现
* @createDate 2025-04-27 13:40:31
*/
@Service
public class ComRegistrationServiceImpl extends ServiceImpl<ComRegistrationMapper, ComRegistration>
    implements ComRegistrationService{

}




