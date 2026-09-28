package springboot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器：除下列公开路径外全部要求 Authorization: Bearer {token}。
 * /auth/login 登录；/public/** 免登录接口（分享页、公开壁纸、思维导图图片，
 * 以及 /public/wechat/oa——由微信签名自行校验）；/uploads/** 为静态资源（后台 <img> 缩略图无法带请求头）。
 * OPTIONS 预检由 AuthInterceptor 直接放行。
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    public static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/public/**",
            "/uploads/**",
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
