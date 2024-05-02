package com.yupi.springbootinit.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 组合SaInterceptor，实现自定义拦截器 放行OPTIONS请求（跨域请求）
 *
 * @author anyan
 * DateTime: 2024/5/2
 */
public class SubSaInterceptor implements HandlerInterceptor {

    private final SaInterceptor saInterceptor;

    public SubSaInterceptor(SaInterceptor saInterceptor) {
        this.saInterceptor = saInterceptor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断请求方式，排除OPTIONS请求
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;//通过所有OPTION请求
        } else {
            return saInterceptor.preHandle(request, response, handler);
        }
    }
}
