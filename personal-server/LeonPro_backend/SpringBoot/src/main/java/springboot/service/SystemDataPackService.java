package springboot.service;

import springboot.DTO.SystemDataStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface SystemDataPackService {

    SystemDataStatus status();

    void writeZip(OutputStream out) throws IOException;

    /** 导入数据包：按 id 覆盖/新增，不删除包外已有数据。返回说明日志。 */
    String importPack(InputStream zipIn) throws IOException;
}
