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

package org.opengroup.osdu.entitlements.v2.auth;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;

public interface AuthorizationService {
    boolean isCurrentUserAuthorized(DpsHeaders headers, String... roles);

    boolean isGivenUserAuthorized(String userId, String partitionId, String... roles);

    /**
     * Get the name of the group that authorized the current user for the given roles.
     * @param headers Request headers containing user information
     * @param roles The roles being checked for authorization
     * @return The group name that authorized the user, or null if not found
     */
    String getAuthorizedGroupName(DpsHeaders headers, String... roles);
}
