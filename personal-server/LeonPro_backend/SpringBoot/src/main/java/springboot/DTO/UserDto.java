package springboot.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;



@Data
public class UserDto {

    private String id;

    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    /**
     * 密码：新增用户时必填，编辑时留空表示不修改密码
     */
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @Email(message = "Invalid email format")
    private String email;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 角色ID（对应 sys_roles.id）
     */
    private String roleId;
}
