package springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import springboot.domain.WallpaperImage;
import springboot.mapper.WallpaperImageMapper;
import springboot.service.WallpaperImageService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WallpaperImageServiceImpl extends ServiceImpl<WallpaperImageMapper, WallpaperImage>
        implements WallpaperImageService {

    @Override
    public Map<String, Long> countByGroup(Collection<String> groupIds, boolean onlyEnabled) {
        Map<String, Long> result = new HashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return result;
        }
        QueryWrapper<WallpaperImage> qw = new QueryWrapper<>();
        qw.select("group_id AS gid", "COUNT(*) AS cnt").in("group_id", groupIds);
        if (onlyEnabled) {
            qw.eq("enabled", 1);
        }
        qw.groupBy("group_id");
        List<Map<String, Object>> rows = this.baseMapper.selectMaps(qw);
        for (Map<String, Object> row : rows) {
            Object gid = value(row, "gid");
            Object cnt = value(row, "cnt");
            if (gid != null && cnt instanceof Number) {
                result.put(String.valueOf(gid), ((Number) cnt).longValue());
            }
        }
        return result;
    }

    @Override
    public int maxSort(String groupId) {
        QueryWrapper<WallpaperImage> qw = new QueryWrapper<>();
        qw.select("MAX(sort) AS maxSort").eq("group_id", groupId);
        List<Map<String, Object>> rows = this.baseMapper.selectMaps(qw);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return 0;
        }
        Object v = value(rows.get(0), "maxSort");
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /** 兼容驱动返回的列名大小写 */
    private static Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
