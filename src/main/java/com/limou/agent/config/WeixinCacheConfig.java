package com.limou.agent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 微信相关缓存配置（基于 Caffeine）
 */
@Configuration
public class WeixinCacheConfig {

    /**
     * 微信 AccessToken 缓存（2小时过期）
     */
    @Bean(name = "weixinAccessTokenCache")
    public Cache<String, String> weixinAccessTokenCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build();
    }

    /**
     * 微信 Openid-Ticket 登录映射缓存（1小时过期）
     */
    @Bean(name = "openidTokenCache")
    public Cache<String, String> openidTokenCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

}
