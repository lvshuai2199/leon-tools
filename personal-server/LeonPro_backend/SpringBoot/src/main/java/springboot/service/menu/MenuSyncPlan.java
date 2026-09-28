package springboot.service.menu;

import lombok.Getter;
import springboot.domain.SysMenus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 一端（admin / app）一次同步要做的事；dry-run 只打印它，apply 按它写库 */
@Getter
public class MenuSyncPlan {

    /** 给存量行补 route_key */
    public record Backfill(String id, String routeKey, String menuName) {
    }

    /** 更新已有行：changes 为 列名 → 新值；claimed = 原来是手工菜单、本次被清单认领；reenabled = 原来已停用、本次重新启用 */
    public record Update(String id, String routeKey, Map<String, Object> changes, List<String> changeLog,
                         boolean claimed, boolean reenabled) {
    }

    /** 清单里已没有的 managed 行，标记 disabled=1（不删行、不删授权） */
    public record Disable(String id, String routeKey, String menuName) {
    }

    private final String client;
    private final int manifestSize;
    private final List<Backfill> backfills = new ArrayList<>();
    private final List<SysMenus> inserts = new ArrayList<>();
    private final List<Update> updates = new ArrayList<>();
    private final List<Disable> disables = new ArrayList<>();
    /** 本该停用、但因为 disable-missing=false 或触发保护阈值而没有停用的行 */
    private final List<Disable> disablesSkipped = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int unchanged;
    private int manualUntouched;
    private boolean disableBlockedByGuard;

    public MenuSyncPlan(String client, int manifestSize) {
        this.client = client;
        this.manifestSize = manifestSize;
    }

    void incUnchanged() {
        unchanged++;
    }

    void setManualUntouched(int n) {
        manualUntouched = n;
    }

    void setDisableBlockedByGuard(boolean b) {
        disableBlockedByGuard = b;
    }

    public boolean hasWrites() {
        return !backfills.isEmpty() || !inserts.isEmpty() || !updates.isEmpty() || !disables.isEmpty();
    }

    public long reenabledCount() {
        return updates.stream().filter(Update::reenabled).count();
    }

    public long claimedCount() {
        return updates.stream().filter(Update::claimed).count();
    }

    public String summary() {
        return "清单 " + manifestSize + " 条：新增 " + inserts.size()
                + "，更新 " + updates.size() + "（其中认领手工菜单 " + claimedCount() + "、重新启用 " + reenabledCount() + "）"
                + "，停用 " + disables.size()
                + (disablesSkipped.isEmpty() ? "" : "（未停用 " + disablesSkipped.size() + (disableBlockedByGuard ? "，触发保护阈值" : "，disable-missing=false") + "）")
                + "，未变化 " + unchanged
                + "，补 route_key " + backfills.size()
                + "；手工菜单 " + manualUntouched + " 条未处理";
    }
}
