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

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.service.GroupsProvider;
import org.opengroup.osdu.entitlements.v2.util.RequestInfoUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthorizationServiceEntitlements implements AuthorizationService {
    private static final String UNAUTHORIZED_ERROR_MESSAGE = "The user is not authorized to perform this action";

    @Autowired
    private JaxRsDpsLog log;
    @Autowired
    private RequestInfoUtilService requestInfoUtilService;
    @Autowired
    private GroupsProvider groupsProvider;

    @Override
    public boolean isCurrentUserAuthorized(DpsHeaders headers, String... roles) {
        log.debug(String.format("authorizeAny timestamp: %d", System.currentTimeMillis()));
        String requesterId = requestInfoUtilService.getUserId(headers);
        List<String> groupNames = getUserGroupNames(requesterId, headers.getPartitionId());
        if (!isValidGroups(groupNames, roles)) {
            throw AppException.createUnauthorized(UNAUTHORIZED_ERROR_MESSAGE);
        }
        return requesterId != null;
    }

    @Override
    public boolean isGivenUserAuthorized(String userId, String partitionId, String... roles) {
        List<String> groupNames = getUserGroupNames(userId, partitionId);
        return isValidGroups(groupNames, roles);
    }

    @Override
    public String getAuthorizedGroupName(DpsHeaders headers, String... roles) {
        String requesterId = requestInfoUtilService.getUserId(headers);
        List<String> groupNamesOriginalCaller = getUserGroupNames(requesterId, headers.getPartitionId());

        return Arrays.stream(roles)
                .filter(groupNamesOriginalCaller::contains)
                .findFirst()
                .orElse(null);
    }

    private List<String> getUserGroupNames(String userId, String partitionId) {
        return groupsProvider.getGroupsInContext(userId, partitionId)
                .stream()
                .map(ParentReference::getName)
                .collect(Collectors.toList());
    }

    private boolean isValidGroups(List<String> groupNames, String... roles) {
        if (!groupNames.contains("users")) {
            return false;
        }
        return !Collections.disjoint(groupNames, Arrays.asList(roles));
    }
}
