package springboot.DTO;

import lombok.Data;

/**
 * 系统数据包状态，供首页展示。
 */
@Data
public class SystemDataStatus {

    /** classpath 是否已有 seed/system-data.zip */
    private boolean packOnClasspath;

    /** 运行目录 seed/system-data.zip 是否存在 */
    private boolean packOnDisk;

    private String classpathPath;

    private String diskPath;

    private int mindmapCount;

    private int regCodeConfigCount;

    private String storageDir;
}
