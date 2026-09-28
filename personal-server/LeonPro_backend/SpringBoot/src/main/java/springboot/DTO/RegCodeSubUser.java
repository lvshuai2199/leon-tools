package springboot.DTO;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 注册码子用户与按配置次数相关的请求 / 返回结构（/common/regCode/subUsers/**、/admin/regCodeUser/**）。
 */
public final class RegCodeSubUser {

    private RegCodeSubUser() {
    }

    /** 某个配置的次数：新建子用户时是“分配多少”，管理员保存客户时是“该配置的上限” */
    @Data
    public static class QuotaCount {
        private String configId;
        private Integer count;
    }

    /** POST /common/regCode/subUsers */
    @Data
    public static class CreateForm {
        private String username;
        private String nickname;
        private String password;
        private List<QuotaCount> quotas;
    }

    /** POST /common/regCode/subUsers/{id}/quota 的一项：正数从自己剩余里追加，负数收回未用的 */
    @Data
    public static class DeltaItem {
        private String configId;
        private Integer delta;
    }

    @Data
    public static class DeltaForm {
        private List<DeltaItem> items;
    }

    /** POST /common/regCode/subUsers/{id}/status：0 停用，1 启用 */
    @Data
    public static class StatusForm {
        private Integer status;
    }

    /** 管理员直接改某个账号某个配置的次数 */
    @Data
    public static class AdminQuotaItem {
        private String configId;
        private Integer allocated;
        private Integer used;
    }

    /** POST /admin/regCodeUser/quota */
    @Data
    public static class AdminQuotaForm {
        private String userId;
        private List<AdminQuotaItem> items;
    }

    /** 某个配置的次数明细 */
    @Data
    public static class QuotaItem {
        private String configId;
        private String configName;
        /** 已分配（上限） */
        private int allocated;
        private int used;
        /** 剩余 = allocated - used；对子用户来说也就是停用或收回时能退回给创建人的数量 */
        private int remaining;
    }

    /** 子用户列表的一项 */
    @Data
    public static class SubUserItem {
        private String id;
        private String username;
        private String nickname;
        /** 1 启用，0 停用 */
        private int status;
        private Date createTime;
        private int usedTotal;
        private int allocatedTotal;
    }

    /** GET /common/regCode/subUsers、GET /admin/regCodeUser/subUsers */
    @Data
    public static class SubUserList {
        private int createdCount;
        private int maxSubUsers;
        /** 还能不能新建（有管理子用户的资格且未达上限） */
        private boolean canCreate;
        private List<SubUserItem> items = new ArrayList<>();
    }

    /** GET /common/regCode/subUsers/{id}/quota */
    @Data
    public static class SubUserQuota {
        private String subUserId;
        private List<QuotaItem> items = new ArrayList<>();
        /** 停用时会退回给创建人的总数（= 各配置 remaining 之和） */
        private int refundableTotal;
        /** 创建人自己各配置的剩余，追加分配时不能超过 */
        private List<QuotaItem> creatorRemaining = new ArrayList<>();
    }

    @Data
    public static class RefundItem {
        private String configId;
        private int count;

        public RefundItem() {
        }

        public RefundItem(String configId, int count) {
            this.configId = configId;
            this.count = count;
        }
    }

    /** POST /common/regCode/subUsers/{id}/status 的返回 */
    @Data
    public static class StatusResult {
        private int status;
        private List<RefundItem> refunded = new ArrayList<>();
        private int refundedTotal;
    }
}
