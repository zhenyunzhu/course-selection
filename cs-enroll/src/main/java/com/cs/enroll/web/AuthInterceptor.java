package com.cs.enroll.web;

import com.cs.common.BizException;
import com.cs.common.ResultCode;
import com.cs.enroll.context.StudentContext;
import com.cs.enroll.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器。
 *
 * 优先信任网关注入的 X-Student-Id（网关已经校验过 token）；
 * 直连本服务调试时，退化成自己解析 Authorization。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String studentIdHeader = request.getHeader("X-Student-Id");
        if (studentIdHeader != null && !studentIdHeader.isBlank()) {
            StudentContext.set(Long.valueOf(studentIdHeader));
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith(BEARER)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Long studentId = authService.resolve(auth.substring(BEARER.length()));
        if (studentId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        StudentContext.set(studentId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程池复用，不清会串号
        StudentContext.clear();
    }
}
