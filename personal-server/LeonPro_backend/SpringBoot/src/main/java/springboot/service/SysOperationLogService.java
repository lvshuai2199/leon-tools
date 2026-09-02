package springboot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import springboot.domain.SysOperationLog;

public interface SysOperationLogService extends IService<SysOperationLog> {

    void saveQuietly(SysOperationLog log);
}
