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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.NodeType;
import org.opengroup.osdu.entitlements.v2.model.Role;

class MemberInfoEntityTest {

    private static final String PARTITION_ID = "dp";
    private static final String USER_EMAIL_MIXED_CASE = "User@Example.COM";
    private static final String USER_EMAIL_LOWER = "user@example.com";

    @Test
    void toEntityNode_producesUserNodeWithLowercaseEmailAsNodeIdAndName() {
        MemberInfoEntity entity = MemberInfoEntity.builder()
                .id(11L)
                .email(USER_EMAIL_MIXED_CASE)
                .partitionId(PARTITION_ID)
                .role(Role.MEMBER.getValue())
                .build();

        EntityNode node = entity.toEntityNode();

        assertEquals(NodeType.USER, node.getType());
        // Email is lowercased for nodeId and name.
        assertEquals(USER_EMAIL_LOWER, node.getNodeId());
        assertEquals(USER_EMAIL_LOWER, node.getName());
        assertEquals(PARTITION_ID, node.getDataPartitionId());
        // Description keeps the original (non-lowercased) email — verifies the mapping contract.
        assertEquals(USER_EMAIL_MIXED_CASE, node.getDescription());
    }

    @Test
    void fromEntityNode_populatesEmailRoleAndPartition() {
        EntityNode node = EntityNode.builder()
                .nodeId(USER_EMAIL_LOWER)
                .name(USER_EMAIL_LOWER)
                .type(NodeType.USER)
                .dataPartitionId(PARTITION_ID)
                .build();

        MemberInfoEntity entity = MemberInfoEntity.fromEntityNode(node, Role.OWNER);

        assertEquals(USER_EMAIL_LOWER, entity.getEmail());
        assertEquals(Role.OWNER.getValue(), entity.getRole());
        assertEquals(PARTITION_ID, entity.getPartitionId());
    }

    @Test
    void toChildrenReference_lowercasesEmailAndUsesUserNodeType() {
        MemberInfoEntity entity = MemberInfoEntity.builder()
                .id(1L)
                .email(USER_EMAIL_MIXED_CASE)
                .partitionId(PARTITION_ID)
                .role(Role.OWNER.getValue())
                .build();

        ChildrenReference ref = entity.toChildrenReference();

        assertEquals(USER_EMAIL_LOWER, ref.getId());
        assertEquals(Role.OWNER, ref.getRole());
        assertEquals(NodeType.USER, ref.getType());
        assertEquals(PARTITION_ID, ref.getDataPartitionId());
    }

    @Test
    void toChildrenReference_invalidRoleString_propagatesEnumValueOfException() {
        // Explicit failure mode: an unrecognized role from the DB must not be silently ignored.
        MemberInfoEntity entity = MemberInfoEntity.builder()
                .id(1L)
                .email(USER_EMAIL_LOWER)
                .partitionId(PARTITION_ID)
                .role("NON_EXISTENT_ROLE")
                .build();

        assertThrows(IllegalArgumentException.class, entity::toChildrenReference);
    }
}
