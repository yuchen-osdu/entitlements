package org.opengroup.osdu.entitlements.v2.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import java.util.List;

/** Caps {@code UpdateGroupOperation.value} to one item when {@code path=/name} via {@code if}/{@code then}. */
final class UpdateGroupOperationOpenApiCustomizer {

    static final String SCHEMA_NAME = "UpdateGroupOperation";

    private UpdateGroupOperationOpenApiCustomizer() {
    }

    static void apply(OpenAPI openApi) {
        Schema<?> schema = openApi.getComponents().getSchemas().get(SCHEMA_NAME);
        if (schema == null) {
            return;
        }
        // @Size(min = 1) (or similar) can emit Integer.MAX_VALUE as maxItems; strip that noise.
        Schema<?> value = schema.getProperties() == null ? null : schema.getProperties().get("value");
        if (value != null && Integer.valueOf(Integer.MAX_VALUE).equals(value.getMaxItems())) {
            value.setMaxItems(null);
        }
        schema.setIf(new Schema<>()
            .addProperty("path", new StringSchema()._enum(List.of("/name")))
            .required(List.of("path")));
        schema.setThen(new Schema<>().addProperty("value", new Schema<>().maxItems(1)));
    }
}
