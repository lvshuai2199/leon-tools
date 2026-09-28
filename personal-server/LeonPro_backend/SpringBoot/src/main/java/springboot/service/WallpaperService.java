package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import springboot.DTO.WallpaperBatchRequest;
import springboot.DTO.WallpaperGroupForm;
import springboot.DTO.WallpaperGroupVO;
import springboot.DTO.WallpaperImageUpdateRequest;
import springboot.DTO.WallpaperImageVO;
import springboot.domain.WallpaperGroup;
import springboot.domain.WallpaperImage;
import springboot.utils.DateUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 壁纸模块业务：分组、图片管理与外部取图。
 */
@Slf4j
@Service
public class WallpaperService {

    public static final Pattern GROUP_KEY = Pattern.compile("^[a-z0-9-]{1,64}$");
    public static final int MAX_PAGE_SIZE = 200;

    private final WallpaperGroupService groupService;
    private final WallpaperImageService imageService;
    private final WallpaperStorage storage;

    public WallpaperService(WallpaperGroupService groupService,
                            WallpaperImageService imageService,
                            WallpaperStorage storage) {
        this.groupService = groupService;
        this.imageService = imageService;
        this.storage = storage;
    }

    // ================================================================== 分组

    public List<WallpaperGroupVO> listGroups() {
        List<WallpaperGroup> groups = groupService.list(new LambdaQueryWrapper<WallpaperGroup>()
                .orderByAsc(WallpaperGroup::getSort).orderByAsc(WallpaperGroup::getCreateTime));
        Map<String, Long> counts = imageService.countByGroup(ids(groups), false);
        List<WallpaperGroupVO> out = new ArrayList<>();
        for (WallpaperGroup g : groups) {
            if (!hasToken(g)) {
                // 令牌为必填：历史数据或手工插入的分组自动补一个
                g.setAccessToken(newToken());
                groupService.update(new LambdaUpdateWrapper<WallpaperGroup>()
                        .eq(WallpaperGroup::getId, g.getId())
                        .set(WallpaperGroup::getAccessToken, g.getAccessToken()));
            }
            out.add(toAdminGroupVO(g, counts.getOrDefault(g.getId(), 0L)));
        }
        return out;
    }

    /**
     * 在当前事务内分配 sort 用：先 SELECT ... FOR UPDATE 锁分组行（串行化同组分配），
     * 再用锁定读（FOR SHARE）取组内最大 sort。锁定读读取最新已提交数据，
     * 不受 REPEATABLE READ 事务快照影响（普通 SELECT 会读到事务开头的旧快照而重复）。
     */
    private int lockedMaxSort(String groupId) {
        groupService.getOne(new LambdaQueryWrapper<WallpaperGroup>()
                .eq(WallpaperGroup::getId, groupId)
                .last("FOR UPDATE"), false);
        WallpaperImage last = imageService.getOne(new LambdaQueryWrapper<WallpaperImage>()
                .select(WallpaperImage::getSort)
                .eq(WallpaperImage::getGroupId, groupId)
                .orderByDesc(WallpaperImage::getSort)
                .last("LIMIT 1 FOR SHARE"), false);
        return last == null || last.getSort() == null ? 0 : last.getSort();
    }

