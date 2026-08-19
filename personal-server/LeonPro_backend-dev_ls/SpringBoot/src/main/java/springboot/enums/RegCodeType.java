package springboot.enums;

public enum RegCodeType {
    TEMPORARY(0, "无注册信息"),
    WELDING(1, "auboweld"),
    PALLET(2, "aubo"),
    YOUBO_LEON(3, "youbo_leon");  // 改为新的枚举值

    private final int code;
    private final String description;

    RegCodeType(int code, String description) {
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
