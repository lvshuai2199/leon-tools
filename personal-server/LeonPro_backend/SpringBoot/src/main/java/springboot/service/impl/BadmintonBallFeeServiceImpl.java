package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.BadmintonBallFee;
import springboot.mapper.BadmintonBallFeeMapper;
import springboot.service.BadmintonBallFeeService;

@Service
public class BadmintonBallFeeServiceImpl extends ServiceImpl<BadmintonBallFeeMapper, BadmintonBallFee>
        implements BadmintonBallFeeService {
}
