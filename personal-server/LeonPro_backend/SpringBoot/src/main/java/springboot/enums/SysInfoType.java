package springboot.enums;

public enum SysInfoType {

    TEMPORARY(0, "系统消息"),
    PERMANENT(1, "通知"),
    TRIAL(2, "申请码");

    private final int code;
    private final String description;

    SysInfoType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    // 根据代码获取描述
    public static String getDescriptionByCode(int code) {
        for (RegCodeType type : RegCodeType.values()) {
            if (type.getCode() == code) {
                return type.getDescription();
            }
        }
        return "未知类型";
    }
}
