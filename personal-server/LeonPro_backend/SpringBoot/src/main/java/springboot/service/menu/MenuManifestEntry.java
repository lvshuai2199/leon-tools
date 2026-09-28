package springboot.service.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 页面清单（menus.json）中的一项。字段含义见 menu-sync-design.md 第 2.2 节。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuManifestEntry {

    public static final String TYPE_DIR = "dir";
    public static final String TYPE_PAGE = "page";

    /** 完整绝对路径，同步的匹配键（route_key） */
    private String path;
    /** 显示名称 */
    private String name;
    /** 父级完整路径，顶级为 null */
    private String parent;
    /** 相对 src/views/ 的组件路径（page 必填；dir 为空或 Layout） */
    private String component;
    private String icon;
    private Integer sort;
    /** dir / page */
    private String type;
    private Boolean hidden;
    private Boolean keepAlive;
    /** admin / app，必须与文件所属端一致 */
    private String client;
    private String redirect;
    private String routeName;
    private Boolean alwaysShow;

    public boolean isDir() {
        return TYPE_DIR.equals(type);
    }

    public boolean isHidden() {
        return Boolean.TRUE.equals(hidden);
    }
}
