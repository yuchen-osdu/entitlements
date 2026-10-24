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

package org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.creategroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.entitlements.v2.jdbc.exception.DatabaseAccessException;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntity;
import org.opengroup.osdu.entitlements.v2.jdbc.model.MemberInfoEntity;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.GroupRepository;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.JdbcTemplateRunner;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.MemberRepository;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.NodeType;
import org.opengroup.osdu.entitlements.v2.model.Role;
import org.opengroup.osdu.entitlements.v2.model.creategroup.CreateGroupRepoDto;
import org.opengroup.osdu.entitlements.v2.service.GroupCacheService;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class CreateGroupRepoJdbcErrorPathTest {

    private static final String PARTITION_ID = "dp";

    @Mock
    private JaxRsDpsLog log;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private JdbcTemplateRunner jdbcTemplateRunner;
    @Mock
    private GroupCacheService groupCacheService;

    @InjectMocks
    private CreateGroupRepoJdbc sut;

    private static EntityNode group(String name) {
        return EntityNode.builder()
                .nodeId(name + "@dp.group.com")
                .type(NodeType.GROUP)
                .name(name)
                .dataPartitionId(PARTITION_ID)
                .build();
    }

    private static EntityNode requester() {
        return EntityNode.builder()
                .nodeId("requester@example.com")
                .type(NodeType.USER)
                .name("requester")
                .dataPartitionId(PARTITION_ID)
                .build();
    }

    @Test
    void createGroup_duplicateKeyDirectly_returnsConflict() {
        // groupRepository.save() throwing DuplicateKeyException must map to 409 CONFLICT.
        EntityNode groupNode = group("users.a");
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(requester())
                .addDataRootGroup(false)
                .partitionId(PARTITION_ID)
                .build();
        when(groupRepository.save(any(GroupInfoEntity.class)))
                .thenThrow(new DuplicateKeyException("already exists"));

        DatabaseAccessException ex = assertThrows(DatabaseAccessException.class,
                () -> sut.createGroup(groupNode, dto));

        assertEquals(409, ex.getError().getCode());
        assertTrue(ex.getError().getMessage().contains("already exists"));
    }

    @Test
    void createGroup_duplicateKeyAsCause_alsoMapsToConflict() {
        // Wrapped DuplicateKeyException (nested via cause) must also map to 409 CONFLICT.
        EntityNode groupNode = group("users.b");
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(requester())
                .addDataRootGroup(false)
                .partitionId(PARTITION_ID)
                .build();
        RuntimeException wrapped = new RuntimeException("wrapper", new DuplicateKeyException("dup"));
        when(groupRepository.save(any(GroupInfoEntity.class))).thenThrow(wrapped);

        DatabaseAccessException ex = assertThrows(DatabaseAccessException.class,
                () -> sut.createGroup(groupNode, dto));
        assertEquals(409, ex.getError().getCode());
    }

    @Test
    void createGroup_arbitraryRuntimeExceptionWithoutDuplicateCause_isPropagated() {
        // A non-duplicate error must NOT be swallowed as CONFLICT — it must bubble up as-is.
        EntityNode groupNode = group("users.c");
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(requester())
                .addDataRootGroup(false)
                .partitionId(PARTITION_ID)
                .build();
        when(groupRepository.save(any(GroupInfoEntity.class)))
                .thenThrow(new IllegalStateException("catastrophic"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> sut.createGroup(groupNode, dto));
        assertEquals("catastrophic", thrown.getMessage());
    }

    @Test
    void createGroup_happyPath_reusesExistingRequesterMember_doesNotSaveNewMember() {
        // If the requester exists as a member row, memberRepository.findByEmail returns it and
        // saveMemberInfoEntity must NOT be invoked. Verifies the "requester.isPresent()" branch.
        EntityNode groupNode = group("users.d");
        EntityNode req = requester();
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(req)
                .addDataRootGroup(false)
                .partitionId(PARTITION_ID)
                .build();

        GroupInfoEntity saved = GroupInfoEntity.builder()
                .id(50L).email(groupNode.getNodeId()).name("users.d")
                .partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();
        when(groupRepository.save(any(GroupInfoEntity.class))).thenReturn(saved);

        MemberInfoEntity existingRequester = MemberInfoEntity.builder()
                .id(77L).email(req.getNodeId()).partitionId(PARTITION_ID).build();
        when(memberRepository.findByEmail(req.getNodeId()))
                .thenReturn(Collections.singletonList(existingRequester));

        Set<String> result = sut.createGroup(groupNode, dto);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(req.getNodeId()));
        // Existing member reused → no insert into member table.
        verify(jdbcTemplateRunner, org.mockito.Mockito.never()).saveMemberInfoEntity(any());
        verify(groupRepository).addMemberById(50L, 77L, Role.OWNER.getValue());
    }

    @Test
    void createGroup_happyPath_missingRequesterMember_createsAndReturnsIt() {
        // When findByEmail is empty, code must create a new MemberInfoEntity via jdbcTemplateRunner.
        EntityNode groupNode = group("users.e");
        EntityNode req = requester();
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(req)
                .addDataRootGroup(false)
                .partitionId(PARTITION_ID)
                .build();

        GroupInfoEntity saved = GroupInfoEntity.builder()
                .id(60L).email(groupNode.getNodeId()).name("users.e")
                .partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();
        when(groupRepository.save(any(GroupInfoEntity.class))).thenReturn(saved);
        when(memberRepository.findByEmail(req.getNodeId())).thenReturn(Collections.emptyList());
        when(jdbcTemplateRunner.saveMemberInfoEntity(any(MemberInfoEntity.class))).thenReturn(88L);

        sut.createGroup(groupNode, dto);

        verify(jdbcTemplateRunner).saveMemberInfoEntity(any(MemberInfoEntity.class));
        verify(groupRepository).addMemberById(60L, 88L, Role.OWNER.getValue());
    }

    @Test
    void createGroup_withAddDataRootGroup_missingRootGroup_throwsNotFound() {
        // dataRoot lookup returns empty => must throw a 404 DatabaseAccessException.
        EntityNode groupNode = group("users.f");
        EntityNode req = requester();
        EntityNode dataRoot = group("users.data.root");
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(req)
                .addDataRootGroup(true)
                .dataRootGroupNode(dataRoot)
                .partitionId(PARTITION_ID)
                .build();

        GroupInfoEntity saved = GroupInfoEntity.builder()
                .id(70L).email(groupNode.getNodeId()).name("users.f")
                .partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();
        when(groupRepository.save(any(GroupInfoEntity.class))).thenReturn(saved);
        when(memberRepository.findByEmail(req.getNodeId()))
                .thenReturn(Collections.singletonList(MemberInfoEntity.builder().id(1L)
                        .email(req.getNodeId()).partitionId(PARTITION_ID).build()));
        // Data root group is looked up but not found.
        when(groupRepository.findByEmail(dataRoot.getNodeId())).thenReturn(Collections.emptyList());

        DatabaseAccessException ex = assertThrows(DatabaseAccessException.class,
                () -> sut.createGroup(groupNode, dto));
        assertEquals(404, ex.getError().getCode());
    }

    @Test
    void createGroup_withAddDataRootGroup_wiresChildLinkAndRefreshesCache() {
        // Happy path with dataRoot: verify addChildGroupById is called with (child, root) IDs
        // AND refreshListGroupCache is invoked with the affected member set.
        EntityNode groupNode = group("users.g");
        EntityNode req = requester();
        EntityNode dataRoot = group("users.data.root");
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(req)
                .addDataRootGroup(true)
                .dataRootGroupNode(dataRoot)
                .partitionId(PARTITION_ID)
                .build();

        GroupInfoEntity saved = GroupInfoEntity.builder()
                .id(80L).email(groupNode.getNodeId()).name("users.g")
                .partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();
        GroupInfoEntity rootEntity = GroupInfoEntity.builder()
                .id(999L).email(dataRoot.getNodeId()).name("users.data.root")
                .partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();

        when(groupRepository.save(any(GroupInfoEntity.class))).thenReturn(saved);
        when(memberRepository.findByEmail(req.getNodeId()))
                .thenReturn(Collections.singletonList(MemberInfoEntity.builder().id(1L)
                        .email(req.getNodeId()).partitionId(PARTITION_ID).build()));
        when(groupRepository.findByEmail(dataRoot.getNodeId()))
                .thenReturn(Collections.singletonList(rootEntity));
        Set<String> affected = new HashSet<>();
        affected.add("member1@example.com");
        affected.add("member2@example.com");
        when(jdbcTemplateRunner.getAffectedMembersForGroup(dataRoot)).thenReturn(affected);

        sut.createGroup(groupNode, dto);

        verify(groupRepository).addChildGroupById(80L, 999L);
        verify(groupCacheService).refreshListGroupCache(affected, PARTITION_ID);
    }

    @Test
    void createGroup_addMemberByIdThrowsUnrelated_isPropagatedNotConflated() {
        // Downstream group-repo failure that isn't a DuplicateKeyException must NOT be forced to 409.
        EntityNode groupNode = group("users.h");
        EntityNode req = requester();
        CreateGroupRepoDto dto = CreateGroupRepoDto.builder()
                .requesterNode(req)
                .addDataRootGroup(false)
                .partitionId(PARTITION_ID)
                .build();
        GroupInfoEntity saved = GroupInfoEntity.builder()
                .id(90L).email(groupNode.getNodeId()).name("users.h")
                .partitionId(PARTITION_ID).appIds(Collections.emptySet()).build();
        when(groupRepository.save(any(GroupInfoEntity.class))).thenReturn(saved);
        when(memberRepository.findByEmail(req.getNodeId()))
                .thenReturn(Collections.singletonList(MemberInfoEntity.builder().id(1L)
                        .email(req.getNodeId()).partitionId(PARTITION_ID).build()));
        doThrow(new RuntimeException("boom (not duplicate)"))
                .when(groupRepository).addMemberById(anyLong(), anyLong(), anyString());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> sut.createGroup(groupNode, dto));
        assertTrue(thrown.getMessage().contains("boom"));
    }
}
