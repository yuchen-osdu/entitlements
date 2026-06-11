//  Copyright © Microsoft Corporation
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.removemember;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.util.JdbcTestDataProvider.DATA_PARTITION_ID;
import static org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.util.JdbcTestDataProvider.getDataViewersGroupNode;
import static org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.util.JdbcTestDataProvider.getMemberNode;
import static org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.util.JdbcTestDataProvider.getRequesterNode;
import static org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.util.JdbcTestDataProvider.getUsersGroupNode;

import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntity;
import org.opengroup.osdu.entitlements.v2.jdbc.model.MemberInfoEntity;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.SpiJdbcTestConfig;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.addmember.AddMemberRepoJdbc;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.GroupRepository;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.JdbcTemplateRunner;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.MemberRepository;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.Role;
import org.opengroup.osdu.entitlements.v2.model.removemember.RemoveMemberServiceDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = SpiJdbcTestConfig.class)
@RunWith(SpringRunner.class)
public class RemoveMemberRepoJdbcTest {

    @Autowired
    private RemoveMemberRepoJdbc sut;
    @MockBean
    private GroupRepository groupRepository;
    @MockBean
    private MemberRepository memberRepository;
    @MockBean
    private JdbcTemplateRunner jdbcTemplateRunner;
    @Autowired
    private AddMemberRepoJdbc addMemberRepoJdbc;

    @Test
    public void should_updateReference_WhenRemoveAUser_fromAGroup() {
        EntityNode requesterNode = getRequesterNode();
        EntityNode memberNode = getMemberNode("member");
        EntityNode groupNode = getDataViewersGroupNode("x");

        GroupInfoEntity savedGroup = GroupInfoEntity.fromEntityNode(groupNode);

        RemoveMemberServiceDto removeMemberServiceDto = RemoveMemberServiceDto.builder()
                .requesterId(requesterNode.getNodeId())
                .partitionId(DATA_PARTITION_ID)
                .childrenReference(ChildrenReference.createChildrenReference(memberNode, Role.MEMBER))
                .build();

        when(groupRepository.findByEmail(any())).thenReturn(Collections.singletonList(savedGroup));
        when(memberRepository.findByEmail(any())).thenReturn(Collections.singletonList(MemberInfoEntity.fromEntityNode(memberNode, Role.MEMBER)));

        //when
        sut.removeMember(groupNode, memberNode, removeMemberServiceDto);

        when(memberRepository.findByEmail(any())).thenReturn(Collections.emptyList());

        //then
        assertTrue(memberRepository.findMembersByGroup(savedGroup.getId()).isEmpty());
        assertTrue(memberRepository.findByEmail(memberNode.getNodeId()).isEmpty());
    }

    @Test
    public void should_updateReferences_whenRemovingAGroupFromAnotherGroup() {
        EntityNode requesterNode = getRequesterNode();
        EntityNode groupNode = getUsersGroupNode("x");
        EntityNode groupToRemoveNode = getUsersGroupNode("y");

        GroupInfoEntity savedGroup = GroupInfoEntity.fromEntityNode(groupNode);
        GroupInfoEntity savedGroupToRemove = GroupInfoEntity.fromEntityNode(groupToRemoveNode);

        RemoveMemberServiceDto removeMemberServiceDto = RemoveMemberServiceDto.builder()
                .requesterId(requesterNode.getNodeId())
                .partitionId(DATA_PARTITION_ID)
                .childrenReference(ChildrenReference.createChildrenReference(groupToRemoveNode, Role.MEMBER))
                .build();

        when(groupRepository.findByEmail(groupNode.getNodeId())).thenReturn(Collections.singletonList(savedGroup));
        when(groupRepository.findByEmail(groupToRemoveNode.getNodeId())).thenReturn(Collections.singletonList(savedGroupToRemove));

        //when
        sut.removeMember(groupNode, groupToRemoveNode, removeMemberServiceDto);

        when(groupRepository.findByEmail(groupToRemoveNode.getNodeId())).thenReturn(Collections.emptyList());

        //then
        assertTrue(groupRepository.findChildByEmail(savedGroup.getId(), groupToRemoveNode.getNodeId()).isEmpty());
        assertTrue(groupRepository.findDirectParents(Collections.singletonList(savedGroupToRemove.getId())).isEmpty());
    }
}
