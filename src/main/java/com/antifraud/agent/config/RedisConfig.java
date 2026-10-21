package com.antifraud.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        // Redis 专用 ObjectMapper(不暴露为 Spring Bean, 见 redisObjectMapper 注释)
        ObjectMapper mapper = redisObjectMapper();
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 专用 ObjectMapper。
     * <p>
     * 注意: <b>不要</b>把它声明为 {@code @Bean} 暴露到容器中。
     * 一旦容器中存在 ObjectMapper bean, 就会覆盖 Spring Boot 自动配置的那个,
     * 而裸 {@code new ObjectMapper()} 默认开启 WRITE_DATES_AS_TIMESTAMPS,
     * 导致 HTTP 响应里的 LocalDateTime 被序列化成数组(如 [2026,9,8,14,40,4])。
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
