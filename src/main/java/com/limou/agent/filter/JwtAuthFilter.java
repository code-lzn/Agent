package com.limou.agent.filter;

import com.limou.agent.constant.UserConstant;
import com.limou.agent.model.entity.User;
import com.limou.agent.service.UserService;
import com.limou.agent.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * <p>
 * 从请求头 Authorization: Bearer <token> 中提取 JWT Token，
 * 验证通过后将用户信息注入 request attribute，供后续 getLoginUser 使用。
 * <p>
 * 该过滤器与现有的 Cookie/Session 认证并存：
 * - 有 Session → 走原有 Cookie 认证（优先）
 * - 无 Session 但有 JWT Token → 走 Token 认证（微信登录免跨域问题）
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Resource
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 提取 Token
        String token = extractToken(request);
        if (token != null) {
            // 2. 解析 Token
            Claims claims = JwtUtils.parseToken(token);
            if (claims != null) {
                Long userId = JwtUtils.getUserId(claims);
                if (userId != null) {
                    // 3. 加载用户并注入 request attribute
                    User user = userService.getById(userId);
                    if (user != null && user.getIsDelete() != 1) {
                        request.setAttribute(UserConstant.USER_LOGIN_STATE, user);
                    }
                }
            }
        }

        // 4. 继续过滤链
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
