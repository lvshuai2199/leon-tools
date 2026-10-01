package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.BadmintonCourtFee;
import springboot.mapper.BadmintonCourtFeeMapper;
import springboot.service.BadmintonCourtFeeService;

@Service
public class BadmintonCourtFeeServiceImpl extends ServiceImpl<BadmintonCourtFeeMapper, BadmintonCourtFee>
        implements BadmintonCourtFeeService {
}
