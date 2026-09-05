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

package org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.addmember;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import org.opengroup.osdu.entitlements.v2.model.addmember.AddMemberRepoDto;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AddMemberRepoJdbcErrorPathTest {

    private static final String PARTITION_ID = "dp";

    @Mock
    private JaxRsDpsLog log;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private JdbcTemplateRunner jdbcTemplateRunner;

    @InjectMocks
    private AddMemberRepoJdbc sut;

    private static EntityNode groupNode(String name) {
        return EntityNode.builder()
                .nodeId(name + "@dp.group.com").type(NodeType.GROUP)
                .name(name).dataPartitionId(PARTITION_ID).build();
    }

    private static EntityNode memberNode(String name) {
        return EntityNode.builder()
                .nodeId(name + "@example.com").type(NodeType.USER)
                .name(name).dataPartitionId(PARTITION_ID).build();
    }

    @Test
    void addMember_asMember_existingMember_reusesId() {
        EntityNode member = memberNode("alice");
        EntityNode group = groupNode("data.a.viewers");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(member).role(Role.MEMBER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();

        // Existing member => must not create a new row.
        when(memberRepository.findByEmail(member.getNodeId())).thenReturn(
                Collections.singletonList(MemberInfoEntity.builder().id(11L)
                        .email(member.getNodeId()).partitionId(PARTITION_ID).build()));
        when(groupRepository.findByEmail(group.getNodeId())).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(50L).email(group.getNodeId())
                        .name(group.getName()).partitionId(PARTITION_ID).appIds(Collections.emptySet())
                        .build()));

        Set<String> result = sut.addMember(group, dto);

        assertEquals(1, result.size());
        assertTrue(result.contains(member.getNodeId()));
        verify(jdbcTemplateRunner, org.mockito.Mockito.never()).saveMemberInfoEntity(any());
        verify(groupRepository).addMemberById(50L, 11L, Role.MEMBER.getValue());
    }

    @Test
    void addMember_asMember_missingMember_savesNewMember() {
        EntityNode member = memberNode("bob");
        EntityNode group = groupNode("data.a.viewers");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(member).role(Role.OWNER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();

        when(memberRepository.findByEmail(member.getNodeId())).thenReturn(Collections.emptyList());
        when(jdbcTemplateRunner.saveMemberInfoEntity(any(MemberInfoEntity.class))).thenReturn(22L);
        when(groupRepository.findByEmail(group.getNodeId())).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(60L).email(group.getNodeId())
                        .name(group.getName()).partitionId(PARTITION_ID).appIds(Collections.emptySet())
                        .build()));

        sut.addMember(group, dto);

        verify(jdbcTemplateRunner).saveMemberInfoEntity(any(MemberInfoEntity.class));
        verify(groupRepository).addMemberById(60L, 22L, Role.OWNER.getValue());
    }

    @Test
    void addMember_asMember_missingParentGroup_throwsDatabaseAccessException() {
        EntityNode member = memberNode("carol");
        EntityNode group = groupNode("data.z.viewers");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(member).role(Role.MEMBER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();
        when(memberRepository.findByEmail(member.getNodeId())).thenReturn(
                Collections.singletonList(MemberInfoEntity.builder().id(1L)
                        .email(member.getNodeId()).partitionId(PARTITION_ID).build()));
        // Parent group is not in database.
        when(groupRepository.findByEmail(group.getNodeId())).thenReturn(Collections.emptyList());

        assertThrows(DatabaseAccessException.class, () -> sut.addMember(group, dto));
    }

    @Test
    void addMember_asGroup_missingChildGroup_throwsDatabaseAccessException() {
        // When adding a group, the group's own record must also be present in the DB.
        EntityNode childGroup = groupNode("child.group");
        EntityNode parentGroup = groupNode("parent.group");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(childGroup).role(Role.MEMBER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();

        when(groupRepository.findByEmail(childGroup.getNodeId())).thenReturn(Collections.emptyList());

        assertThrows(DatabaseAccessException.class, () -> sut.addMember(parentGroup, dto));
    }

    @Test
    void addMember_asGroup_returnsAffectedMembersOfChildGroup() {
        // Group-as-member path: response set == affected members of the child group.
        EntityNode childGroup = groupNode("child.group");
        EntityNode parentGroup = groupNode("parent.group");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(childGroup).role(Role.MEMBER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();

        GroupInfoEntity childEntity = GroupInfoEntity.builder().id(1L).email(childGroup.getNodeId())
                .name(childGroup.getName()).partitionId(PARTITION_ID).appIds(Collections.emptySet())
                .build();
        GroupInfoEntity parentEntity = GroupInfoEntity.builder().id(2L).email(parentGroup.getNodeId())
                .name(parentGroup.getName()).partitionId(PARTITION_ID).appIds(Collections.emptySet())
                .build();

        when(groupRepository.findByEmail(childGroup.getNodeId())).thenReturn(
                Collections.singletonList(childEntity));
        when(groupRepository.findByEmail(parentGroup.getNodeId())).thenReturn(
                Collections.singletonList(parentEntity));

        Set<String> affected = new HashSet<>();
        affected.add("m1@example.com");
        affected.add("m2@example.com");
        when(jdbcTemplateRunner.getAffectedMembersForGroup(childGroup)).thenReturn(affected);

        Set<String> result = sut.addMember(parentGroup, dto);

        assertEquals(2, result.size());
        verify(groupRepository).addChildGroupById(2L, 1L);
    }

    @Test
    void addMember_duplicateKeyException_mapsToConflictWithClearMessage() {
        EntityNode member = memberNode("dupmember");
        EntityNode group = groupNode("data.a.viewers");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(member).role(Role.MEMBER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();
        when(memberRepository.findByEmail(member.getNodeId())).thenReturn(
                Collections.singletonList(MemberInfoEntity.builder().id(1L)
                        .email(member.getNodeId()).partitionId(PARTITION_ID).build()));
        when(groupRepository.findByEmail(group.getNodeId())).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(50L).email(group.getNodeId())
                        .name(group.getName()).partitionId(PARTITION_ID).appIds(Collections.emptySet())
                        .build()));
        doThrow(new DuplicateKeyException("already exists"))
                .when(groupRepository).addMemberById(anyLong(), anyLong(), anyString());

        DatabaseAccessException ex = assertThrows(DatabaseAccessException.class,
                () -> sut.addMember(group, dto));
        assertEquals(409, ex.getError().getCode());
        // Message should mention both the member and the group.
        assertTrue(ex.getError().getMessage().contains(member.getNodeId()));
        assertTrue(ex.getError().getMessage().contains(group.getNodeId()));
    }

    @Test
    void addMember_duplicateKeyAsCause_alsoMapsToConflict() {
        EntityNode member = memberNode("dupmember2");
        EntityNode group = groupNode("data.a.viewers");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(member).role(Role.MEMBER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();
        when(memberRepository.findByEmail(member.getNodeId())).thenReturn(
                Collections.singletonList(MemberInfoEntity.builder().id(1L)
                        .email(member.getNodeId()).partitionId(PARTITION_ID).build()));
        when(groupRepository.findByEmail(group.getNodeId())).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(50L).email(group.getNodeId())
                        .name(group.getName()).partitionId(PARTITION_ID).appIds(Collections.emptySet())
                        .build()));
        doThrow(new RuntimeException("wrapper", new DuplicateKeyException("dup")))
                .when(groupRepository).addMemberById(anyLong(), anyLong(), anyString());

        DatabaseAccessException ex = assertThrows(DatabaseAccessException.class,
                () -> sut.addMember(group, dto));
        assertEquals(409, ex.getError().getCode());
    }

    @Test
    void addMember_unrelatedRuntimeException_isPropagated() {
        // Non-DuplicateKey exceptions must NOT be rewritten as 409 CONFLICT.
        EntityNode member = memberNode("errmember");
        EntityNode group = groupNode("data.a.viewers");
        AddMemberRepoDto dto = AddMemberRepoDto.builder()
                .memberNode(member).role(Role.MEMBER)
                .existingParents(new HashSet<>()).partitionId(PARTITION_ID).build();
        when(memberRepository.findByEmail(member.getNodeId())).thenReturn(
                Collections.singletonList(MemberInfoEntity.builder().id(1L)
                        .email(member.getNodeId()).partitionId(PARTITION_ID).build()));
        when(groupRepository.findByEmail(group.getNodeId())).thenReturn(
                Collections.singletonList(GroupInfoEntity.builder().id(50L).email(group.getNodeId())
                        .name(group.getName()).partitionId(PARTITION_ID).appIds(Collections.emptySet())
                        .build()));
        doThrow(new IllegalStateException("boom"))
                .when(groupRepository).addMemberById(anyLong(), anyLong(), anyString());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> sut.addMember(group, dto));
        assertEquals("boom", thrown.getMessage());
    }
}
