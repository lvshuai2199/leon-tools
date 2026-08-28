package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.RegCodeConfig;
import springboot.mapper.RegCodeConfigMapper;
import springboot.service.RegCodeConfigService;

@Service
public class RegCodeConfigServiceImpl extends ServiceImpl<RegCodeConfigMapper, RegCodeConfig>
        implements RegCodeConfigService {
}
