package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.WallpaperGroup;
import springboot.mapper.WallpaperGroupMapper;
import springboot.service.WallpaperGroupService;

@Service
public class WallpaperGroupServiceImpl extends ServiceImpl<WallpaperGroupMapper, WallpaperGroup>
        implements WallpaperGroupService {
}
