package com.yupi.springbootinit.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * spring-boot-starter-webmvc 配置类
 *
 * @author anyan
 * DateTime: 2024/5/2
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 权限拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验。
        SaInterceptor saInterceptor = new SaInterceptor(handle -> StpUtil.checkLogin());
        SubSaInterceptor subSaInterceptor = new SubSaInterceptor(saInterceptor);
        registry.addInterceptor(subSaInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/user/getCaptcha", "/user/login", "/user/register", "/user/logout", "/user/get/login",
                        "/user/sendEmailCode", "/user/email/login", "/user/email/register")
                //放行静态资源
                .excludePathPatterns("/doc.html", "/swagger-ui.html", "/webjars/**", "/v2/**", "/swagger-resources/**");
    }
}
