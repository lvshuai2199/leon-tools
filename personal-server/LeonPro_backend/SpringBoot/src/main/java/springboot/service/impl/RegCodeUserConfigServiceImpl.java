package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.RegCodeUserConfig;
import springboot.mapper.RegCodeUserConfigMapper;
import springboot.service.RegCodeUserConfigService;

@Service
public class RegCodeUserConfigServiceImpl extends ServiceImpl<RegCodeUserConfigMapper, RegCodeUserConfig>
        implements RegCodeUserConfigService {
}
