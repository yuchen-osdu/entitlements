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

package org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.removemember;

import org.opengroup.osdu.entitlements.v2.azure.service.GraphTraversalSourceUtilService;
import org.opengroup.osdu.entitlements.v2.azure.service.RemoveEdgeDto;
import org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.constant.EdgePropertyNames;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.removemember.RemoveMemberServiceDto;
import org.opengroup.osdu.entitlements.v2.spi.removemember.RemoveMemberRepo;
import org.opengroup.osdu.entitlements.v2.spi.retrievegroup.RetrieveGroupRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class RemoveMemberRepoGremlin implements RemoveMemberRepo {

    @Autowired
    private GraphTraversalSourceUtilService graphTraversalSourceUtilService;

    @Autowired
    private RetrieveGroupRepo retrieveGroupRepo;

    /**
     * Removes child edge and parent edge
     */
    @Override
    public Set<String> removeMember(EntityNode groupNode, EntityNode memberNode, RemoveMemberServiceDto removeMemberServiceDto) {
        return executeRemoveMemberOperation(groupNode, memberNode);
    }

    private Set<String> executeRemoveMemberOperation(EntityNode groupNode, EntityNode memberNode) {
        List<String> impactedUsers = retrieveGroupRepo.loadAllChildrenUsers(memberNode).getChildrenUserIds();
        RemoveEdgeDto removeChildEdgeDto = RemoveEdgeDto.builder()
                .fromNodeId(groupNode.getNodeId())
                .fromDataPartitionId(groupNode.getDataPartitionId())
                .toNodeId(memberNode.getNodeId())
                .toDataPartitionId(memberNode.getDataPartitionId())
                .edgeLabel(EdgePropertyNames.CHILD_EDGE_LB)
                .build();
        RemoveEdgeDto removeParentEdgeDto = RemoveEdgeDto.builder()
                .fromNodeId(memberNode.getNodeId())
                .fromDataPartitionId(memberNode.getDataPartitionId())
                .toNodeId(groupNode.getNodeId())
                .toDataPartitionId(groupNode.getDataPartitionId())
                .edgeLabel(EdgePropertyNames.PARENT_EDGE_LB)
                .build();
        graphTraversalSourceUtilService.removeEdge(removeChildEdgeDto);
        graphTraversalSourceUtilService.removeEdge(removeParentEdgeDto);
        return (impactedUsers == null) ? Collections.emptySet() : new HashSet<>(impactedUsers);
    }
}
