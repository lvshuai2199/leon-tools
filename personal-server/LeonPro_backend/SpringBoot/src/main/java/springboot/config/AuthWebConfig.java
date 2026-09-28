package springboot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器：除下列公开路径外全部要求 Authorization: Bearer {token}。
 * /uploads/** 为静态资源（后台 <img> 缩略图无法带请求头），/wechat/oa 由微信签名自行校验。
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    public static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/auth/login2",
            "/auth/captcha",
            "/extern/**",
            "/public/**",
            "/uploads/**",
            "/wechat/oa",
            "/wechat/oa/**",
            "/error"
    };

    private final AuthInterceptor authInterceptor;

    public AuthWebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }
}
