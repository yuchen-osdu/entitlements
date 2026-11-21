package org.opengroup.osdu.entitlements.v2.model.updategroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.entitlements.v2.validation.ValidUpdateGroupOp;
import org.opengroup.osdu.entitlements.v2.validation.ValidUpdateGroupPath;

import java.util.List;

@Data
@Builder
@Generated
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateGroupOperation", description = "Represents a model for the Update Group Operation")
public class UpdateGroupOperation {
    @NotNull
    @ValidUpdateGroupOp
    @JsonProperty("op")
    @Schema(
            description = "Update Group Operation. Spec enum is case-sensitive; runtime accepts replace case-insensitively.",
            allowableValues = {"replace"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String operation;

    @NotNull
    @ValidUpdateGroupPath
    @Schema(
            description = "Update Group Path. Spec enum is case-sensitive; runtime accepts /name and /appIds case-insensitively. When path is /name, OpenAPI limits value to maxItems 1 (runtime uses the first element and ignores extras).",
            allowableValues = {"/name", "/appIds"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String path;

    @NotNull
    @NotEmpty
    @ArraySchema(minItems = 1, schema = @Schema(type = "string"))
    @Schema(description = "Values to apply. For path=/name only the first element is used at runtime; OpenAPI documents maxItems 1 for that case.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> value;
}
