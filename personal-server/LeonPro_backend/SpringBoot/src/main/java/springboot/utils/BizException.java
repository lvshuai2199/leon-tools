package springboot.utils;

/**
 * 业务校验失败（如次数不足、子用户数已满）：由 GlobalExceptionHandler 转成
 * {@code {status: 500, message}}（HTTP 200，与其他 ApiResponse.failure 一致），并让所在事务整体回滚。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
