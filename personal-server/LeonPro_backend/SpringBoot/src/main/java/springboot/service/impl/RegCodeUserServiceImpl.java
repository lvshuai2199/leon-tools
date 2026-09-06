package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.RegCodeUser;
import springboot.mapper.RegCodeUserMapper;
import springboot.service.RegCodeUserService;

@Service
public class RegCodeUserServiceImpl extends ServiceImpl<RegCodeUserMapper, RegCodeUser>
        implements RegCodeUserService {
}
