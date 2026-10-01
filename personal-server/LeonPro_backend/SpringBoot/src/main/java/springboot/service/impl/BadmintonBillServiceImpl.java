package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.BadmintonBill;
import springboot.mapper.BadmintonBillMapper;
import springboot.service.BadmintonBillService;

@Service
public class BadmintonBillServiceImpl extends ServiceImpl<BadmintonBillMapper, BadmintonBill>
        implements BadmintonBillService {
}
