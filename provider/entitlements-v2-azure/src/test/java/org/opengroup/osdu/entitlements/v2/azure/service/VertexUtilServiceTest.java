package org.opengroup.osdu.entitlements.v2.azure.service;

import com.google.gson.Gson;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.entitlements.v2.azure.model.NodeEdge;
import org.opengroup.osdu.entitlements.v2.azure.model.NodeVertex;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.NodeType;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.model.Role;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VertexUtilServiceTest {

    @InjectMocks
    private VertexUtilService vertexUtilService;

    private static final Gson GSON = new Gson();

    private NodeVertex groupVertex;
    private NodeVertex userVertex;
    private NodeEdge edge;

    @BeforeEach
    void setUp() {
        Map<String, List<Map<String, String>>> groupProperties = new HashMap<>();
        groupProperties.put("nodeId", Arrays.asList(createPropertyValue("group@example.com")));
        groupProperties.put("name", Arrays.asList(createPropertyValue("Test Group")));
        groupProperties.put("description", Arrays.asList(createPropertyValue("Test Description")));
        groupProperties.put("dataPartitionId", Arrays.asList(createPropertyValue("partition-1")));
        groupProperties.put("appId", Arrays.asList(
            createPropertyValue("app1"),
            createPropertyValue("app2")
        ));

        groupVertex = NodeVertex.builder()
                .label(NodeType.GROUP.toString())
                .properties(groupProperties)
                .build();

        Map<String, List<Map<String, String>>> userProperties = new HashMap<>();
        userProperties.put("nodeId", Arrays.asList(createPropertyValue("user@example.com")));
        userProperties.put("dataPartitionId", Arrays.asList(createPropertyValue("partition-1")));

        userVertex = NodeVertex.builder()
                .label(NodeType.USER.toString())
                .properties(userProperties)
                .build();

        Map<String, String> edgeProperties = new HashMap<>();
        edgeProperties.put("role", Role.MEMBER.getValue());

        edge = NodeEdge.builder()
                .properties(edgeProperties)
                .build();
    }

    private Map<String, String> createPropertyValue(String value) {
        Map<String, String> map = new HashMap<>();
        map.put("value", value);
        return map;
    }

    @Test
    void createMemberNode_shouldCreateGroupNodeWithAllFields() {
        EntityNode result = vertexUtilService.createMemberNode(groupVertex);

        assertNotNull(result);
        assertEquals(NodeType.GROUP, result.getType());
        assertEquals("group@example.com", result.getNodeId());
        assertEquals("Test Group", result.getName());
        assertEquals("Test Description", result.getDescription());
        assertEquals("partition-1", result.getDataPartitionId());
        assertEquals(2, result.getAppIds().size());
        assertTrue(result.getAppIds().contains("app1"));
        assertTrue(result.getAppIds().contains("app2"));
    }

    @Test
    void createMemberNode_shouldCreateUserNodeWithLimitedFields() {
        EntityNode result = vertexUtilService.createMemberNode(userVertex);

        assertNotNull(result);
        assertEquals(NodeType.USER, result.getType());
        assertEquals("user@example.com", result.getNodeId());
        assertEquals("partition-1", result.getDataPartitionId());
        // User nodes don't have name, description, or appIds set in the source vertex
    }

    @Test
    void createParentReference_shouldCreateReferenceWithAllFields() {
        ParentReference result = vertexUtilService.createParentReference(groupVertex);

        assertNotNull(result);
        assertEquals("group@example.com", result.getId());
        assertEquals("Test Group", result.getName());
        assertEquals("Test Description", result.getDescription());
        assertEquals("partition-1", result.getDataPartitionId());
        assertEquals(2, result.getAppIds().size());
        assertTrue(result.getAppIds().contains("app1"));
        assertTrue(result.getAppIds().contains("app2"));
    }

    @Test
    void createChildReference_shouldCreateReferenceFromMap() {
        Map<NodeVertex, NodeEdge> map = new HashMap<>();
        map.put(userVertex, edge);

        ChildrenReference result = vertexUtilService.createChildReference(map);

        assertNotNull(result);
        assertEquals("user@example.com", result.getId());
        assertEquals(Role.MEMBER, result.getRole());
        assertEquals(NodeType.USER, result.getType());
        assertEquals("partition-1", result.getDataPartitionId());
    }

    @Test
    void createChildReference_shouldReturnNullForEmptyMap() {
        Map<NodeVertex, NodeEdge> emptyMap = new HashMap<>();

        ChildrenReference result = vertexUtilService.createChildReference(emptyMap);

        assertNull(result);
    }
}