    public WallpaperGroup getGroup(String id) {
        return id == null || id.isBlank() ? null : groupService.getById(id.trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public WallpaperGroupVO createGroup(WallpaperGroupForm form) {
        if (form == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        String name = requireName(form.getName());
        String key = requireKey(form.getGroupKey(), null);
        Date now = DateUtils.getNow();
        WallpaperGroup g = new WallpaperGroup();
        g.setName(name);
        g.setGroupKey(key);
        g.setDescription(trimTo(form.getDescription(), 500));
        g.setSort(form.getSort() != null ? form.getSort() : maxGroupSort() + 1);
        g.setIsPublic(flag(form.getIsPublic(), 1));
        g.setAccessToken(newToken());
        g.setCreateTime(now);
        g.setUpdateTime(now);
        groupService.save(g);
        return toAdminGroupVO(g, 0L);
    }

    @Transactional(rollbackFor = Exception.class)
    public WallpaperGroupVO updateGroup(String id, WallpaperGroupForm form) {
        WallpaperGroup g = getGroup(id);
        if (g == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        if (form == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        LambdaUpdateWrapper<WallpaperGroup> uw = new LambdaUpdateWrapper<>();
        uw.eq(WallpaperGroup::getId, g.getId());
        if (form.getName() != null) {
            uw.set(WallpaperGroup::getName, requireName(form.getName()));
        }
        if (form.getGroupKey() != null) {
            uw.set(WallpaperGroup::getGroupKey, requireKey(form.getGroupKey(), g.getId()));
        }
        if (form.getDescription() != null) {
            uw.set(WallpaperGroup::getDescription, trimTo(form.getDescription(), 500));
        }
        if (form.getSort() != null) {
            uw.set(WallpaperGroup::getSort, form.getSort());
        }
        if (form.getIsPublic() != null) {
            uw.set(WallpaperGroup::getIsPublic, flag(form.getIsPublic(), 1));
        }
        uw.set(WallpaperGroup::getUpdateTime, DateUtils.getNow());
        groupService.update(uw);
        WallpaperGroup fresh = groupService.getById(g.getId());
        return toAdminGroupVO(fresh, countImages(fresh.getId()));
    }

    /** 生成新的随机访问令牌（32 位十六进制），旧令牌立即失效；返回新令牌 */
    @Transactional(rollbackFor = Exception.class)
    public String regenerateToken(String id) {
        WallpaperGroup g = getGroup(id);
        if (g == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        String token = newToken();
        groupService.update(new LambdaUpdateWrapper<WallpaperGroup>()
                .eq(WallpaperGroup::getId, g.getId())
                .set(WallpaperGroup::getAccessToken, token)
                .set(WallpaperGroup::getUpdateTime, DateUtils.getNow()));
        return token;
    }

    public long countImages(String groupId) {
        return imageService.count(new LambdaQueryWrapper<WallpaperImage>().eq(WallpaperImage::getGroupId, groupId));
    }

    /** 删除分组及其全部图片记录与文件 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(String id) {
        WallpaperGroup g = getGroup(id);
        if (g == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        List<WallpaperImage> images = imageService.list(
                new LambdaQueryWrapper<WallpaperImage>().eq(WallpaperImage::getGroupId, g.getId()));
        imageService.remove(new LambdaQueryWrapper<WallpaperImage>().eq(WallpaperImage::getGroupId, g.getId()));
        groupService.removeById(g.getId());
        for (WallpaperImage img : images) {
            storage.deleteQuietly(img.getFilePath(), img.getThumbPath());
        }
        storage.deleteGroupDirQuietly(g.getId());
    }

    /**
     * 拖拽排序：只改提交的这些分组。取它们现有的 sort 值升序排列，再按提交的新顺序依次分配，
     * 未提交的分组（例如未加载的）位置保持不变。返回实际更新条数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int sortGroups(List<String> ids) {
        List<String> ordered = distinct(ids);
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("请传入分组ID数组");
        }
        Map<String, Integer> current = new HashMap<>();
        for (WallpaperGroup g : groupService.listByIds(ordered)) {
            current.put(g.getId(), g.getSort());
        }
        Map<String, Integer> changes = reassignSorts(ordered, current);
        Date now = DateUtils.getNow();
        for (Map.Entry<String, Integer> e : changes.entrySet()) {
            groupService.update(new LambdaUpdateWrapper<WallpaperGroup>()
                    .eq(WallpaperGroup::getId, e.getKey())
                    .set(WallpaperGroup::getSort, e.getValue())
                    .set(WallpaperGroup::getUpdateTime, now));
        }
        return changes.size();
    }

    // ================================================================== 图片

    public Page<WallpaperImageVO> pageImages(String groupId, long pageNum, long pageSize, boolean onlyEnabled) {
        LambdaQueryWrapper<WallpaperImage> qw = new LambdaQueryWrapper<>();
        if (groupId != null && !groupId.isBlank()) {
            qw.eq(WallpaperImage::getGroupId, groupId.trim());
        }
        if (onlyEnabled) {
            qw.eq(WallpaperImage::getEnabled, 1);
        }
        qw.orderByAsc(WallpaperImage::getSort).orderByAsc(WallpaperImage::getCreateTime).orderByAsc(WallpaperImage::getId);
        Page<WallpaperImage> page = imageService.page(new Page<>(clampPageNum(pageNum), clampPageSize(pageSize)), qw);
        Page<WallpaperImageVO> out = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(page.getRecords().stream().map(this::toImageVO).collect(Collectors.toList()));
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public WallpaperImageVO upload(String groupId, MultipartFile file, String title) throws IOException {
        WallpaperGroup g = getGroup(groupId);
        if (g == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        WallpaperStorage.StoredImage stored = storage.store(g.getId(), file);
        try {
            Date now = DateUtils.getNow();
            WallpaperImage img = new WallpaperImage();
            img.setGroupId(g.getId());
            String t = title != null && !title.isBlank() ? title : baseName(file.getOriginalFilename());
            img.setTitle(trimTo(sanitizeTitle(t), 200));
            img.setFilePath(stored.getFilePath());
            img.setThumbPath(stored.getThumbPath());
            img.setWidth(stored.getWidth());
            img.setHeight(stored.getHeight());
            img.setFileSize(stored.getFileSize());
            // 同组并发上传：锁分组行后用锁定读取最新 max(sort)，保证 +1 不重复（锁在事务提交时释放）
            img.setSort(lockedMaxSort(g.getId()) + 1);
            img.setEnabled(1);
            img.setCreateTime(now);
            img.setUpdateTime(now);
            imageService.save(img);
            return toImageVO(img);
        } catch (RuntimeException e) {
            storage.deleteQuietly(stored.getFilePath(), stored.getThumbPath());
            throw e;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WallpaperImageVO updateImage(String id, WallpaperImageUpdateRequest req) {
        WallpaperImage img = id == null ? null : imageService.getById(id);
        if (img == null) {
            throw new IllegalArgumentException("图片不存在");
        }
        if (req == null || (req.getTitle() == null && req.getEnabled() == null)) {
            throw new IllegalArgumentException("请传入 title 或 enabled");
        }
        LambdaUpdateWrapper<WallpaperImage> uw = new LambdaUpdateWrapper<>();
        uw.eq(WallpaperImage::getId, img.getId());
        if (req.getTitle() != null) {
            uw.set(WallpaperImage::getTitle, trimTo(sanitizeTitle(req.getTitle()), 200));
        }
        if (req.getEnabled() != null) {
            uw.set(WallpaperImage::getEnabled, flag(req.getEnabled(), 1));
        }
        uw.set(WallpaperImage::getUpdateTime, DateUtils.getNow());
        imageService.update(uw);
        return toImageVO(imageService.getById(img.getId()));
    }

    /** 替换图片文件：重新生成缩略图并删除旧文件，保留 id / 标题 / 排序 */
    @Transactional(rollbackFor = Exception.class)
    public WallpaperImageVO replace(String id, MultipartFile file) throws IOException {
        WallpaperImage img = id == null ? null : imageService.getById(id);
        if (img == null) {
            throw new IllegalArgumentException("图片不存在");
        }
        WallpaperStorage.StoredImage stored = storage.store(img.getGroupId(), file);
        String oldFile = img.getFilePath();
        String oldThumb = img.getThumbPath();
        try {
            imageService.update(new LambdaUpdateWrapper<WallpaperImage>()
                    .eq(WallpaperImage::getId, img.getId())
                    .set(WallpaperImage::getFilePath, stored.getFilePath())
                    .set(WallpaperImage::getThumbPath, stored.getThumbPath())
                    .set(WallpaperImage::getWidth, stored.getWidth())
                    .set(WallpaperImage::getHeight, stored.getHeight())
                    .set(WallpaperImage::getFileSize, stored.getFileSize())
                    .set(WallpaperImage::getUpdateTime, DateUtils.getNow()));
        } catch (RuntimeException e) {
            storage.deleteQuietly(stored.getFilePath(), stored.getThumbPath());
            throw e;
        }
        storage.deleteQuietly(oldFile, oldThumb);
        return toImageVO(imageService.getById(img.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public int batch(WallpaperBatchRequest req) throws IOException {
        if (req == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        List<String> ids = distinct(req.getIds());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请选择图片");
        }
        String action = req.getAction() == null ? "" : req.getAction().trim().toLowerCase(Locale.ROOT);
        Date now = DateUtils.getNow();
        switch (action) {
            case "enable":
            case "disable": {
                LambdaUpdateWrapper<WallpaperImage> uw = new LambdaUpdateWrapper<>();
                uw.in(WallpaperImage::getId, ids)
                        .set(WallpaperImage::getEnabled, "enable".equals(action) ? 1 : 0)
                        .set(WallpaperImage::getUpdateTime, now);
                imageService.update(uw);
                return (int) imageService.count(new LambdaQueryWrapper<WallpaperImage>().in(WallpaperImage::getId, ids));
            }
            case "move": {
                WallpaperGroup target = getGroup(req.getTargetGroupId());
                if (target == null) {
                    throw new IllegalArgumentException("目标分组不存在");
                }
                List<WallpaperImage> images = orderedImages(ids);
                int sort = lockedMaxSort(target.getId());
                int moved = 0;
                for (WallpaperImage img : images) {
                    if (target.getId().equals(img.getGroupId())) {
                        continue;
                    }
                    String newFile = storage.moveToGroup(img.getFilePath(), target.getId());
                    String newThumb = storage.moveToGroup(img.getThumbPath(), target.getId());
                    imageService.update(new LambdaUpdateWrapper<WallpaperImage>()
                            .eq(WallpaperImage::getId, img.getId())
                            .set(WallpaperImage::getGroupId, target.getId())
                            .set(WallpaperImage::getFilePath, newFile)
                            .set(WallpaperImage::getThumbPath, newThumb)
                            .set(WallpaperImage::getSort, ++sort)
                            .set(WallpaperImage::getUpdateTime, now));
                    moved++;
                }
                return moved;
            }
            case "delete": {
                List<WallpaperImage> images = imageService.listByIds(ids);
                if (images.isEmpty()) {
                    return 0;
                }
                imageService.removeByIds(ids(images));
                for (WallpaperImage img : images) {
                    storage.deleteQuietly(img.getFilePath(), img.getThumbPath());
                }
                return images.size();
            }
            default:
                throw new IllegalArgumentException("action 仅支持 enable / disable / move / delete");
        }
    }

    /**
     * 组内拖拽排序：规则同 sortGroups，只在提交的图片之间重新分配它们原有的 sort 值，
     * 未提交 / 未加载的图片保持不动。返回实际更新条数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int sortImages(List<String> ids) {
        List<String> ordered = distinct(ids);
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("请传入图片ID数组");
        }
        List<WallpaperImage> images = imageService.listByIds(ordered);
        Set<String> groups = images.stream().map(WallpaperImage::getGroupId).collect(Collectors.toSet());
        if (groups.size() > 1) {
            throw new IllegalArgumentException("只能对同一分组内的图片排序");
        }
        Map<String, Integer> current = new HashMap<>();
        for (WallpaperImage img : images) {
            current.put(img.getId(), img.getSort());
        }
        Map<String, Integer> changes = reassignSorts(ordered, current);
        Date now = DateUtils.getNow();
        for (Map.Entry<String, Integer> e : changes.entrySet()) {
            imageService.update(new LambdaUpdateWrapper<WallpaperImage>()
                    .eq(WallpaperImage::getId, e.getKey())
                    .set(WallpaperImage::getSort, e.getValue())
                    .set(WallpaperImage::getUpdateTime, now));
        }
        return changes.size();
    }

    /**
     * 纯函数：orderedIds 为提交的新顺序，currentSort 为这些 id 现有的 sort（不存在的 id 忽略，null 视为 0）。
     * 把现有 sort 值升序后按新顺序依次分配；若有重复值，则在提交集合内部顺延为严格递增（后一个至少比前一个大 1），
     * 保证新顺序生效。只返回 sort 发生变化的 id -> 新 sort，绝不涉及集合外的记录。
     */
    public static Map<String, Integer> reassignSorts(List<String> orderedIds, Map<String, Integer> currentSort) {
        List<String> present = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (String id : orderedIds) {
            if (currentSort.containsKey(id) && !present.contains(id)) {
                present.add(id);
                Integer v = currentSort.get(id);
                values.add(v == null ? 0 : v);
            }
        }
        Collections.sort(values);
        for (int k = 1; k < values.size(); k++) {
            if (values.get(k) <= values.get(k - 1)) {
                values.set(k, values.get(k - 1) + 1);
            }
        }
        Map<String, Integer> changes = new java.util.LinkedHashMap<>();
        for (int k = 0; k < present.size(); k++) {
            String id = present.get(k);
            Integer old = currentSort.get(id);
            int nv = values.get(k);
            if (old == null || old != nv) {
                changes.put(id, nv);
            }
        }
        return changes;
    }

    // ================================================================== 外部接口

    /** 公开且至少有一张启用图片的分组（免 token，且不返回 token）；张数只算启用图片，封面取排序第一张启用图片 */
    public List<WallpaperGroupVO> publicGroups() {
        List<WallpaperGroup> groups = groupService.list(new LambdaQueryWrapper<WallpaperGroup>()
                .eq(WallpaperGroup::getIsPublic, 1)
                .orderByAsc(WallpaperGroup::getSort).orderByAsc(WallpaperGroup::getCreateTime));
        Map<String, Long> counts = imageService.countByGroup(ids(groups), true);
        List<WallpaperGroupVO> out = new ArrayList<>();
        for (WallpaperGroup g : groups) {
            long count = counts.getOrDefault(g.getId(), 0L);
            if (count <= 0) {
                // 没有启用图片的分组不对外展示
                continue;
            }
            WallpaperGroupVO vo = toGroupVO(g, count);
            WallpaperImage cover = imageService.getOne(new LambdaQueryWrapper<WallpaperImage>()
                    .eq(WallpaperImage::getGroupId, g.getId())
                    .eq(WallpaperImage::getEnabled, 1)
                    .orderByAsc(WallpaperImage::getSort).orderByAsc(WallpaperImage::getCreateTime)
                    .last("LIMIT 1"), false);
            if (cover != null) {
                vo.setCoverUrl(storage.url(cover.getFilePath()));
                vo.setCoverThumbUrl(storage.url(thumbOrFile(cover)));
            }
            out.add(vo);
        }
        return out;
    }

    /** 按 key 查公开分组，不存在或未公开返回 null */
    public WallpaperGroup findPublicGroup(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        WallpaperGroup g = groupService.getOne(new LambdaQueryWrapper<WallpaperGroup>()
                .eq(WallpaperGroup::getGroupKey, key.trim()), false);
        if (g == null || g.getIsPublic() == null || g.getIsPublic() != 1) {
            return null;
        }
        return g;
    }

    /** count + 随机偏移，避免 ORDER BY RAND() 全表排序；仅供 random 接口使用 */
    public WallpaperImageVO randomImage(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<WallpaperImage> countQw = new LambdaQueryWrapper<WallpaperImage>()
                .in(WallpaperImage::getGroupId, groupIds)
                .eq(WallpaperImage::getEnabled, 1);
        long total = imageService.count(countQw);
        if (total <= 0) {
            return null;
        }
        long offset = ThreadLocalRandom.current().nextLong(total);
        WallpaperImage img = imageService.getOne(new LambdaQueryWrapper<WallpaperImage>()
                .in(WallpaperImage::getGroupId, groupIds)
                .eq(WallpaperImage::getEnabled, 1)
                .orderByAsc(WallpaperImage::getId)
                .last("LIMIT " + offset + ", 1"), false);
        if (img == null) {
            return null;
        }
        // 随机接口给外部直接使用：url / thumbUrl 为带公网 API 前缀的绝对路径（如 /prod-api/uploads/wallpaper/...）
        WallpaperImageVO vo = toImageVO(img);
        vo.setUrl(storage.redirectUrl(img.getFilePath()));
        vo.setThumbUrl(storage.redirectUrl(thumbOrFile(img)));
        return vo;
    }

    public boolean hasToken(WallpaperGroup g) {
        return g.getAccessToken() != null && !g.getAccessToken().isBlank();
    }

    /** 令牌必填：分组无令牌或请求未带 / 不匹配时均为 false（常量时间比较） */
    public boolean tokenMatches(WallpaperGroup g, String token) {
        if (!hasToken(g) || token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(g.getAccessToken().getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================== 工具

    public WallpaperImageVO toImageVO(WallpaperImage img) {
        WallpaperImageVO vo = new WallpaperImageVO();
        vo.setId(img.getId());
        vo.setGroupId(img.getGroupId());
        vo.setTitle(img.getTitle());
        vo.setUrl(storage.url(img.getFilePath()));
        vo.setThumbUrl(storage.url(thumbOrFile(img)));
        vo.setWidth(img.getWidth());
        vo.setHeight(img.getHeight());
        vo.setFileSize(img.getFileSize());
        vo.setSort(img.getSort());
        vo.setEnabled(img.getEnabled());
        vo.setCreateTime(img.getCreateTime());
        return vo;
    }

    private WallpaperGroupVO toAdminGroupVO(WallpaperGroup g, long imageCount) {
        WallpaperGroupVO vo = toGroupVO(g, imageCount);
        vo.setToken(g.getAccessToken());
        return vo;
    }

    private WallpaperGroupVO toGroupVO(WallpaperGroup g, long imageCount) {
        WallpaperGroupVO vo = new WallpaperGroupVO();
        vo.setId(g.getId());
        vo.setName(g.getName());
        vo.setGroupKey(g.getGroupKey());
        vo.setDescription(g.getDescription());
        vo.setSort(g.getSort());
        vo.setIsPublic(g.getIsPublic());
        vo.setImageCount(imageCount);
        vo.setCreateTime(g.getCreateTime());
        vo.setUpdateTime(g.getUpdateTime());
        return vo;
    }

    private static String thumbOrFile(WallpaperImage img) {
        return img.getThumbPath() != null && !img.getThumbPath().isBlank() ? img.getThumbPath() : img.getFilePath();
    }

    private List<WallpaperImage> orderedImages(List<String> ids) {
        Map<String, WallpaperImage> byId = new HashMap<>();
        for (WallpaperImage img : imageService.listByIds(ids)) {
            byId.put(img.getId(), img);
        }
        List<WallpaperImage> out = new ArrayList<>();
        for (String id : ids) {
            WallpaperImage img = byId.get(id);
            if (img != null) {
                out.add(img);
            }
        }
        return out;
    }

    private int maxGroupSort() {
        WallpaperGroup last = groupService.getOne(new LambdaQueryWrapper<WallpaperGroup>()
                .select(WallpaperGroup::getSort)
                .orderByDesc(WallpaperGroup::getSort)
                .last("LIMIT 1"), false);
        return last == null || last.getSort() == null ? 0 : last.getSort();
    }

    private String requireName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new IllegalArgumentException("请填写分组名称");
        }
        if (n.length() > 100) {
            throw new IllegalArgumentException("分组名称不能超过 100 字");
        }
        return n;
    }

    private String requireKey(String key, String selfId) {
        String k = key == null ? "" : key.trim();
        if (!GROUP_KEY.matcher(k).matches()) {
            throw new IllegalArgumentException("分组标识只能包含小写字母、数字和 -，长度 1~64");
        }
        LambdaQueryWrapper<WallpaperGroup> qw = new LambdaQueryWrapper<WallpaperGroup>().eq(WallpaperGroup::getGroupKey, k);
        if (selfId != null) {
            qw.ne(WallpaperGroup::getId, selfId);
        }
        if (groupService.count(qw) > 0) {
            throw new IllegalArgumentException("分组标识已存在：" + k);
        }
        return k;
    }

    private static String newToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** 兼容 true/false、1/0、"true"/"1" */
    private static Integer flag(Object v, int def) {
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean) {
            return (Boolean) v ? 1 : 0;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue() != 0 ? 1 : 0;
        }
        String t = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(t) || "1".equals(t)) {
            return 1;
        }
        if ("false".equals(t) || "0".equals(t) || t.isEmpty()) {
            return 0;
        }
        throw new IllegalArgumentException("开关参数只能是 true/false 或 1/0");
    }

    private static String trimTo(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** 标题只做展示：去掉控制字符和路径分隔符等（与存储路径无关，路径始终是 UUID） */
    private static String sanitizeTitle(String title) {
        if (title == null) {
            return null;
        }
        return title.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "").trim();
    }

    private static String baseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String n = filename.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static List<String> distinct(List<String> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                set.add(id.trim());
            }
        }
        return new ArrayList<>(set);
    }

    private static <T> List<String> ids(List<T> rows) {
        List<String> out = new ArrayList<>();
        for (T row : rows) {
            if (row instanceof WallpaperGroup) {
                out.add(((WallpaperGroup) row).getId());
            } else if (row instanceof WallpaperImage) {
                out.add(((WallpaperImage) row).getId());
            }
        }
        return out;
    }

    private static long clampPageNum(long pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private static long clampPageSize(long pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
