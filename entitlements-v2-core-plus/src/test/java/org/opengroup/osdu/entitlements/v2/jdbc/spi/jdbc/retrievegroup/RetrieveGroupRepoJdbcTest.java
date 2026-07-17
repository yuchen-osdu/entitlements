/*
 * Copyright 2024 Google LLC
 * Copyright 2024 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.retrievegroup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.entitlements.v2.jdbc.JdbcAppProperties;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntity;
import org.opengroup.osdu.entitlements.v2.jdbc.model.MemberInfoEntity;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.SpiJdbcTestConfig;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.GroupRepository;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.JdbcTemplateRunner;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.MemberRepository;
import org.opengroup.osdu.entitlements.v2.logging.AuditLogger;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.util.JdbcTestDataProvider.*;
import static org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.util.JdbcTestDataProvider.getUsersGroupNode;
import static org.powermock.api.mockito.PowerMockito.when;

@SpringBootTest(classes = SpiJdbcTestConfig.class)
@ExtendWith(SpringExtension.class)
class RetrieveGroupRepoJdbcTest {

  @MockBean
  protected AuditLogger auditLogger;

  @Autowired
  private RetrieveGroupRepoJdbc sut;
  @MockBean
  private GroupRepository groupRepository;
  @MockBean
  private MemberRepository memberRepository;
  @MockBean
  private JdbcTemplateRunner jdbcTemplateRunner;
  @MockBean
  private JdbcAppProperties config;

  @Test
  void shouldThrow404IfGroupDoesNotExist() {
    try {
      sut.groupExistenceValidation("users.test@group.com", "dp");
      fail("Should throw exception");
    } catch (AppException ex) {
      assertEquals(404, ex.getError().getCode());
    } catch (Exception ex) {
      fail(String.format("Should not throw exception: %s", ex.getMessage()));
    }
  }

  @Test
  void shouldReturnEmptyListIfNotParentsWhenLoadDirectParents() {
    EntityNode member = getMemberNode("member");

    jdbcTemplateRunner.saveMemberInfoEntity(MemberInfoEntity.fromEntityNode(member, Role.MEMBER));

    List<ParentReference> parents = sut.loadDirectParents(DATA_PARTITION_ID, member.getNodeId());
    assertEquals(0, parents.size());
  }


  @Test
  void shouldReturnDirectParentListWhenLoadDirectParents() {
    EntityNode member = getMemberNode("member");
    EntityNode group1 = getUsersGroupNode("x");
    EntityNode group2 = getUsersGroupNode("y");
    EntityNode group3 = getUsersGroupNode("z");

    GroupInfoEntity savedGroup1 = GroupInfoEntity.fromEntityNode(group1);
    GroupInfoEntity savedGroup2 = GroupInfoEntity.fromEntityNode(group2);
    GroupInfoEntity savedGroup3 = GroupInfoEntity.fromEntityNode(group3);

    when(memberRepository.findByEmail(any())).thenReturn(
        Collections.singletonList(MemberInfoEntity.fromEntityNode(member, Role.MEMBER)));
    when(groupRepository.findDirectGroups(anyList())).thenReturn(
        Arrays.asList(savedGroup1, savedGroup3));

    List<String> parentIds = sut.loadDirectParents(DATA_PARTITION_ID, member.getNodeId()).stream()
        .map(ParentReference::getId)
        .toList();
    assertEquals(2, parentIds.size());

    assertTrue(parentIds.contains(group1.getNodeId()));
    assertTrue(parentIds.contains(group3.getNodeId()));

    assertFalse(parentIds.contains(group2.getNodeId()));
  }



  @Test
  void shouldReturnFalseWhenNoDirectChildInTenant() {
    EntityNode groupNode = getUsersGroupNode("x", "tenant-1");
    ChildrenReference childrenReference = getUserChildrenReference("test@email.com", "tenant-2");
    GroupInfoEntity savedGroup = GroupInfoEntity.fromEntityNode(groupNode);

    when(groupRepository.findByEmail(groupNode.getNodeId())).thenReturn(
        Collections.singletonList(savedGroup));
    when(memberRepository.findMemberByEmailInGroup(savedGroup.getId(),
        childrenReference.getId())).thenReturn(Collections.emptyList());

    assertFalse(sut.hasDirectChild(groupNode, childrenReference));
  }

  @Test
  void shouldLoadDirectParentsForUserEmail() {
    String userEmail = "user@example.com";
    EntityNode parentGroup1 = getUsersGroupNode("parent1");
    EntityNode parentGroup2 = getUsersGroupNode("parent2");

    MemberInfoEntity memberEntity = MemberInfoEntity.builder()
        .id(1L)
        .email(userEmail)
        .build();

    when(memberRepository.findByEmail(userEmail)).thenReturn(Collections.singletonList(memberEntity));
    when(groupRepository.findDirectGroups(Collections.singletonList(1L))).thenReturn(
        Arrays.asList(
            GroupInfoEntity.fromEntityNode(parentGroup1),
            GroupInfoEntity.fromEntityNode(parentGroup2)
        ));

    List<ParentReference> parents = sut.loadDirectParents(DATA_PARTITION_ID, userEmail);

    assertEquals(2, parents.size());
    assertTrue(parents.stream().anyMatch(p -> p.getId().equals(parentGroup1.getNodeId())));
    assertTrue(parents.stream().anyMatch(p -> p.getId().equals(parentGroup2.getNodeId())));
  }

  @Test
  void shouldLoadDirectParentsForGroupEmail() {
    EntityNode childGroup = getUsersGroupNode("child");
    EntityNode parentGroup = getUsersGroupNode("parent");

    GroupInfoEntity childGroupEntity = GroupInfoEntity.fromEntityNode(childGroup);
    childGroupEntity.setId(10L);

    when(memberRepository.findByEmail(childGroup.getNodeId())).thenReturn(Collections.emptyList());
    when(config.getDomain()).thenReturn("group.com");
    when(groupRepository.findByEmail(childGroup.getNodeId())).thenReturn(Collections.singletonList(childGroupEntity));
    when(groupRepository.findDirectParents(Collections.singletonList(10L))).thenReturn(
        Collections.singletonList(GroupInfoEntity.fromEntityNode(parentGroup)));

    List<ParentReference> parents = sut.loadDirectParents(DATA_PARTITION_ID, childGroup.getNodeId());

    assertEquals(1, parents.size());
    assertEquals(parentGroup.getNodeId(), parents.get(0).getId());
  }

  @Test
  void shouldReturnDirectChildGroupsAndMembersWhenLoadDirectChildren() {
    EntityNode parentGroup = getUsersGroupNode("parent");
    GroupInfoEntity parentEntity = GroupInfoEntity.fromEntityNode(parentGroup);
    parentEntity.setId(1L);

    EntityNode childGroup1 = getUsersGroupNode("child1");
    EntityNode childGroup2 = getUsersGroupNode("child2");
    GroupInfoEntity childEntity1 = GroupInfoEntity.fromEntityNode(childGroup1);
    GroupInfoEntity childEntity2 = GroupInfoEntity.fromEntityNode(childGroup2);

    MemberInfoEntity member1 = MemberInfoEntity.builder()
        .id(10L)
        .email("member1@dp.group.com")
        .partitionId(DATA_PARTITION_ID)
        .role(Role.MEMBER.getValue())
        .build();

    when(groupRepository.findByEmail(parentGroup.getNodeId()))
        .thenReturn(Collections.singletonList(parentEntity));
    when(groupRepository.findDirectChildren(Collections.singletonList(1L)))
        .thenReturn(Arrays.asList(childEntity1, childEntity2));
    when(memberRepository.findMembersByGroup(1L))
        .thenReturn(Collections.singletonList(member1));

    List<ChildrenReference> children =
        sut.loadDirectChildren(DATA_PARTITION_ID, parentGroup.getNodeId());

    assertEquals(3, children.size());

    List<String> childIds = children.stream().map(ChildrenReference::getId).toList();
    assertTrue(childIds.contains(childGroup1.getNodeId()));
    assertTrue(childIds.contains(childGroup2.getNodeId()));
    assertTrue(childIds.contains("member1@dp.group.com"));

    assertTrue(children.stream()
        .filter(ChildrenReference::isGroup)
        .map(ChildrenReference::getId)
        .toList()
        .containsAll(Arrays.asList(childGroup1.getNodeId(), childGroup2.getNodeId())));
    assertTrue(children.stream().anyMatch(
        ref -> "member1@dp.group.com".equals(ref.getId()) && !ref.isGroup()));
  }

  @Test
  void shouldReturnOnlyChildGroupsWhenGroupHasNoMembers() {
    EntityNode parentGroup = getUsersGroupNode("parent");
    GroupInfoEntity parentEntity = GroupInfoEntity.fromEntityNode(parentGroup);
    parentEntity.setId(1L);

    EntityNode childGroup = getUsersGroupNode("child");
    GroupInfoEntity childEntity = GroupInfoEntity.fromEntityNode(childGroup);

    when(groupRepository.findByEmail(parentGroup.getNodeId()))
        .thenReturn(Collections.singletonList(parentEntity));
    when(groupRepository.findDirectChildren(Collections.singletonList(1L)))
        .thenReturn(Collections.singletonList(childEntity));
    when(memberRepository.findMembersByGroup(1L))
        .thenReturn(Collections.emptyList());

    List<ChildrenReference> children =
        sut.loadDirectChildren(DATA_PARTITION_ID, parentGroup.getNodeId());

    assertEquals(1, children.size());
    assertEquals(childGroup.getNodeId(), children.get(0).getId());
    assertTrue(children.get(0).isGroup());
  }

  @Test
  void shouldReturnOnlyMembersWhenGroupHasNoChildGroups() {
    EntityNode parentGroup = getUsersGroupNode("parent");
    GroupInfoEntity parentEntity = GroupInfoEntity.fromEntityNode(parentGroup);
    parentEntity.setId(1L);

    MemberInfoEntity member1 = MemberInfoEntity.builder()
        .id(10L)
        .email("member1@dp.group.com")
        .partitionId(DATA_PARTITION_ID)
        .role(Role.MEMBER.getValue())
        .build();

    when(groupRepository.findByEmail(parentGroup.getNodeId()))
        .thenReturn(Collections.singletonList(parentEntity));
    when(groupRepository.findDirectChildren(Collections.singletonList(1L)))
        .thenReturn(Collections.emptyList());
    when(memberRepository.findMembersByGroup(1L))
        .thenReturn(Collections.singletonList(member1));

    List<ChildrenReference> children =
        sut.loadDirectChildren(DATA_PARTITION_ID, parentGroup.getNodeId());

    assertEquals(1, children.size());
    assertEquals("member1@dp.group.com", children.get(0).getId());
    assertFalse(children.get(0).isGroup());
  }

  @Test
  void shouldReturnEmptyListWhenNodeDoesNotResolveToGroup() {
    String unknownEmail = "not.a.group@dp.group.com";

    when(groupRepository.findByEmail(unknownEmail)).thenReturn(Collections.emptyList());

    List<ChildrenReference> children = sut.loadDirectChildren(DATA_PARTITION_ID, unknownEmail);

    assertTrue(children.isEmpty());
    // With no resolved group, the empty-parentIds short-circuit must fire: no
    // child-group lookup (avoids the non-portable empty-IN query) and no member
    // fan-out.
    verify(groupRepository, never()).findDirectChildren(any());
    verify(memberRepository, never()).findMembersByGroup(anyLong());
  }
}
