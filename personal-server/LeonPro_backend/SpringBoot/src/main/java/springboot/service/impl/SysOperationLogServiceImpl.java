package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import springboot.domain.SysOperationLog;
import springboot.mapper.SysOperationLogMapper;
import springboot.service.SysOperationLogService;

@Slf4j
@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog>
        implements SysOperationLogService {

    @Override
    public void saveQuietly(SysOperationLog record) {
        try {
            save(record);
        } catch (Exception e) {
            log.warn("写入操作日志失败: {}", e.getMessage());
        }
    }
}
