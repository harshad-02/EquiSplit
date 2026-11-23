package com.equisplit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.time.Duration;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${app.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            URI uri = URI.create(redisUrl);
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            int port = uri.getPort() > 0 ? uri.getPort() : 6379;
            boolean isSsl = "rediss".equalsIgnoreCase(uri.getScheme());

            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(host, port);

            if (uri.getUserInfo() != null) {
                String[] parts = uri.getUserInfo().split(":", 2);
                if (parts.length > 0 && !parts[0].isEmpty() && !"default".equalsIgnoreCase(parts[0])) {
                    redisConfig.setUsername(parts[0]);
                }
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    redisConfig.setPassword(RedisPassword.of(parts[1]));
                }
            }

            JedisClientConfiguration.JedisClientConfigurationBuilder builder =
                    JedisClientConfiguration.builder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .readTimeout(Duration.ofSeconds(5));

            if (isSsl) {
                builder.useSsl();
            }

            log.info("Configured RedisConnectionFactory for host: {}, port: {}, ssl: {}", host, port, isSsl);
            return new JedisConnectionFactory(redisConfig, builder.build());
        } catch (Exception e) {
            log.error("Failed to parse REDIS_URL or create RedisConnectionFactory: {}", e.getMessage());
            // Fallback to default localhost JedisConnectionFactory so Spring context boots cleanly
            return new JedisConnectionFactory();
        }
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
