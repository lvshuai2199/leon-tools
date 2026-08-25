package springboot.DTO;

import lombok.Data;
import java.util.List;

@Data
public class UsersDelDto {
    private List<Long> userIds; // 假设用户 ID 是 Long 类型
}
