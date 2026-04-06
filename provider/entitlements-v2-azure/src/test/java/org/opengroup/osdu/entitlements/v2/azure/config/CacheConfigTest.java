package org.opengroup.osdu.entitlements.v2.azure.config;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.opengroup.osdu.azure.cache.IRedisClientFactory;
import org.opengroup.osdu.azure.cache.RedisAzureCache;
import org.opengroup.osdu.core.common.cache.IRedisCache;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReferences;
import org.opengroup.osdu.entitlements.v2.model.ParentReferences;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class CacheConfigTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @InjectMocks
    private CacheConfig cacheConfig;

    @Mock
    private IRedisClientFactory<ParentReferences> groupRedisClientFactory;

    @Mock
    private IRedisClientFactory<ChildrenReferences> memberRedisClientFactory;

    @Mock
    private IRedisCache<String, ParentReferences> mockParentRedisCache;

    @Mock
    private IRedisCache<String, ChildrenReferences> mockChildrenRedisCache;

    @Mock
    private RedissonClient mockRedissonClient;

    private final String principalId;
    private final Class<?> valueClass;

    public CacheConfigTest(String principalId, Class<?> valueClass) {
        this.principalId = principalId;
        this.valueClass = valueClass;
    }

    @Parameterized.Parameters(name = "{1} with principalId={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "a1b2c3d4-e5f6-47a8-9bcd-ef0123456789", ParentReferences.class },
            { null, ParentReferences.class },
            { "f9e8d7c6-b5a4-43c2-8fed-ba9876543210", ChildrenReferences.class },
            { null, ChildrenReferences.class }
        });
    }

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(cacheConfig, "redisPort", 6380);
        ReflectionTestUtils.setField(cacheConfig, "redisDatabase", 8);
        ReflectionTestUtils.setField(cacheConfig, "redisTtlSeconds", 3600);
        ReflectionTestUtils.setField(cacheConfig, "redisExpiration", 3600);
        ReflectionTestUtils.setField(cacheConfig, "commandTimeout", 5);
        ReflectionTestUtils.setField(cacheConfig, "applicationName", "test-app");
        ReflectionTestUtils.setField(cacheConfig, "redisPrincipalId", principalId);

        when(groupRedisClientFactory.getClient(any(), any(), any())).thenReturn(mockParentRedisCache);
        when(groupRedisClientFactory.getRedissonClient(any(), any())).thenReturn(mockRedissonClient);
        when(memberRedisClientFactory.getClient(any(), any(), any())).thenReturn(mockChildrenRedisCache);
        when(memberRedisClientFactory.getRedissonClient(any(), any())).thenReturn(mockRedissonClient);
    }

    @Test
    public void testCacheCanBeCreated() {
        RedisAzureCache<?> cache = valueClass == ParentReferences.class
            ? cacheConfig.groupCacheRedis(groupRedisClientFactory)
            : cacheConfig.memberCacheRedis(memberRedisClientFactory);

        assertNotNull(cache);
    }
}
