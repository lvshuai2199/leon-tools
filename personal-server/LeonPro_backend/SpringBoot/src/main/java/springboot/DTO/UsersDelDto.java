package springboot.DTO;

import lombok.Data;
import java.util.List;

@Data
public class UsersDelDto {
    private List<String> userIds; // 与 SysUsers 的 String 类型 UUID 主键一致
}
