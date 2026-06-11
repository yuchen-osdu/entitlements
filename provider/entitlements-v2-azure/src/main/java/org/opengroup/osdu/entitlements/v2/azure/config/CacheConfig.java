package org.opengroup.osdu.entitlements.v2.azure.config;

import org.opengroup.osdu.azure.cache.IRedisClientFactory;
import org.opengroup.osdu.azure.cache.RedisAzureCache;
import org.opengroup.osdu.azure.di.RedisAzureConfiguration;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReferences;
import org.opengroup.osdu.entitlements.v2.model.ParentReferences;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class CacheConfig {

    @Value("${redis.port}")
    private int redisPort;

    @Value("${redis.database}")
    private int redisDatabase;

    @Value("${app.redis.ttl.seconds}")
    private int redisTtlSeconds;

    @Value("${redis.expiration:3600}")
    private int redisExpiration;

    @Value("${redis.command.timeout:5}")
    private int commandTimeout;

    @Value("${redis.principal.id:#{null}}")
    private String redisPrincipalId;

    @Value("${redis.hostname:#{null}}")
    private String redisHostname;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * To make sure a connection to redis is created beforehand,
     * we need to create this spring bean on application startup
     */
    @Bean
    @Lazy(false)
    public RedisAzureCache<ParentReferences> groupCacheRedis(IRedisClientFactory<ParentReferences> redisClientFactory) {
        return createRedisCache(ParentReferences.class, redisClientFactory);
    }

    @Bean
    public RedisAzureCache<ChildrenReferences> memberCacheRedis(IRedisClientFactory<ChildrenReferences> redisClientFactory) {
        return createRedisCache(ChildrenReferences.class, redisClientFactory);
    }

    private <T> RedisAzureCache<T> createRedisCache(Class<T> valueClass, IRedisClientFactory<T> redisClientFactory) {
        RedisAzureConfiguration redisConfig = new RedisAzureConfiguration(
            redisDatabase,
            redisExpiration,
            redisPort,
            redisTtlSeconds,
            commandTimeout,
            redisPrincipalId,
            redisHostname);

        // Forcing the Redis client creation + connection establishment at service startup
        redisClientFactory.getClient(valueClass, redisConfig, null);
        redisClientFactory.getRedissonClient(this.applicationName, redisConfig);

        return new RedisAzureCache<>(valueClass, redisConfig);
    }
}