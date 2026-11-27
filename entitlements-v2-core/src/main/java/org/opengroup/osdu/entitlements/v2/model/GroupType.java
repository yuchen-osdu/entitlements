package org.opengroup.osdu.entitlements.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum GroupType {
    NONE,
    DATA,
    USER,
    SERVICE
}
