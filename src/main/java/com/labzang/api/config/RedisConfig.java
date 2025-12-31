package com.labzang.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정
 * 로컬 Redis와 Google Cloud Memorystore Redis를 모두 지원합니다.
 * 
 * SSL 설정은 application.yaml의 spring.data.redis.ssl.enabled로 제어됩니다.
 * Spring Boot의 자동 설정이 SSL 연결을 처리합니다.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    /**
     * RedisTemplate 빈 생성
     * RedisConnectionFactory는 Spring Boot 자동 설정을 사용합니다.
     * application.yaml의 spring.data.redis 설정이 자동으로 적용됩니다.
     */

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key는 String으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value는 JSON으로 직렬화
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();

        // 연결 정보 출력
        if (sslEnabled) {
            System.out.println("Redis SSL 연결 활성화: " + redisHost + ":" + redisPort);
        } else {
            System.out.println("Redis 일반 연결: " + redisHost + ":" + redisPort);
        }

        // 연결 테스트 (비동기로 실행하여 애플리케이션 시작을 막지 않음)
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 애플리케이션 시작 후 2초 대기
                template.opsForValue().set("connection:test", "ok", 10, java.util.concurrent.TimeUnit.SECONDS);
                System.out.println("✅ Redis 연결 성공");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 연결 실패 (선택적 기능): " + e.getMessage());
                System.err.println("   Redis 없이도 애플리케이션은 정상 작동합니다.");
                // 스택 트레이스는 첫 줄만 출력
                if (e.getCause() != null) {
                    System.err.println(
                            "   원인: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage());
                }
            }
        }).start();

        return template;
    }
}