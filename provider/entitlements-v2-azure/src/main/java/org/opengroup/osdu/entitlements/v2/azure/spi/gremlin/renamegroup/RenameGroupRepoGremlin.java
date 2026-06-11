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

package org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.renamegroup;

import lombok.RequiredArgsConstructor;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.connection.GremlinConnector;
import org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.constant.VertexPropertyNames;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.spi.renamegroup.RenameGroupRepo;
import org.opengroup.osdu.entitlements.v2.spi.retrievegroup.RetrieveGroupRepo;
import org.opengroup.osdu.entitlements.v2.util.GroupCreationUtil;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RenameGroupRepoGremlin implements RenameGroupRepo {

    private final RetrieveGroupRepo retrieveGroupRepo;
    private final GremlinConnector gremlinConnector;

    @Override
    public Set<String> run(EntityNode groupNode, String newGroupName) {
        return executeRenameGroupOperation(groupNode, newGroupName);
    }

    private Set<String> executeRenameGroupOperation(EntityNode groupNode, String newGroupName) {
        List<String> impactedUsers = new ArrayList<>();
        impactedUsers.addAll(retrieveGroupRepo.loadAllChildrenUsers(groupNode).getChildrenUserIds());
        String partitionDomain = groupNode.getNodeId().split("@")[1];
        String newNodeId = GroupCreationUtil.createGroupEmail(newGroupName, partitionDomain);
        Traversal<Vertex, Vertex> traversal = gremlinConnector.getGraphTraversalSource().V()
                .has(VertexPropertyNames.DATA_PARTITION_ID, groupNode.getDataPartitionId())
                .has(VertexPropertyNames.NODE_ID, groupNode.getNodeId())
                .property(VertexProperty.Cardinality.single, VertexPropertyNames.NODE_ID, newNodeId)
                .property(VertexProperty.Cardinality.single, VertexPropertyNames.NAME, newGroupName);
        gremlinConnector.updateVertex(traversal);
        return new HashSet<>(impactedUsers);
    }
}
