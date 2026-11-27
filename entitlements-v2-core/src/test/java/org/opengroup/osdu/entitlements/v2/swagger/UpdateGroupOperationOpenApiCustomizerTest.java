package org.opengroup.osdu.entitlements.v2.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.opengroup.osdu.entitlements.v2.swagger.UpdateGroupOperationOpenApiCustomizer.SCHEMA_NAME;

public class UpdateGroupOperationOpenApiCustomizerTest {

    @Test
    public void constrainsNameValueToSingleItem() {
        Schema<?> updateGroup = new Schema<>();
        Map<String, Schema> properties = new HashMap<>();
        properties.put("path", new StringSchema()._enum(List.of("/name", "/appIds")));
        properties.put("value", new Schema<>().type("array").minItems(1).maxItems(Integer.MAX_VALUE));
        properties.put("op", new StringSchema()._enum(List.of("replace")));
        updateGroup.setProperties(properties);

        OpenAPI openAPI = new OpenAPI()
                .components(new Components().addSchemas(SCHEMA_NAME, updateGroup));
        UpdateGroupOperationOpenApiCustomizer.apply(openAPI);

        Schema<?> fixed = openAPI.getComponents().getSchemas().get(SCHEMA_NAME);
        assertNotNull(fixed.getIf());
        assertNotNull(fixed.getThen());
        assertEquals(Integer.valueOf(1),
                ((Schema<?>) fixed.getThen().getProperties().get("value")).getMaxItems());
        assertNull(((Schema<?>) fixed.getProperties().get("value")).getMaxItems());
    }
}
