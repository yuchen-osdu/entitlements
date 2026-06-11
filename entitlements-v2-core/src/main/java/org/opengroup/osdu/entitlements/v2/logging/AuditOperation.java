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

package org.opengroup.osdu.entitlements.v2.logging;

import org.opengroup.osdu.entitlements.v2.AppProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Enum defining audit operations and their required authorization groups.
 * Maps to the same groups used in @PreAuthorize annotations on API endpoints.
 *
 * Note: Some operations are defined for completeness but may not be actively
 * used in the current service layer implementation.
 */
public enum AuditOperation {

    // === ACTIVELY USED OPERATIONS ===

    // Group operations requiring admin-level access
    CREATE_GROUP(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN)),
    DELETE_GROUP(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN)),

    // Member operations requiring user-level access
    ADD_MEMBER(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN, AppProperties.USERS)),
    REMOVE_MEMBER(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN, AppProperties.USERS)),
    DELETE_MEMBER(Collections.singletonList(AppProperties.OPS)),

    // Read operations
    LIST_MEMBER(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN, AppProperties.USERS)),

    // Update operations
    UPDATE_GROUP(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN, AppProperties.USERS)),
    UPDATE_APP_IDS(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN, AppProperties.USERS)),

    // === CURRENTLY UNUSED OPERATIONS ===
    // Defined for completeness; may be used in future or provider-specific implementations

    // List groups - not currently audited in ListGroupService
    LIST_GROUP(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN, AppProperties.USERS)),

    // Redis backup operations (IBM provider specific) - may not be actively used
    REDIS_BACKUP(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN)),
    REDIS_BACKUP_VERSIONS(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN)),
    REDIS_RESTORE(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN)),

    // Cache operations - not currently audited
    UPDATE_CACHE(Arrays.asList(AppProperties.OPS, AppProperties.ADMIN));

    private final List<String> requiredGroups;

    AuditOperation(List<String> requiredGroups) {
        this.requiredGroups = Collections.unmodifiableList(requiredGroups);
    }

    /**
     * Get the list of groups that are authorized to perform this operation.
     * @return Unmodifiable list of group names
     */
    public List<String> getRequiredGroups() {
        return requiredGroups;
    }
}
