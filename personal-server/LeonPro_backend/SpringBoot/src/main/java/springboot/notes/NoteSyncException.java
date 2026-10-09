package springboot.notes;

/** 仓库克隆、拉取、推送失败。message 已去掉令牌，可以直接给前端。 */
public class NoteSyncException extends RuntimeException {

    public NoteSyncException(String message) {
        super(message == null || message.isBlank() ? "仓库访问失败" : message);
    }
}
