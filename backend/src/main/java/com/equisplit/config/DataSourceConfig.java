package com.equisplit.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String rawUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String configuredUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String configuredPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minIdle;

    @Value("${spring.datasource.hikari.idle-timeout:30000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(maxLifetime);

        // Crucial for Supabase PgBouncer pooler compatibility
        config.setConnectionTestQuery("SELECT 1");
        config.addDataSourceProperty("prepareThreshold", "0");
        config.addDataSourceProperty("preparedStatementCacheQueries", "0");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");

        if (rawUrl.startsWith("postgresql://") || rawUrl.startsWith("postgres://")) {
            parseAndApplyPostgresUri(rawUrl, config);
        } else {
            config.setJdbcUrl(rawUrl);
            if (configuredUsername != null) {
                config.setUsername(configuredUsername);
            }
            if (configuredPassword != null) {
                config.setPassword(configuredPassword);
            }
        }

        log.info("Configured PostgreSQL DataSource for Supabase pooler compatibility");
        return new HikariDataSource(config);
    }

    private void parseAndApplyPostgresUri(String uriString, HikariConfig config) {
        String cleanUri = uriString.replaceFirst("^postgres(ql)?://", "");

        int slashIndex = cleanUri.indexOf('/');
        String userInfoAndHost;
        String databaseAndParams = "";

        if (slashIndex >= 0) {
            userInfoAndHost = cleanUri.substring(0, slashIndex);
            databaseAndParams = cleanUri.substring(slashIndex + 1);
        } else {
            userInfoAndHost = cleanUri;
        }

        int lastAtIndex = userInfoAndHost.lastIndexOf('@');
        String hostAndPort;
        if (lastAtIndex >= 0) {
            String userInfo = userInfoAndHost.substring(0, lastAtIndex);
            hostAndPort = userInfoAndHost.substring(lastAtIndex + 1);

            int colonIndex = userInfo.indexOf(':');
            if (colonIndex >= 0) {
                config.setUsername(userInfo.substring(0, colonIndex));
                config.setPassword(userInfo.substring(colonIndex + 1));
            } else {
                config.setUsername(userInfo);
            }
        } else {
            hostAndPort = userInfoAndHost;
            if (configuredUsername != null) config.setUsername(configuredUsername);
            if (configuredPassword != null) config.setPassword(configuredPassword);
        }

        String jdbcUrl = "jdbc:postgresql://" + hostAndPort + "/" + databaseAndParams;
        if (!jdbcUrl.contains("sslmode=")) {
            jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
        }

        config.setJdbcUrl(jdbcUrl);
    }
}
