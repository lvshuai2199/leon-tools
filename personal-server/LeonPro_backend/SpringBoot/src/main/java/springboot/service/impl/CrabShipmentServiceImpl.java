package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.CrabShipment;
import springboot.mapper.CrabShipmentMapper;
import springboot.service.CrabShipmentService;

@Service
public class CrabShipmentServiceImpl extends ServiceImpl<CrabShipmentMapper, CrabShipment>
        implements CrabShipmentService {
}
