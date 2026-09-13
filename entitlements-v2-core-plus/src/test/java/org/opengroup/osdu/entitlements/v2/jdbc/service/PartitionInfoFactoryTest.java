/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.entitlements.v2.jdbc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;

@ExtendWith(MockitoExtension.class)
class PartitionInfoFactoryTest {

    private static final String PARTITION_ID = "dp";

    @Mock
    private ICache<String, PartitionInfo> partitionInfoCache;
    @Mock
    private IPartitionProvider partitionProvider;
    @Mock
    private DpsHeaders dpsHeaders;

    @InjectMocks
    private PartitionInfoFactory sut;

    @BeforeEach
    void setUp() {
        // Lenient — getObjectType() test does not consult headers, so the shared stub is unused there.
        lenient().when(dpsHeaders.getPartitionId()).thenReturn(PARTITION_ID);
    }

    @Test
    void getObject_cacheHit_returnsCachedPartitionInfoWithoutHittingProvider() throws Exception {
        PartitionInfo cached = mock(PartitionInfo.class);
        when(partitionInfoCache.get(PARTITION_ID)).thenReturn(cached);

        PartitionInfo result = sut.getObject();

        assertSame(cached, result);
        verify(partitionProvider, never()).get(PARTITION_ID);
        verify(partitionInfoCache, never()).put(eq(PARTITION_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getObject_cacheMiss_fetchesFromProviderAndStoresInCache() throws Exception {
        when(partitionInfoCache.get(PARTITION_ID)).thenReturn(null);
        PartitionInfo fresh = mock(PartitionInfo.class);
        when(partitionProvider.get(PARTITION_ID)).thenReturn(fresh);

        PartitionInfo result = sut.getObject();

        assertSame(fresh, result);
        verify(partitionInfoCache, times(1)).put(PARTITION_ID, fresh);
    }

    @Test
    void getObject_providerThrowsPartitionException_wrappedAsAppException500() throws Exception {
        when(partitionInfoCache.get(PARTITION_ID)).thenReturn(null);
        // Underlying partition service outage must be surfaced as a 500 AppException — the factory
        // does NOT leak the source HTTP status.
        PartitionException pe = new PartitionException("boom", newHttpResponse(503));
        when(partitionProvider.get(PARTITION_ID)).thenThrow(pe);

        AppException ex = assertThrows(AppException.class, () -> sut.getObject());
        assertEquals(500, ex.getError().getCode());
    }

    private static HttpResponse newHttpResponse(int code) {
        HttpResponse r = new HttpResponse();
        r.setResponseCode(code);
        return r;
    }

    @Test
    void getObjectType_returnsPartitionInfoClass() {
        // Verifies FactoryBean contract — Spring uses this to determine bean type.
        assertEquals(PartitionInfo.class, sut.getObjectType());
    }
}
