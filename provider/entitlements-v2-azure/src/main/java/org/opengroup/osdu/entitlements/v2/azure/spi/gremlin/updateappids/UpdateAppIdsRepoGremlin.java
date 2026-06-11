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

package org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.updateappids;

import lombok.RequiredArgsConstructor;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality;
import org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.connection.GremlinConnector;
import org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.constant.VertexPropertyNames;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.spi.retrievegroup.RetrieveGroupRepo;
import org.opengroup.osdu.entitlements.v2.spi.updateappids.UpdateAppIdsRepo;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class UpdateAppIdsRepoGremlin implements UpdateAppIdsRepo {
    private final GremlinConnector gremlinConnector;
    private final RetrieveGroupRepo retrieveGroupRepo;

    @Override
    public Set<String> updateAppIds(EntityNode groupNode, Set<String> appIds) {
        return executeUpdateAppIdsOperation(groupNode, appIds);
    }

    private Set<String> executeUpdateAppIdsOperation(EntityNode groupNode, Set<String> appIds) {
        List<String> impactedUsers = retrieveGroupRepo.loadAllChildrenUsers(groupNode).getChildrenUserIds();
        GraphTraversal<Vertex, Vertex> traversal = gremlinConnector.getGraphTraversalSource().V()
                .has(VertexPropertyNames.NODE_ID, groupNode.getNodeId())
                .has(VertexPropertyNames.DATA_PARTITION_ID, groupNode.getDataPartitionId())
                .sideEffect(__.properties(VertexPropertyNames.APP_ID).drop())
                .barrier();
        appIds.forEach(appId -> traversal.property(Cardinality.list, VertexPropertyNames.APP_ID, appId));
        gremlinConnector.updateVertex(traversal);
        return (impactedUsers == null) ? Collections.emptySet() : new HashSet<>(impactedUsers);
    }
}
