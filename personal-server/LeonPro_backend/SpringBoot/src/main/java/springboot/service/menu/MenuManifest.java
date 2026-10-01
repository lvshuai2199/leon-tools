package springboot.service.menu;

import java.util.List;

/**
 * 读取一份清单的结果。MISSING（文件不存在）和 INVALID（解析 / 校验失败）都表示“这一端本次不同步”，
 * 绝不能当成空清单处理（否则会把整端菜单停用）。
 */
public record MenuManifest(String client, Status status, String location,
                           List<MenuManifestEntry> entries, List<String> errors, List<String> warnings) {

    public enum Status { LOADED, MISSING, INVALID }

    public boolean loaded() {
        return status == Status.LOADED;
    }
}
