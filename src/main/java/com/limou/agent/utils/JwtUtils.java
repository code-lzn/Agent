package com.limou.agent.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 工具类
 * 用于微信登录的跨域认证，替代 Cookie/Session 方案
 */
public class JwtUtils {

    /**
     * 签名密钥（生产环境应从配置读取）
     */
    private static final String SECRET = "MiaoYuGouPiao2024@JWTSecretKeyForWechatLogin!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /**
     * Token 有效期：30 天（与 Session 过期时间一致）
     */
    private static final long EXPIRE_MS = 30L * 24 * 60 * 60 * 1000;

    /**
     * 生成 JWT Token
     *
     * @param userId   用户 ID
     * @param userRole 用户角色
     * @return JWT Token 字符串
     */
    public static String createToken(Long userId, String userRole) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + EXPIRE_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userRole", userRole)
                .issuedAt(now)
                .expiration(expire)
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token 并返回 Claims。如果 Token 无效或过期则返回 null。
     *
     * @param token JWT Token 字符串
     * @return Claims 或 null
     */
    public static Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Claims 中提取用户 ID
     */
    public static Long getUserId(Claims claims) {
        if (claims == null) return null;
        String sub = claims.getSubject();
        return sub != null ? Long.valueOf(sub) : null;
    }
}
