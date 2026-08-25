package springboot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.stereotype.Service;
import springboot.domain.ExternAccounts;
import springboot.mapper.ExternAccountsMapper;
import springboot.service.ExternAccountsService;

/**
* @author 13326
* @description 针对表【extern_accounts】的数据库操作Service实现
* @createDate 2025-09-08 20:59:43
*/
@Service
public class ExternAccountsServiceImpl extends ServiceImpl<ExternAccountsMapper, ExternAccounts>
    implements ExternAccountsService {

}




