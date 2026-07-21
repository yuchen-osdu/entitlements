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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.entitlements.v2.jdbc.JdbcAppProperties;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.NodeType;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.model.ParentReferences;
import org.opengroup.osdu.entitlements.v2.model.ParentTreeDto;
import org.opengroup.osdu.entitlements.v2.spi.retrievegroup.RetrieveGroupRepo;

@ExtendWith(MockitoExtension.class)
class GroupCacheServiceJdbcTest {

    private static final String PARTITION_ID = "dp";
    private static final String DOMAIN = "group.com";
    private static final String GROUP_EMAIL = "users.data.root@dp.group.com";
    private static final String USER_EMAIL = "some.user@example.com";

    @Mock
    private JdbcAppProperties config;
    @Mock
    private ICache<String, ParentReferences> entityGroupsCache;
    @Mock
    private RetrieveGroupRepo retrieveGroupRepo;

    @InjectMocks
    private GroupCacheServiceJdbc sut;

    @Test
    void getFromPartitionCache_userInput_returnsCachedReferencesWithoutHittingRepo() {
        when(config.getDomain()).thenReturn(DOMAIN);
        ParentReferences cached = new ParentReferences();
        ParentReference ref = ParentReference.builder().id("g@dp.group.com").name("g").build();
        cached.setParentReferencesOfUser(new HashSet<>(Collections.singletonList(ref)));
        // Cache HIT: returned non-null value must be returned as-is without loading from repo.
        when(entityGroupsCache.get(anyString())).thenReturn(cached);

        Set<ParentReference> result = sut.getFromPartitionCache(USER_EMAIL, PARTITION_ID);

        assertEquals(1, result.size());
        assertTrue(result.contains(ref));
        verify(retrieveGroupRepo, never()).loadAllParents(any(), any());
        verify(entityGroupsCache, never()).put(anyString(), any(ParentReferences.class));
    }

    @Test
    void getFromPartitionCache_userInput_cacheMiss_loadsAndPutsInCache() {
        when(config.getDomain()).thenReturn(DOMAIN);
        // Cache MISS path: repo returns the parents, service stores under a "false"-suffixed key.
        when(entityGroupsCache.get(anyString())).thenReturn(null);
        ParentReference ref = ParentReference.builder().id("g@dp.group.com").name("g").build();
        ParentTreeDto treeDto = ParentTreeDto.builder()
                .parentReferences(new HashSet<>(Collections.singletonList(ref)))
                .build();
        when(retrieveGroupRepo.loadAllParents(any(), eq(false))).thenReturn(treeDto);

        Set<ParentReference> result = sut.getFromPartitionCache(USER_EMAIL, PARTITION_ID);

        assertEquals(1, result.size());
        assertTrue(result.contains(ref));

        // Verify the roleRequired=false key suffix.
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(entityGroupsCache).put(keyCap.capture(), any(ParentReferences.class));
        assertTrue(keyCap.getValue().endsWith("-false"));
    }

    @Test
    void getFromPartitionCache_roleRequired_true_populatesRoleKeyAndCallsLoadWithFlag() {
        when(config.getDomain()).thenReturn(DOMAIN);
        when(entityGroupsCache.get(anyString())).thenReturn(null);
        ParentReference ref = ParentReference.builder().id("g@dp.group.com").name("g").role("OWNER").build();
        ParentTreeDto treeDto = ParentTreeDto.builder()
                .parentReferences(new HashSet<>(Collections.singletonList(ref)))
                .build();
        when(retrieveGroupRepo.loadAllParents(any(), eq(true))).thenReturn(treeDto);

        Set<ParentReference> result = sut.getFromPartitionCache(USER_EMAIL, PARTITION_ID, Boolean.TRUE);

        assertEquals(1, result.size());
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(entityGroupsCache).put(keyCap.capture(), any(ParentReferences.class));
        // Confirm the "-true" role-required key suffix distinguishes the two cache slots.
        assertTrue(keyCap.getValue().endsWith("-true"));
    }

    @Test
    void getFromPartitionCache_groupInput_takesGroupEmailBranch() {
        // Feeding a group-email identifier must exercise the group branch: the service builds an
        // EntityNode of type GROUP (createNodeFromGroupEmail) before delegating to loadAllParents.
        // Capturing that node and asserting its type is what actually distinguishes this from the
        // member branch (which would build a USER node via createMemberNodeForNewUser).
        when(config.getDomain()).thenReturn(DOMAIN);
        when(entityGroupsCache.get(anyString())).thenReturn(null);
        when(retrieveGroupRepo.loadAllParents(any(), any())).thenReturn(
                ParentTreeDto.builder().parentReferences(new HashSet<>()).build());

        Set<ParentReference> result = sut.getFromPartitionCache(GROUP_EMAIL, PARTITION_ID, Boolean.FALSE);

        assertTrue(result.isEmpty());
        ArgumentCaptor<EntityNode> nodeCap = ArgumentCaptor.forClass(EntityNode.class);
        verify(retrieveGroupRepo, times(1)).loadAllParents(nodeCap.capture(), eq(false));
        // GROUP type here proves the group-email branch was taken, not the member branch.
        assertEquals(NodeType.GROUP, nodeCap.getValue().getType());
        assertEquals(GROUP_EMAIL, nodeCap.getValue().getNodeId());
    }

    @Test
    void refreshListGroupCache_iteratesUsers_deletingBothRoleKeys() {
        when(config.getDomain()).thenReturn(DOMAIN);

        Set<String> userIds = new HashSet<>();
        userIds.add("a@example.com");
        userIds.add("b@example.com");

        sut.refreshListGroupCache(userIds, PARTITION_ID);

        // Each user must be flushed for both roleRequired variants (true + false).
        verify(entityGroupsCache, times(2 * userIds.size())).delete(anyString());
    }

    @Test
    void flushListGroupCacheForUser_deletesBothCacheKeys() {
        when(config.getDomain()).thenReturn(DOMAIN);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);

        sut.flushListGroupCacheForUser(USER_EMAIL, PARTITION_ID);

        verify(entityGroupsCache, times(2)).delete(keyCap.capture());
        // We expect one "-true" and one "-false" key.
        boolean hasTrue = keyCap.getAllValues().stream().anyMatch(k -> k.endsWith("-true"));
        boolean hasFalse = keyCap.getAllValues().stream().anyMatch(k -> k.endsWith("-false"));
        assertTrue(hasTrue, "expected a cache key with -true suffix");
        assertTrue(hasFalse, "expected a cache key with -false suffix");
    }

    @Test
    void refreshListGroupCache_emptySet_isANoOp() {
        // Empty user set exits the loop without touching the cache — verifies no accidental deletes.
        sut.refreshListGroupCache(Collections.emptySet(), PARTITION_ID);
        verify(entityGroupsCache, never()).delete(anyString());
    }

    @Test
    void getFromPartitionCache_defaultOverload_delegatesWithRoleFalse() {
        // The 2-arg overload must use Boolean.FALSE and place the value under a "-false" key.
        when(config.getDomain()).thenReturn(DOMAIN);
        ParentReferences cached = mock(ParentReferences.class);
        when(cached.getParentReferencesOfUser()).thenReturn(Collections.emptySet());
        when(entityGroupsCache.get(anyString())).thenReturn(cached);

        Set<ParentReference> refs = sut.getFromPartitionCache(USER_EMAIL, PARTITION_ID);

        assertTrue(refs.isEmpty());
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(entityGroupsCache).get(keyCap.capture());
        assertTrue(keyCap.getValue().endsWith("-false"));
    }
}
