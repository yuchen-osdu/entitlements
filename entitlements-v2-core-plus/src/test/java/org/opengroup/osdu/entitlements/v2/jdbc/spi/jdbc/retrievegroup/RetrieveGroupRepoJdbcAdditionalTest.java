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

package org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.retrievegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.entitlements.v2.jdbc.JdbcAppProperties;
import org.opengroup.osdu.entitlements.v2.jdbc.exception.DatabaseAccessException;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntity;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntityList;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.GroupRepository;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.JdbcTemplateRunner;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.MemberRepository;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.GroupType;
import org.opengroup.osdu.entitlements.v2.model.NodeType;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.model.Role;
import org.opengroup.osdu.entitlements.v2.model.listgroup.ListGroupsOfPartitionDto;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RetrieveGroupRepoJdbcAdditionalTest {

    private static final String PARTITION_ID = "dp";
    private static final String DOMAIN = "group.com";
    private static final String GROUP_EMAIL = "users.data.root@dp.group.com";
    private static final String USER_EMAIL = "user@example.com";

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private JdbcTemplateRunner jdbcTemplateRunner;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private JdbcAppProperties config;

    @InjectMocks
    private RetrieveGroupRepoJdbc sut;

    @BeforeEach
    void setUp() {
        lenient().when(config.getDomain()).thenReturn(DOMAIN);
    }

    @Test
    void getEntityNode_groupFound_returnsPresentOptional() {
        GroupInfoEntity g = GroupInfoEntity.builder()
                .id(1L).email(GROUP_EMAIL).name("users.data.root")
                .partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();
        when(groupRepository.findByEmail(GROUP_EMAIL)).thenReturn(Collections.singletonList(g));

        Optional<EntityNode> result = sut.getEntityNode(GROUP_EMAIL, PARTITION_ID);

        assertTrue(result.isPresent());
        assertEquals(GROUP_EMAIL, result.get().getNodeId());
    }

    @Test
    void getEntityNode_groupNotFound_returnsEmptyOptional() {
        when(groupRepository.findByEmail(GROUP_EMAIL)).thenReturn(Collections.emptyList());

        Optional<EntityNode> result = sut.getEntityNode(GROUP_EMAIL, PARTITION_ID);

        assertFalse(result.isPresent());
    }

    @Test
    void getMemberNodeForRemovalFromGroup_userEmail_producesUserNode() {
        EntityNode node = sut.getMemberNodeForRemovalFromGroup(USER_EMAIL, PARTITION_ID);
        assertEquals(NodeType.USER, node.getType());
        assertEquals(USER_EMAIL, node.getNodeId());
    }

    @Test
    void getMemberNodeForRemovalFromGroup_groupEmail_producesGroupNode() {
        EntityNode node = sut.getMemberNodeForRemovalFromGroup(GROUP_EMAIL, PARTITION_ID);
        assertEquals(NodeType.GROUP, node.getType());
        assertEquals(GROUP_EMAIL, node.getNodeId());
    }

    @Test
    void hasDirectChild_missingParentGroup_throwsDatabaseAccessException() {
        EntityNode groupNode = EntityNode.builder()
                .nodeId(GROUP_EMAIL).type(NodeType.GROUP).name("users.data.root")
                .dataPartitionId(PARTITION_ID).build();
        ChildrenReference child = ChildrenReference.builder()
                .id("child@example.com").type(NodeType.USER)
                .role(Role.MEMBER).dataPartitionId(PARTITION_ID).build();
        when(groupRepository.findByEmail(GROUP_EMAIL)).thenReturn(Collections.emptyList());

        // Parent group absent => 404 mapped to DatabaseAccessException.createNotFound
        assertThrows(DatabaseAccessException.class,
                () -> sut.hasDirectChild(groupNode, child));
    }

    @Test
    void hasDirectChild_memberInGroup_returnsTrue() {
        EntityNode groupNode = EntityNode.builder()
                .nodeId(GROUP_EMAIL).type(NodeType.GROUP).name("users.data.root")
                .dataPartitionId(PARTITION_ID).build();
        ChildrenReference memberChild = ChildrenReference.builder()
                .id("member@example.com").type(NodeType.USER)
                .role(Role.MEMBER).dataPartitionId(PARTITION_ID).build();
        GroupInfoEntity parent = GroupInfoEntity.builder().id(10L).email(GROUP_EMAIL)
                .name("users.data.root").partitionId(PARTITION_ID).appIds(Collections.emptySet())
                .build();
        when(groupRepository.findByEmail(GROUP_EMAIL)).thenReturn(Collections.singletonList(parent));
        when(memberRepository.findMemberByEmailInGroup(10L, "member@example.com"))
                .thenReturn(Collections.singletonList(
                        org.opengroup.osdu.entitlements.v2.jdbc.model.MemberInfoEntity.builder()
                                .email("member@example.com").role("MEMBER").build()));

        assertTrue(sut.hasDirectChild(groupNode, memberChild));
    }

    @Test
    void hasDirectChild_childGroupInGroup_returnsTrueViaGroupPath() {
        EntityNode groupNode = EntityNode.builder()
                .nodeId(GROUP_EMAIL).type(NodeType.GROUP).name("users.data.root")
                .dataPartitionId(PARTITION_ID).build();
        // Child is another group — exercise the hasChildGroupInGroup branch.
        ChildrenReference groupChild = ChildrenReference.builder()
                .id("child@dp.group.com").type(NodeType.GROUP)
                .role(Role.MEMBER).dataPartitionId(PARTITION_ID).build();
        GroupInfoEntity parent = GroupInfoEntity.builder().id(10L).email(GROUP_EMAIL)
                .name("users.data.root").partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();
        when(groupRepository.findByEmail(GROUP_EMAIL)).thenReturn(Collections.singletonList(parent));
        when(groupRepository.findChildByEmail(10L, "child@dp.group.com"))
                .thenReturn(Collections.singletonList(parent));

        assertTrue(sut.hasDirectChild(groupNode, groupChild));
    }

    @Test
    void loadAllChildrenUsers_returnsEmptyChildrenTreeDto() {
        EntityNode node = EntityNode.builder()
                .nodeId(GROUP_EMAIL).type(NodeType.GROUP).name("users.data.root")
                .dataPartitionId(PARTITION_ID).build();
        assertNotNull(sut.loadAllChildrenUsers(node));
        assertTrue(sut.loadAllChildrenUsers(node).getChildrenUserIds().isEmpty());
    }

    @Test
    void filterParentsByAppId_returnsAllRefs_whenAppIdsEmpty() {
        // A reference with no restricted appIds must always be included.
        ParentReference open = ParentReference.builder().id("g1@dp.group.com").name("g1")
                .description("d").dataPartitionId(PARTITION_ID).appIds(new HashSet<>()).build();
        ParentReference restricted = ParentReference.builder().id("g2@dp.group.com").name("g2")
                .description("d").dataPartitionId(PARTITION_ID)
                .appIds(new HashSet<>(Collections.singletonList("app-x"))).build();

        Set<ParentReference> input = new HashSet<>(Arrays.asList(open, restricted));
        Set<ParentReference> matchingAppX = sut.filterParentsByAppId(input, PARTITION_ID, "app-x");
        Set<ParentReference> otherApp = sut.filterParentsByAppId(input, PARTITION_ID, "app-y");

        // Match on appId returns both. Non-match keeps the "open" one only.
        assertEquals(2, matchingAppX.size());
        assertEquals(1, otherApp.size());
        assertTrue(otherApp.contains(open));
    }

    @Test
    void getGroupsInPartition_nullCursor_offsetZero_advancesCursorByLimit() {
        GroupInfoEntityList backing = GroupInfoEntityList.builder()
                .totalCount(10L)
                .groupInfoEntities(Collections.emptyList())
                .build();
        when(jdbcTemplateRunner.getGroupsInPartition(PARTITION_ID, GroupType.USER,
                0, 50)).thenReturn(backing);

        ListGroupsOfPartitionDto result = sut.getGroupsInPartition(PARTITION_ID, GroupType.USER, null, 50);

        assertEquals(10L, result.getTotalCount());
        // With null cursor and limit=50, cursor advances to 50.
        assertEquals("50", result.getCursor());
    }

    @Test
    void getGroupsInPartition_emptyCursor_treatedAsOffsetZero() {
        GroupInfoEntityList backing = GroupInfoEntityList.builder()
                .totalCount(0L).groupInfoEntities(Collections.emptyList()).build();
        when(jdbcTemplateRunner.getGroupsInPartition(PARTITION_ID, GroupType.USER,
                0, 25)).thenReturn(backing);

        ListGroupsOfPartitionDto result = sut.getGroupsInPartition(PARTITION_ID, GroupType.USER, "", 25);

        assertEquals("25", result.getCursor());
    }

    @Test
    void getGroupsInPartition_validCursor_addsLimitToCursor() {
        GroupInfoEntityList backing = GroupInfoEntityList.builder()
                .totalCount(100L).groupInfoEntities(Collections.emptyList()).build();
        when(jdbcTemplateRunner.getGroupsInPartition(PARTITION_ID, GroupType.USER,
                30, 20)).thenReturn(backing);

        ListGroupsOfPartitionDto result = sut.getGroupsInPartition(PARTITION_ID, GroupType.USER, "30", 20);

        // Offset 30 + limit 20 = next cursor "50".
        assertEquals("50", result.getCursor());
    }

    @Test
    void getGroupsInPartition_malformedCursor_throwsAppException400() {
        // A non-numeric cursor is a client error — must map to 400 AppException.
        AppException ex = assertThrows(AppException.class,
                () -> sut.getGroupsInPartition(PARTITION_ID, GroupType.USER, "not-a-number", 10));
        assertEquals(400, ex.getError().getCode());
    }

    @Test
    void groupExistenceValidation_missingGroup_throwsDatabaseAccessExceptionWith404() {
        // Reads the not-found path via the shared setUp domain mock — no findByEmail hits => 404.
        when(groupRepository.findByEmail(anyString())).thenReturn(Collections.emptyList());

        DatabaseAccessException ex = assertThrows(DatabaseAccessException.class,
                () -> sut.groupExistenceValidation("nonexistent@dp.group.com", PARTITION_ID));
        assertEquals(404, ex.getError().getCode());
    }

    @Test
    void groupExistenceValidation_groupFound_returnsEntityNode() {
        GroupInfoEntity g = GroupInfoEntity.builder().id(3L).email(GROUP_EMAIL)
                .name("users.data.root").partitionId(PARTITION_ID).appIds(Collections.emptySet())
                .build();
        when(groupRepository.findByEmail(GROUP_EMAIL)).thenReturn(Collections.singletonList(g));

        EntityNode result = sut.groupExistenceValidation(GROUP_EMAIL, PARTITION_ID);
        assertEquals(GROUP_EMAIL, result.getNodeId());
    }

    @Test
    void loadDirectParents_multipleNodeIds_returnsCombinedParents() {
        // Confirms varargs handling — the loop iterates over each nodeId in nodeIds.
        when(memberRepository.findByEmail(any())).thenReturn(Collections.emptyList());
        when(groupRepository.findByEmail("g1@dp.group.com")).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(1L).email("g1@dp.group.com")
                        .name("g1").partitionId(PARTITION_ID).appIds(Collections.emptySet()).build()));
        when(groupRepository.findByEmail("g2@dp.group.com")).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(2L).email("g2@dp.group.com")
                        .name("g2").partitionId(PARTITION_ID).appIds(Collections.emptySet()).build()));
        when(groupRepository.findDirectParents(anyList())).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(999L).email("parent@dp.group.com")
                        .name("parent").partitionId(PARTITION_ID).appIds(Collections.emptySet()).build()));

        List<ParentReference> parents = sut.loadDirectParents(PARTITION_ID,
                "g1@dp.group.com", "g2@dp.group.com");

        // Two group inputs; each maps to one parent lookup → 2 entries in output.
        assertEquals(2, parents.size());
    }
}
