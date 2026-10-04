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

package org.opengroup.osdu.entitlements.v2.jdbc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.NodeType;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.model.Role;

class GroupInfoEntityTest {

    private static final String PARTITION_ID = "dp";
    private static final String GROUP_EMAIL = "USERS.Data.Root@dp.group.com";

    private static GroupInfoEntity newGroup(Set<AppId> appIds) {
        return GroupInfoEntity.builder()
                .id(101L)
                .name("Users.Data.Root")
                .email(GROUP_EMAIL)
                .description("Data root group")
                .partitionId(PARTITION_ID)
                .appIds(appIds)
                .build();
    }

    @Test
    void toEntityNode_lowercasesEmailAndName_andPreservesAppIds() {
        Set<AppId> appIds = new HashSet<>();
        appIds.add(AppId.builder().appIdValue("app-a").build());
        appIds.add(AppId.builder().appIdValue("app-b").build());
        GroupInfoEntity entity = newGroup(appIds);

        EntityNode node = entity.toEntityNode();

        assertEquals(NodeType.GROUP, node.getType());
        // Email and name must be normalized to lowercase.
        assertEquals("users.data.root@dp.group.com", node.getNodeId());
        assertEquals("users.data.root", node.getName());
        assertEquals(PARTITION_ID, node.getDataPartitionId());
        assertEquals(2, node.getAppIds().size());
        assertTrue(node.getAppIds().contains("app-a"));
        assertTrue(node.getAppIds().contains("app-b"));
    }

    @Test
    void toEntityNode_emptyAppIds_returnsEntityNodeWithEmptyAppIdSet() {
        GroupInfoEntity entity = newGroup(new HashSet<>());

        EntityNode node = entity.toEntityNode();

        assertNotNull(node.getAppIds());
        assertTrue(node.getAppIds().isEmpty());
    }

    @Test
    void fromEntityNode_populatesEntityFromEntityNode_wrappingAppIds() {
        Set<String> appIds = new HashSet<>();
        appIds.add("app-x");
        EntityNode node = EntityNode.builder()
                .nodeId("users.g@dp.group.com")
                .name("users.g")
                .description("d")
                .dataPartitionId(PARTITION_ID)
                .type(NodeType.GROUP)
                .appIds(appIds)
                .build();

        GroupInfoEntity entity = GroupInfoEntity.fromEntityNode(node);

        assertEquals("users.g", entity.getName());
        assertEquals("users.g@dp.group.com", entity.getEmail());
        assertEquals(PARTITION_ID, entity.getPartitionId());
        assertEquals(1, entity.getAppIds().size());
        assertEquals("app-x", entity.getAppIds().iterator().next().getAppIdValue());
    }

    @Test
    void toChildrenReference_returnsMemberRoleGroupType_lowercaseEmail() {
        GroupInfoEntity entity = newGroup(Collections.emptySet());

        ChildrenReference ref = entity.toChildrenReference();

        assertEquals("users.data.root@dp.group.com", ref.getId());
        assertEquals(Role.MEMBER, ref.getRole());
        assertEquals(NodeType.GROUP, ref.getType());
        assertEquals(PARTITION_ID, ref.getDataPartitionId());
    }

    @Test
    void toParentReference_populatesAppIds_whenAppIdsSetIsNonNull() {
        Set<AppId> appIds = new HashSet<>();
        appIds.add(AppId.builder().appIdValue("app-a").build());
        GroupInfoEntity entity = newGroup(appIds);

        ParentReference ref = entity.toParentReference();

        assertEquals("users.data.root@dp.group.com", ref.getId());
        assertEquals("Users.Data.Root", ref.getName());
        assertEquals(PARTITION_ID, ref.getDataPartitionId());
        // AppIds must round-trip from the app_id table into the reference.
        assertEquals(1, ref.getAppIds().size());
        assertTrue(ref.getAppIds().contains("app-a"));
    }

    @Test
    void toParentReference_nullAppIds_leavesAppIdsUntouched() {
        GroupInfoEntity entity = GroupInfoEntity.builder()
                .id(1L)
                .name("g")
                .email("g@dp.group.com")
                .description("desc")
                .partitionId(PARTITION_ID)
                .appIds(null)
                .build();

        // When the app_id join is empty (null Set), the builder leaves the default empty set from
        // ParentReference — the mapper must not throw when appIds is null.
        ParentReference ref = entity.toParentReference();

        assertNotNull(ref);
        assertEquals("g@dp.group.com", ref.getId());
    }
}
