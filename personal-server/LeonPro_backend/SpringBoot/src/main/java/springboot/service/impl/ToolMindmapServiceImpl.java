package springboot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.ToolMindmap;
import springboot.mapper.ToolMindmapMapper;
import springboot.service.ToolMindmapService;

@Service
public class ToolMindmapServiceImpl extends ServiceImpl<ToolMindmapMapper, ToolMindmap>
        implements ToolMindmapService {
}
