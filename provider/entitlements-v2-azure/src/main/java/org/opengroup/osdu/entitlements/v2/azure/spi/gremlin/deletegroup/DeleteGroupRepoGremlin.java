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

package org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.deletegroup;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.connection.GremlinConnector;
import org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.constant.VertexPropertyNames;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.spi.Operation;
import org.opengroup.osdu.entitlements.v2.spi.deletegroup.DeleteGroupRepo;
import org.opengroup.osdu.entitlements.v2.spi.retrievegroup.RetrieveGroupRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class DeleteGroupRepoGremlin implements DeleteGroupRepo {

    @Autowired
    private GremlinConnector gremlinConnector;

    @Autowired
    private RetrieveGroupRepo retrieveGroupRepo;

    /**
     * Deleting a vertex removes all incoming and outgoing edges
     */
    @Override
    public Set<String> deleteGroup(final EntityNode groupNode) {
        return executeDeleteGroupOperation(groupNode);
    }

    @Override
    public Set<String> deleteGroup(Deque<Operation> executedCommandsDeque, EntityNode groupNode) {
        return new HashSet<>();
    }

    private Set<String> executeDeleteGroupOperation(final EntityNode groupNode) {
        List<String> impactedUsers = retrieveGroupRepo.loadAllChildrenUsers(groupNode).getChildrenUserIds();
        Traversal<Vertex, Vertex> traversal = gremlinConnector.getGraphTraversalSource().V()
                .has(VertexPropertyNames.NODE_ID, groupNode.getNodeId())
                .has(VertexPropertyNames.DATA_PARTITION_ID, groupNode.getDataPartitionId())
                .drop();
        gremlinConnector.removeVertex(traversal);
        return (impactedUsers == null) ? Collections.emptySet() : new HashSet<>(impactedUsers);
    }
}
